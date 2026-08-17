package enrolment

import dcb.testing.given
import kotlin.test.Test

class SubscribeStudentToCourseTest {
    private val ada = StudentId("s1")
    private val grace = StudentId("s2")
    private val history = CourseId("c1")

    @Test
    fun `a student can subscribe when there is a seat`() {
        given(
            CourseDefined(history, "History", capacity = 2),
            StudentRegistered(ada, "Ada"),
            StudentRegistered(grace, "Grace"),
            StudentSubscribedToCourse(ada, history),
        ).whenever {
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
        ).whenever {
            subscribeStudentToCourse(grace, history)
        }.expectRejection("Course c1 is full")
    }

    @Test
    fun `a student cannot subscribe to more courses than they are allowed`() {
        val art = CourseId("c2")
        given(
            CourseDefined(history, "History", capacity = 10),
            CourseDefined(art, "Art", capacity = 10),
            StudentRegistered(ada, "Ada", maxCourses = 1),
            StudentSubscribedToCourse(ada, history),
        ).whenever {
            subscribeStudentToCourse(ada, art)
        }.expectRejection("Student s1 cannot take more than 1 courses")
    }

    @Test
    fun `a student cannot subscribe twice to the same course`() {
        given(
            CourseDefined(history, "History", capacity = 10),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
        ).whenever {
            subscribeStudentToCourse(ada, history)
        }.expectRejection("Student s1 is already subscribed to c1")
    }

    @Test
    fun `a student who is not registered cannot subscribe`() {
        given(
            CourseDefined(history, "History", capacity = 10),
        ).whenever {
            subscribeStudentToCourse(ada, history)
        }.expectRejection("Student s1 is not registered")
    }

    @Test
    fun `a student cannot subscribe to a course that is not defined`() {
        given(
            StudentRegistered(ada, "Ada"),
        ).whenever {
            subscribeStudentToCourse(ada, history)
        }.expectRejection("Course c1 is not defined")
    }

    @Test
    fun `a student cannot subscribe to an archived course`() {
        given(
            CourseDefined(history, "History", capacity = 10),
            CourseArchived(history),
            StudentRegistered(ada, "Ada"),
        ).whenever {
            subscribeStudentToCourse(ada, history)
        }.expectRejection("Course c1 is not defined")
    }

    @Test
    fun `unsubscribing frees a seat`() {
        given(
            CourseDefined(history, "History", capacity = 1),
            StudentRegistered(ada, "Ada"),
            StudentRegistered(grace, "Grace"),
            StudentSubscribedToCourse(ada, history),
            StudentUnsubscribedFromCourse(ada, history),
        ).whenever {
            subscribeStudentToCourse(grace, history)
        }.expect(StudentSubscribedToCourse(grace, history))
    }
}
