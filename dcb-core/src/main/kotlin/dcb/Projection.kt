package dcb

/**
 * A just-in-time read: the folded state, and the store head when it was built.
 *
 * [asOf] is the highest position in the store at read time, not the last
 * matching fact. It is useful later when a caller waits for a slower projection.
 */
data class Snapshot<S>(
    val state: S,
    val asOf: Position?,
)

/**
 * Build a projection now from the facts currently in the store.
 * Nothing is persisted; the next call reads the history again.
 */
fun <S> EventStore.project(definition: Question<S>): Snapshot<S> {
    val read = read(Query.of(definition.queryItem))
    return Snapshot(definition.fold(read.facts), read.asOf())
}

/** The folded state only — the usual ad-hoc query. */
fun <S> EventStore.ask(definition: Question<S>): S = project(definition).state

/**
 * Compose several questions into one view with a single store read.
 *
 * ```
 * store.project {
 *     val seats by lookingAt { seatsTaken(course) }
 *     val capacity by lookingAt { courseCapacity(course) }
 *     answer { seats < capacity }
 * }
 * ```
 */
fun <R> EventStore.project(block: AdHocProjection<R>.() -> Unit): Snapshot<R> {
    val builder = AdHocProjection<R>()
    builder.block()
    val answer = builder.answerBlock
        ?: error("Ad-hoc projection is missing answer { }")
    require(builder.questions.isNotEmpty()) {
        "Ad-hoc projection needs at least one lookingAt { } question"
    }
    val read = read(Query.of(builder.questions.map { it.question.queryItem }))
    for (question in builder.questions) {
        question.fold(read.facts)
    }
    return Snapshot(answer(), read.asOf())
}

class AdHocProjection<R> internal constructor() {
    internal val questions = mutableListOf<BoundQuestion<*>>()
    internal var answerBlock: (() -> R)? = null

    /**
     * Include this question in the just-in-time read.
     * There is no lock: ad-hoc projections do not append.
     */
    fun <S> lookingAt(make: () -> Question<S>): BoundQuestion<S> {
        val bound = BoundQuestion(make(), lock = false)
        questions += bound
        return bound
    }

    fun answer(block: () -> R) {
        answerBlock = block
    }
}

private fun ReadResult.asOf(): Position? = head
