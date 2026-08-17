package dcb.postgres

import dcb.AppendCondition
import dcb.Query
import dcb.QueryItem
import dcb.Subject
import dcb.append
import dcb.subjects
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.parallel.ResourceLock
import java.sql.DriverManager
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("postgresIsUp")
@ResourceLock("postgres")
class PostgresBenchTest {
    private val url = System.getenv("DCB_PG_URL") ?: "jdbc:postgresql://localhost:5433/dcb"
    private val user = System.getenv("DCB_PG_USER") ?: "dcb"
    private val password = System.getenv("DCB_PG_PASSWORD") ?: "dcb"
    private val eventCount = System.getenv("DCB_BENCH_EVENTS")?.toIntOrNull() ?: 500

    @Test
    @Tag("bench")
    @EnabledIfEnvironmentVariable(named = "DCB_BENCH", matches = "1")
    fun `tag lookup and conditional append stay in the millisecond range`() {
        val store = PostgresEventStore(
            connect = { DriverManager.getConnection(url, user, password) },
            codec = jsonFactCodec { register(NotePosted.serializer()) },
        )
        store.ensureSchema()
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { it.execute("TRUNCATE events RESTART IDENTITY CASCADE") }
        }

        val appendMs = measureTimeMillis {
            repeat(eventCount) { index ->
                store.append(NotePosted("${index % 50}", "note-$index"))
            }
        }

        val board = Subject("board:7")
        val query = Query.of(QueryItem(tags = subjects(board)))
        var readMs = 0L
        var readCount = 0
        repeat(20) {
            readMs += measureTimeMillis {
                readCount = store.read(query).facts.size
            }
        }
        val avgReadMs = readMs / 20.0

        val conditionMs = measureTimeMillis {
            val head = store.read(Query.all()).head
            store.append(
                listOf(NotePosted("board-7", "late")),
                AppendCondition(failIfEventsMatch = query, after = head),
            )
        }

        println(
            "bench events=$eventCount append=${appendMs}ms (${appendMs.toDouble() / eventCount}ms/event) " +
                "readAvg=${"%.2f".format(avgReadMs)}ms matches=$readCount conditionAppend=${conditionMs}ms",
        )
        assertTrue(readCount > 0)
        assertTrue(avgReadMs < 100, "tagged read averaged ${avgReadMs}ms")
    }

    companion object {
        @JvmStatic
        fun postgresIsUp(): Boolean = runCatching {
            val url = System.getenv("DCB_PG_URL") ?: "jdbc:postgresql://localhost:5433/dcb"
            val user = System.getenv("DCB_PG_USER") ?: "dcb"
            val password = System.getenv("DCB_PG_PASSWORD") ?: "dcb"
            DriverManager.getConnection(url, user, password).use { }
        }.isSuccess
    }
}
