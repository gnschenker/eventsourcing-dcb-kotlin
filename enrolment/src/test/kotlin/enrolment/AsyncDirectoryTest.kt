package enrolment

import dcb.InMemoryEventStore
import dcb.Position
import dcb.append
import dcb.projectAsync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsyncDirectoryTest {
    private val ada = StudentId("s1")
    private val grace = StudentId("s2")
    private val history = CourseId("c1")
    private val art = CourseId("c2")

    @Test
    fun `the course directory catches up after subscriptions`() {
        val store = InMemoryEventStore()
        val directory = store.projectAsync("course-directory", courseDirectory())

        store.append(
            CourseDefined(history, "History", 2),
            CourseDefined(art, "Art", 10),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
        )
        directory.catchUp()

        val listing = directory.state[history]!!
        assertEquals("History", listing.title)
        assertEquals(1, listing.seatsTaken)
        assertEquals(2, listing.capacity)
        assertTrue(listing.open)
        assertEquals(0, directory.state[art]!!.seatsTaken)
    }

    @Test
    fun `the directory stays in sync as facts keep arriving`() {
        val store = InMemoryEventStore()
        val directory = store.projectAsync("course-directory", courseDirectory())
        store.append(
            CourseDefined(history, "History", 1),
            StudentRegistered(ada, "Ada"),
            StudentRegistered(grace, "Grace"),
            StudentSubscribedToCourse(ada, history),
        )
        directory.catchUp()
        assertFalse(directory.state[history]!!.open)

        store.append(StudentUnsubscribedFromCourse(ada, history))
        directory.catchUp()
        assertTrue(directory.state[history]!!.open)
        assertEquals(0, directory.state[history]!!.seatsTaken)
    }

    @Test
    fun `a running directory sees a new course without a manual catch-up`() {
        val store = InMemoryEventStore()
        val directory = store.projectAsync("course-directory", courseDirectory())
        directory.start(pollMillis = 20).use {
            store.append(CourseDefined(history, "History", 8))
            val snap = directory.catchUpTo(Position(1), timeoutMillis = 2_000)
            assertEquals("History", snap.state[history]!!.title)
        }
    }
}
