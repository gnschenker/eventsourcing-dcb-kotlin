package enrolment

import dcb.InMemoryEventStore
import dcb.append
import dcb.ask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdHocProjectionTest {
    private val ada = StudentId("s1")
    private val grace = StudentId("s2")
    private val history = CourseId("c1")
    private val art = CourseId("c2")

    @Test
    fun `course availability is built just in time from the facts`() {
        val store = InMemoryEventStore()
        store.append(
            CourseDefined(history, "History", capacity = 2),
            StudentRegistered(ada, "Ada"),
            StudentRegistered(grace, "Grace"),
            StudentSubscribedToCourse(ada, history),
        )

        val view = store.availabilityOf(history)
        assertTrue(view.defined)
        assertEquals("History", view.title)
        assertEquals(1, view.seatsTaken)
        assertEquals(2, view.capacity)
        assertTrue(view.open)
    }

    @Test
    fun `a full course is not open`() {
        val store = InMemoryEventStore()
        store.append(
            CourseDefined(history, "History", capacity = 1),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
        )

        val view = store.availabilityOf(history)
        assertEquals(1, view.seatsTaken)
        assertFalse(view.open)
    }

    @Test
    fun `an unknown course answers with the empty view`() {
        val view = InMemoryEventStore().availabilityOf(history)
        assertFalse(view.defined)
        assertEquals(null, view.title)
        assertEquals(0, view.seatsTaken)
        assertEquals(0, view.capacity)
        assertFalse(view.open)
    }

    @Test
    fun `a student course list is rebuilt from subscriptions`() {
        val store = InMemoryEventStore()
        store.append(
            CourseDefined(history, "History", 10),
            CourseDefined(art, "Art", 10),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
            StudentSubscribedToCourse(ada, art),
            StudentUnsubscribedFromCourse(ada, history),
        )

        assertEquals(setOf(art), store.coursesTakenBy(ada))
        assertEquals(emptySet(), store.studentsEnrolledOn(history))
        assertEquals(setOf(ada), store.studentsEnrolledOn(art))
    }

    @Test
    fun `the same question can be asked after more facts happen`() {
        val store = InMemoryEventStore()
        store.append(
            CourseDefined(history, "History", 10),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
        )
        assertEquals(1, store.ask(seatsTaken(history)))

        store.append(StudentUnsubscribedFromCourse(ada, history))
        assertEquals(0, store.ask(seatsTaken(history)))
    }
}
