package enrolment

import dcb.Position
import dcb.append
import dcb.projectAsync
import dcb.postgres.PostgresProjectionStore
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.parallel.ResourceLock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("postgresIsUp")
@ResourceLock("postgres")
class PostgresAsyncProjectionTest {
    private val ada = StudentId("s1")
    private val history = CourseId("c1")
    private val store by lazy { PostgresSupport.store() }
    private val json = Json { encodeDefaults = true }
    private val snapshots by lazy {
        PostgresProjectionStore(
            connect = PostgresSupport::connect,
            encode = { json.encodeToString(CourseDirectory.serializer(), it) },
            decode = { json.decodeFromString(CourseDirectory.serializer(), it) },
        )
    }

    @BeforeAll
    fun schema() {
        store.ensureSchema()
        snapshots.ensureSchema()
    }

    @BeforeEach
    fun wipe() {
        PostgresSupport.wipe()
        snapshots.delete("course-directory")
    }

    @Test
    fun `a postgres directory snapshot survives a new projector instance`() {
        store.append(
            CourseDefined(history, "History", 2),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
        )
        store.projectAsync("course-directory", courseDirectory(), snapshots).catchUp()

        val resumed = store.projectAsync("course-directory", courseDirectory(), snapshots)
        assertEquals(1, resumed.state[history]!!.seatsTaken)
        assertEquals(Position(3), resumed.asOf)

        store.append(StudentUnsubscribedFromCourse(ada, history))
        val caught = resumed.catchUp()
        assertEquals(0, caught.state[history]!!.seatsTaken)
        assertTrue(caught.state[history]!!.open)
    }

    @Test
    fun `a running postgres directory waits for notify`() {
        val directory = store.projectAsync("course-directory", courseDirectory(), snapshots)
        val waiter = Thread {
            directory.catchUpTo(Position(1), timeoutMillis = 5_000)
        }
        waiter.start()
        Thread.sleep(80)
        store.append(CourseDefined(history, "History", 5))
        waiter.join(3_000)
        assertTrue(!waiter.isAlive, "catchUpTo did not return after append")
        assertEquals("History", directory.state[history]!!.title)
    }

    companion object {
        @JvmStatic
        fun postgresIsUp(): Boolean = PostgresSupport.canConnect()
    }
}
