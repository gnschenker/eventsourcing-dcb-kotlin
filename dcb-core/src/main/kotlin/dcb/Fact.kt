package dcb

/**
 * Something that happened. Domain facts are data classes that implement this
 * and declare what they are [about].
 *
 * The recorded type is the simple class name, e.g. `StudentSubscribedToCourse`.
 */
interface Fact {
    val about: Set<Subject>
}

val Fact.type: String
    get() = this::class.simpleName ?: error("Fact must be a named class")
