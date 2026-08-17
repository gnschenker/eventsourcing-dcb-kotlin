package enrolment

import dcb.Fact
import dcb.subjects
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class CourseDefined(
    val course: CourseId,
    val title: String,
    val capacity: Int,
) : Fact {
    @Transient override val about = subjects(course)
}

@Serializable
data class CourseArchived(
    val course: CourseId,
) : Fact {
    @Transient override val about = subjects(course)
}

@Serializable
data class CourseCapacityChanged(
    val course: CourseId,
    val newCapacity: Int,
) : Fact {
    @Transient override val about = subjects(course)
}

@Serializable
data class StudentRegistered(
    val student: StudentId,
    val name: String,
    val maxCourses: Int = 10,
) : Fact {
    @Transient override val about = subjects(student)
}

@Serializable
data class StudentSubscribedToCourse(
    val student: StudentId,
    val course: CourseId,
) : Fact {
    @Transient override val about = subjects(student, course)
}

@Serializable
data class StudentUnsubscribedFromCourse(
    val student: StudentId,
    val course: CourseId,
) : Fact {
    @Transient override val about = subjects(student, course)
}
