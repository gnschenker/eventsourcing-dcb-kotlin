package enrolment

import dcb.postgres.jsonFactCodec

fun enrolmentFactCodec() = jsonFactCodec {
    register(CourseDefined.serializer())
    register(CourseArchived.serializer())
    register(CourseCapacityChanged.serializer())
    register(StudentRegistered.serializer())
    register(StudentSubscribedToCourse.serializer())
    register(StudentUnsubscribedFromCourse.serializer())
}
