package enrolment

import dcb.append
import dcb.testing.given
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.parallel.ResourceLock

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("postgresIsUp")
@ResourceLock("postgres")
class PostgresEnrolmentTest {
    private val ada = StudentId("s1")
    private val grace = StudentId("s2")
    private val history = CourseId("c1")
    private val store by lazy { PostgresSupport.store() }

    @BeforeAll
    fun schema() {
        store.ensureSchema()
    }

    @BeforeEach
    fun wipe() {
        PostgresSupport.wipe()
    }

    @Test
    fun `a student can subscribe when there is a seat`() {
        given(
            CourseDefined(history, "History", capacity = 2),
            StudentRegistered(ada, "Ada"),
            StudentRegistered(grace, "Grace"),
            StudentSubscribedToCourse(ada, history),
        ).against(store).whenever {
            subscribeStudentToCourse(grace, history)
        }.expect(StudentSubscribedToCourse(grace, history))
    }

    @Test
    fun `a student cannot subscribe when the course is full`() {
        given(
            CourseDefined(history, "History", capacity = 1),
            StudentRegistered(ada, "Ada"),
            StudentRegistered(grace, "Grace"),
            StudentSubscribedToCourse(ada, history),
        ).against(store).whenever {
            subscribeStudentToCourse(grace, history)
        }.expectRejection("Course c1 is full")
    }

    @Test
    fun `course availability can be asked just in time`() {
        store.append(
            CourseDefined(history, "History", capacity = 2),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
        )

        val view = store.availabilityOf(history)
        assertTrue(view.defined)
        assertEquals("History", view.title)
        assertEquals(1, view.seatsTaken)
        assertTrue(view.open)
    }

    @Test
    fun `a course cannot be defined twice`() {
        given(
            CourseDefined(history, "History", capacity = 20),
        ).against(store).whenever {
            defineCourse(history, "History", capacity = 20)
        }.expectRejection("Course c1 is already defined")
    }

    companion object {
        @JvmStatic
        fun postgresIsUp(): Boolean = PostgresSupport.canConnect()
    }
}
