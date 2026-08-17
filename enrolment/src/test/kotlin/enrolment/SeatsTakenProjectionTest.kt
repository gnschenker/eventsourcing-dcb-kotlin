package enrolment

import dcb.EventStore
import dcb.FoldingProjector
import dcb.InMemoryCheckpointStore
import dcb.InMemoryEventStore
import dcb.append
import kotlin.test.Test
import kotlin.test.assertEquals

class SeatsTakenProjectionTest {
    private val history = CourseId("c1")
    private val ada = StudentId("s1")
    private val grace = StudentId("s2")

    @Test
    fun `seats taken for a course can be projected from the same question the decision uses`() {
        val store = InMemoryEventStore()
        store.append(
            CourseDefined(history, "History", 10),
            StudentRegistered(ada, "Ada"),
            StudentRegistered(grace, "Grace"),
            StudentSubscribedToCourse(ada, history),
            StudentSubscribedToCourse(grace, history),
            StudentUnsubscribedFromCourse(ada, history),
        )

        assertEquals(1, seatsTakenOn(store, history))
    }

    private fun seatsTakenOn(store: EventStore, course: CourseId): Int {
        val projector = FoldingProjector(
            name = "seats-taken-${course}",
            store = store,
            checkpoints = InMemoryCheckpointStore(),
            question = seatsTaken(course),
        )
        return projector.catchUp()
    }
}
