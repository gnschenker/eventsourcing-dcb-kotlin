package enrolment

import dcb.testing.given
import kotlin.test.Test

class DefineAndRegisterTest {
    private val ada = StudentId("s1")
    private val history = CourseId("c1")

    @Test
    fun `a new course can be defined`() {
        given().whenever {
            defineCourse(history, "History", capacity = 20)
        }.expect(CourseDefined(history, "History", 20))
    }

    @Test
    fun `a course cannot be defined twice`() {
        given(
            CourseDefined(history, "History", capacity = 20),
        ).whenever {
            defineCourse(history, "History", capacity = 20)
        }.expectRejection("Course c1 is already defined")
    }

    @Test
    fun `a course must have a capacity`() {
        given().whenever {
            defineCourse(history, "History", capacity = 0)
        }.expectRejection("Course c1 must have a capacity greater than 0")
    }

    @Test
    fun `a new student can be registered`() {
        given().whenever {
            registerStudent(ada, "Ada", maxCourses = 5)
        }.expect(StudentRegistered(ada, "Ada", 5))
    }

    @Test
    fun `a student cannot be registered twice`() {
        given(
            StudentRegistered(ada, "Ada"),
        ).whenever {
            registerStudent(ada, "Ada")
        }.expectRejection("Student s1 is already registered")
    }

    @Test
    fun `course capacity can be increased`() {
        given(
            CourseDefined(history, "History", capacity = 1),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
        ).whenever {
            changeCourseCapacity(history, 5)
        }.expect(CourseCapacityChanged(history, 5))
    }

    @Test
    fun `course capacity cannot drop below the students already subscribed`() {
        given(
            CourseDefined(history, "History", capacity = 2),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
        ).whenever {
            changeCourseCapacity(history, 0)
        }.expectRejection("Course c1 must have a capacity greater than 0")
    }

    @Test
    fun `a student can leave a course they are subscribed to`() {
        given(
            CourseDefined(history, "History", capacity = 10),
            StudentRegistered(ada, "Ada"),
            StudentSubscribedToCourse(ada, history),
        ).whenever {
            unsubscribeStudentFromCourse(ada, history)
        }.expect(StudentUnsubscribedFromCourse(ada, history))
    }

    @Test
    fun `a student cannot leave a course they are not subscribed to`() {
        given(
            CourseDefined(history, "History", capacity = 10),
            StudentRegistered(ada, "Ada"),
        ).whenever {
            unsubscribeStudentFromCourse(ada, history)
        }.expectRejection("Student s1 is not subscribed to c1")
    }
}
