package enrolment

import dcb.question
import kotlinx.serialization.Serializable

@Serializable
data class CourseRecord(
    val course: CourseId,
    val title: String,
    val capacity: Int,
    val seatsTaken: Int,
    val archived: Boolean = false,
) {
    val open: Boolean get() = !archived && seatsTaken < capacity
}

@Serializable
data class CourseDirectory(val courses: List<CourseRecord> = emptyList()) {
    operator fun get(id: CourseId): CourseRecord? = courses.find { it.course == id }

    fun upsert(record: CourseRecord): CourseDirectory =
        copy(courses = courses.filterNot { it.course == record.course } + record)

    fun update(id: CourseId, change: CourseRecord.() -> CourseRecord): CourseDirectory {
        val current = this[id] ?: return this
        return upsert(current.change())
    }
}

/** Global directory: one question about every course, for an async read model. */
fun courseDirectory() = question(initial = CourseDirectory(), about = emptySet()) {
    on<CourseDefined> {
        upsert(CourseRecord(it.course, it.title, it.capacity, seatsTaken = 0))
    }
    on<CourseArchived> { update(it.course) { copy(archived = true) } }
    on<CourseCapacityChanged> { update(it.course) { copy(capacity = it.newCapacity) } }
    on<StudentSubscribedToCourse> { update(it.course) { copy(seatsTaken = seatsTaken + 1) } }
    on<StudentUnsubscribedFromCourse> { update(it.course) { copy(seatsTaken = seatsTaken - 1) } }
}
