package dcb.postgres

import dcb.append
import dcb.projectSync
import dcb.question
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.parallel.ResourceLock
import java.sql.DriverManager
import kotlin.test.assertEquals
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("postgresIsUp")
@ResourceLock("postgres")
class PostgresSyncProjectionTest {
    private val url = System.getenv("DCB_PG_URL") ?: "jdbc:postgresql://localhost:5433/dcb"
    private val user = System.getenv("DCB_PG_USER") ?: "dcb"
    private val password = System.getenv("DCB_PG_PASSWORD") ?: "dcb"

    private val store by lazy {
        PostgresEventStore(
            connect = { DriverManager.getConnection(url, user, password) },
            codec = jsonFactCodec { register(NotePosted.serializer()) },
        )
    }

    private val snapshots by lazy {
        PostgresProjectionStore(
            connect = { DriverManager.getConnection(url, user, password) },
            encode = { it.toString() },
            decode = { it.toInt() },
        )
    }

    @BeforeAll
    fun schema() {
        store.ensureSchema()
        snapshots.ensureSchema()
    }

    @BeforeEach
    fun wipe() {
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use {
                it.execute("TRUNCATE events RESTART IDENTITY CASCADE")
                runCatching { it.execute("TRUNCATE projections") }
            }
        }
    }

    @Test
    fun `append updates a postgres projection in the same transaction`() {
        val count = question(initial = 0, about = emptySet()) {
            on<NotePosted> { this + 1 }
        }
        val projection = store.projectSync("note-count", count, snapshots)
        store.append(NotePosted("general", "one"), NotePosted("random", "two"))
        assertEquals(2, projection.state)
        assertEquals(2L, projection.asOf?.value)

        val resumed = store.projectSync("note-count", count, snapshots)
        assertEquals(2, resumed.state)
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
