package enrolment

import dcb.question
import dcb.subjects

fun courseExists(course: CourseId) = question(initial = false, about = course) {
    on<CourseDefined> { true }
    on<CourseArchived> { false }
}

fun courseCapacity(course: CourseId) = question(initial = 0, about = course) {
    on<CourseDefined> { it.capacity }
    on<CourseCapacityChanged> { it.newCapacity }
}

fun seatsTaken(course: CourseId) = question(initial = 0, about = course) {
    on<StudentSubscribedToCourse> { this + 1 }
    on<StudentUnsubscribedFromCourse> { this - 1 }
}

fun studentExists(student: StudentId) = question(initial = false, about = student) {
    on<StudentRegistered> { true }
}

fun studentRegistration(student: StudentId) = question(
    initial = null as StudentRegistered?,
    about = student,
) {
    on<StudentRegistered> { it }
}

fun coursesTaken(student: StudentId) = question(initial = 0, about = student) {
    on<StudentSubscribedToCourse> { this + 1 }
    on<StudentUnsubscribedFromCourse> { this - 1 }
}

fun alreadySubscribed(student: StudentId, course: CourseId) = question(
    initial = false,
    about = subjects(student, course),
) {
    on<StudentSubscribedToCourse> { true }
    on<StudentUnsubscribedFromCourse> { false }
}

fun courseTitle(course: CourseId) = question(initial = null as String?, about = course) {
    on<CourseDefined> { it.title }
}

fun coursesOf(student: StudentId) = question(initial = emptySet<CourseId>(), about = student) {
    on<StudentSubscribedToCourse> { this + it.course }
    on<StudentUnsubscribedFromCourse> { this - it.course }
}

fun studentsOn(course: CourseId) = question(initial = emptySet<StudentId>(), about = course) {
    on<StudentSubscribedToCourse> { this + it.student }
    on<StudentUnsubscribedFromCourse> { this - it.student }
}
