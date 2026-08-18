package dcb.postgres

import dcb.Position
import dcb.ProjectionStore
import dcb.Snapshot
import java.sql.Connection

class PostgresProjectionStore<S>(
    private val connect: () -> Connection,
    private val encode: (S) -> String,
    private val decode: (String) -> S,
) : ProjectionStore<S> {

    fun ensureSchema() {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS projections (
                      name text PRIMARY KEY,
                      position bigint NOT NULL,
                      state jsonb NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    override fun load(name: String): Snapshot<S>? = withConnection { connection ->
        connection.prepareStatement(
            "SELECT position, state FROM projections WHERE name = ?",
        ).use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return@withConnection null
                Snapshot(
                    state = decode(rows.getString("state")),
                    asOf = Position(rows.getLong("position")),
                )
            }
        }
    }

    override fun save(name: String, snapshot: Snapshot<S>) {
        val asOf = snapshot.asOf ?: error("Cannot save a projection snapshot without a position")
        withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO projections (name, position, state) VALUES (?, ?, ?::jsonb)
                ON CONFLICT (name) DO UPDATE SET position = EXCLUDED.position, state = EXCLUDED.state
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, name)
                statement.setLong(2, asOf.value)
                statement.setString(3, encode(snapshot.state))
                statement.executeUpdate()
            }
        }
    }

    override fun delete(name: String) {
        withConnection { connection ->
            connection.prepareStatement("DELETE FROM projections WHERE name = ?").use { statement ->
                statement.setString(1, name)
                statement.executeUpdate()
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        val bound = PostgresSession.current()
        if (bound != null) return block(bound)
        return connect().use(block)
    }
}
