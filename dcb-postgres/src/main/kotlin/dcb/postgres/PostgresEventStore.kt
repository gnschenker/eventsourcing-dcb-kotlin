package dcb.postgres

import dcb.AppendCondition
import dcb.ConcurrencyConflict
import dcb.EventStore
import dcb.Fact
import dcb.FactCodec
import dcb.Position
import dcb.Query
import dcb.ReadResult
import dcb.RecordedFact
import dcb.Subject
import dcb.isBefore
import dcb.type
import java.sql.Connection
import java.sql.SQLException
import org.postgresql.PGConnection

class PostgresEventStore(
    private val connect: () -> Connection,
    private val codec: FactCodec,
) : EventStore {

    fun ensureSchema() {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS events (
                      position bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      type text NOT NULL,
                      tags text[] NOT NULL,
                      data jsonb NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS event_tags (
                      tag text NOT NULL,
                      position bigint NOT NULL REFERENCES events(position),
                      PRIMARY KEY (tag, position)
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    override fun read(query: Query, after: Position?): ReadResult {
        connect().use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            try {
                val head = connection.storeHead()
                val facts = connection.load(query, after)
                connection.commit()
                return ReadResult(facts, head)
            } catch (error: Exception) {
                connection.rollbackQuietly()
                throw error
            }
        }
    }

    override fun append(facts: List<Fact>, condition: AppendCondition?): Position {
        require(facts.isNotEmpty()) { "Cannot append an empty list of facts" }
        connect().use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            try {
                if (condition != null && connection.hasConflict(condition)) {
                    connection.rollback()
                    throw ConcurrencyConflict("A conflicting fact was recorded")
                }
                var last = Position(0)
                for (fact in facts) {
                    last = connection.insert(fact, codec)
                }
                connection.createStatement().use { it.execute("NOTIFY dcb_append") }
                connection.commit()
                return last
            } catch (error: SQLException) {
                connection.rollbackQuietly()
                if (error.isConcurrencyFailure()) {
                    throw ConcurrencyConflict("A conflicting fact was recorded")
                }
                throw error
            } catch (error: ConcurrencyConflict) {
                throw error
            } catch (error: Exception) {
                connection.rollbackQuietly()
                throw error
            }
        }
    }

    override fun awaitAppend(after: Position?, timeoutMillis: Long): Boolean {
        connect().use { connection ->
            if (connection.hasNews(after)) return true
            if (timeoutMillis <= 0) return false
            connection.createStatement().use { it.execute("LISTEN dcb_append") }
            val pg = connection.unwrap(PGConnection::class.java)
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (true) {
                if (connection.hasNews(after)) return true
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return connection.hasNews(after)
                val wait = remaining.coerceAtMost(100).toInt()
                val notifications = pg.getNotifications(wait)
                if (notifications != null && notifications.isNotEmpty()) return true
            }
        }
    }

    override fun subscribe(query: Query, after: Position): Sequence<RecordedFact> = sequence {
        var cursor = after
        while (true) {
            val batch = read(query, cursor).facts
            if (batch.isEmpty()) return@sequence
            for (fact in batch) {
                yield(fact)
                cursor = fact.position
            }
        }
    }

    private fun Connection.insert(fact: Fact, codec: FactCodec): Position {
        val sql = "INSERT INTO events (type, tags, data) VALUES (?, ?, ?::jsonb) RETURNING position"
        prepareStatement(sql).use { statement ->
            statement.setString(1, fact.type)
            statement.setArray(2, createArrayOf("text", fact.about.map { it.value }.toTypedArray()))
            statement.setString(3, codec.encode(fact))
            statement.executeQuery().use { rows ->
                check(rows.next()) { "Insert did not return a position" }
                val position = Position(rows.getLong(1))
                insertTags(position, fact.about)
                return position
            }
        }
    }

    private fun Connection.insertTags(position: Position, tags: Set<dcb.Subject>) {
        if (tags.isEmpty()) return
        prepareStatement("INSERT INTO event_tags (tag, position) VALUES (?, ?)").use { statement ->
            for (tag in tags) {
                statement.setString(1, tag.value)
                statement.setLong(2, position.value)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun Connection.hasConflict(condition: AppendCondition): Boolean {
        val bound = condition.failIfEventsMatch.selectSql(
            after = condition.after,
            columns = "SELECT 1",
            limitOne = true,
        )
        prepareStatement(bound.sql).use { statement ->
            statement.bind(bound.bindings)
            statement.executeQuery().use { rows ->
                return rows.next()
            }
        }
    }

    private fun Connection.load(query: Query, after: Position?): List<RecordedFact> {
        val bound = query.selectSql(
            after = after,
            columns = "SELECT e.position, e.type, e.tags, e.data",
        )
        prepareStatement(bound.sql).use { statement ->
            statement.bind(bound.bindings)
            statement.executeQuery().use { rows ->
                val facts = mutableListOf<RecordedFact>()
                while (rows.next()) {
                    val type = rows.getString("type")
                    facts += RecordedFact(
                        position = Position(rows.getLong("position")),
                        type = type,
                        tags = rows.subjects("tags"),
                        payload = codec.decode(type, rows.getString("data")),
                    )
                }
                return facts
            }
        }
    }

    private fun Connection.hasNews(after: Position?): Boolean {
        val head = storeHead() ?: return false
        return after.isBefore(head)
    }

    private fun Connection.storeHead(): Position? {
        createStatement().use { statement ->
            statement.executeQuery("SELECT MAX(position) FROM events").use { rows ->
                rows.next()
                val value = rows.getLong(1)
                return if (rows.wasNull()) null else Position(value)
            }
        }
    }

    private fun java.sql.PreparedStatement.bind(bindings: List<Any>) {
        bindings.forEachIndexed { index, value ->
            when (value) {
                is Long -> setLong(index + 1, value)
                is Int -> setInt(index + 1, value)
                is String -> setString(index + 1, value)
                is Array<*> -> setArray(index + 1, connection.createArrayOf("text", value))
                else -> error("Unsupported binding ${value::class.simpleName}")
            }
        }
    }

    private fun java.sql.ResultSet.subjects(column: String): Set<Subject> {
        val raw = getArray(column)?.array as? Array<*> ?: return emptySet()
        return raw.map { Subject(it.toString()) }.toSet()
    }

    private fun Connection.rollbackQuietly() {
        try {
            rollback()
        } catch (_: SQLException) {
        }
    }

    private fun SQLException.isConcurrencyFailure(): Boolean =
        sqlState == "40001" || sqlState == "40P01"
}
