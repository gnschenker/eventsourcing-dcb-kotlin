package enrolment

import dcb.About
import dcb.Subject
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class StudentId(val value: String) : About {
    override fun asSubject(): Subject = Subject("student:$value")
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class CourseId(val value: String) : About {
    override fun asSubject(): Subject = Subject("course:$value")
    override fun toString(): String = value
}
