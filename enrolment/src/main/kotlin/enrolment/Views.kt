package enrolment

import dcb.EventStore
import dcb.ask
import dcb.project

data class CourseAvailability(
    val defined: Boolean,
    val title: String?,
    val seatsTaken: Int,
    val capacity: Int,
) {
    val open: Boolean get() = defined && seatsTaken < capacity
}

/** Just-in-time view of one course, built from the same questions decisions use. */
fun EventStore.availabilityOf(course: CourseId): CourseAvailability = project {
    val defined by lookingAt { courseExists(course) }
    val title by lookingAt { courseTitle(course) }
    val seats by lookingAt { seatsTaken(course) }
    val capacity by lookingAt { courseCapacity(course) }
    answer {
        CourseAvailability(
            defined = defined,
            title = title,
            seatsTaken = seats,
            capacity = capacity,
        )
    }
}.state

fun EventStore.coursesTakenBy(student: StudentId): Set<CourseId> = ask(coursesOf(student))

fun EventStore.studentsEnrolledOn(course: CourseId): Set<StudentId> = ask(studentsOn(course))
