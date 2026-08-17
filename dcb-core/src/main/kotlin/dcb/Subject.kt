package dcb

/**
 * A domain subject an event is about, e.g. `student:s1` or `course:c1`.
 * This is the DCB tag; domain code should build it from typed identifiers.
 */
@JvmInline
value class Subject(val value: String) : About {
    override fun asSubject(): Subject = this

    override fun toString(): String = value
}

/** Something that can be named as a subject of a fact. */
fun interface About {
    fun asSubject(): Subject
}

fun subjects(vararg items: About): Set<Subject> =
    items.map { it.asSubject() }.toSet()

fun subjects(items: Iterable<About>): Set<Subject> =
    items.map { it.asSubject() }.toSet()
