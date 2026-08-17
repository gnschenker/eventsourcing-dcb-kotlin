package enrolment

import dcb.decision

fun defineCourse(course: CourseId, title: String, capacity: Int) = decision {
    val exists by requiring { courseExists(course) }
    decide {
        unless(capacity > 0) { "Course $course must have a capacity greater than 0" }
        unless(!exists) { "Course $course is already defined" }
        then(CourseDefined(course, title, capacity))
    }
}

fun registerStudent(student: StudentId, name: String, maxCourses: Int = 10) = decision {
    val exists by requiring { studentExists(student) }
    decide {
        unless(maxCourses > 0) { "Student $student must be allowed at least one course" }
        unless(!exists) { "Student $student is already registered" }
        then(StudentRegistered(student, name, maxCourses))
    }
}

fun subscribeStudentToCourse(student: StudentId, course: CourseId) = decision {
    val registered by requiring { studentExists(student) }
    val defined by requiring { courseExists(course) }
    val already by requiring { alreadySubscribed(student, course) }
    val takenByThem by requiring { coursesTaken(student) }
    val seats by requiring { seatsTaken(course) }
    val registration by requiring { studentRegistration(student) }
    val capacity by considering { courseCapacity(course) }

    decide {
        unless(registered) { "Student $student is not registered" }
        unless(defined) { "Course $course is not defined" }
        unless(!already) { "Student $student is already subscribed to $course" }
        val maxCourses = registration?.maxCourses ?: 0
        unless(takenByThem < maxCourses) { "Student $student cannot take more than $maxCourses courses" }
        unless(seats < capacity) { "Course $course is full" }
        then(StudentSubscribedToCourse(student, course))
    }
}

fun unsubscribeStudentFromCourse(student: StudentId, course: CourseId) = decision {
    val already by requiring { alreadySubscribed(student, course) }
    decide {
        unless(already) { "Student $student is not subscribed to $course" }
        then(StudentUnsubscribedFromCourse(student, course))
    }
}

fun changeCourseCapacity(course: CourseId, newCapacity: Int) = decision {
    val defined by requiring { courseExists(course) }
    val seats by requiring { seatsTaken(course) }
    val current by considering { courseCapacity(course) }
    decide {
        unless(defined) { "Course $course is not defined" }
        unless(newCapacity > 0) { "Course $course must have a capacity greater than 0" }
        unless(newCapacity != current) { "Course $course already has capacity $newCapacity" }
        unless(newCapacity >= seats) { "Course $course already has $seats students" }
        then(CourseCapacityChanged(course, newCapacity))
    }
}
