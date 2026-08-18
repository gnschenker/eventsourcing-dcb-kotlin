package dcb.postgres

import dcb.AppendCondition
import dcb.ConcurrencyConflict
import dcb.append
import dcb.Position
import dcb.Query
import dcb.QueryItem
import dcb.Subject
import dcb.subjects
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.parallel.ResourceLock
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue



@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("postgresIsUp")
@ResourceLock("postgres")
class PostgresEventStoreTest {
    private val url = System.getenv("DCB_PG_URL") ?: "jdbc:postgresql://localhost:5433/dcb"
    private val user = System.getenv("DCB_PG_USER") ?: "dcb"
    private val password = System.getenv("DCB_PG_PASSWORD") ?: "dcb"

    private val store by lazy {
        PostgresEventStore(
            connect = { DriverManager.getConnection(url, user, password) },
            codec = jsonFactCodec { register(NotePosted.serializer()) },
        )
    }

    @BeforeAll
    fun schema() {
        store.ensureSchema()
    }

    @BeforeEach
    fun wipe() {
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use {
                it.execute("TRUNCATE events RESTART IDENTITY CASCADE")
            }
        }
    }

    @Test
    fun `append and read round-trip facts and store head`() {
        store.append(NotePosted("general", "hello"), NotePosted("random", "world"))

        val all = store.read(Query.all())
        assertEquals(listOf(1L, 2L), all.facts.map { it.position.value })
        assertEquals(Position(2), all.head)
        assertEquals("hello", (all.facts[0].payload as NotePosted).text)

        val general = store.read(Query.of(QueryItem(tags = subjects(Subject("board:general")))))
        assertEquals(listOf("hello"), general.facts.map { (it.payload as NotePosted).text })
        assertEquals(Position(2), general.head)
    }

    @Test
    fun `empty store has no head`() {
        val result = store.read(Query.all())
        assertEquals(emptyList(), result.facts)
        assertNull(result.head)
    }

    @Test
    fun `awaitAppend returns shortly after a later append`() {
        val waiter = Thread {
            store.awaitAppend(Position(0), 5_000)
        }
        waiter.start()
        Thread.sleep(80)
        val started = System.currentTimeMillis()
        store.append(NotePosted("general", "ping"))
        waiter.join(2_000)
        assertTrue(!waiter.isAlive, "awaitAppend did not return after append")
        assertTrue(System.currentTimeMillis() - started < 1_500, "awaitAppend ignored NOTIFY")
    }

    @Test
    fun `append condition uses store head not last match`() {
        store.append(NotePosted("general", "one"))
        val head = store.read(Query.all()).head
        store.append(NotePosted("general", "two"))

        assertFailsWith<ConcurrencyConflict> {
            store.append(
                listOf(NotePosted("general", "three")),
                AppendCondition(
                    failIfEventsMatch = Query.of(QueryItem(tags = subjects(Subject("board:general")))),
                    after = head,
                ),
            )
        }
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
