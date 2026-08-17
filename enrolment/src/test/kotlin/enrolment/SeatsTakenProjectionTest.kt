package enrolment

import dcb.InMemoryEventStore
import dcb.append
import dcb.ask
import kotlin.test.Test
import kotlin.test.assertEquals

class SeatsTakenProjectionTest {
    private val history = CourseId("c1")
    private val ada = StudentId("s1")
    private val grace = StudentId("s2")

    @Test
    fun `seats taken for a course can be asked from the same question the decision uses`() {
        val store = InMemoryEventStore()
        store.append(
            CourseDefined(history, "History", 10),
            StudentRegistered(ada, "Ada"),
            StudentRegistered(grace, "Grace"),
            StudentSubscribedToCourse(ada, history),
            StudentSubscribedToCourse(grace, history),
            StudentUnsubscribedFromCourse(ada, history),
        )

        assertEquals(1, store.ask(seatsTaken(history)))
    }
}
