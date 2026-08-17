package dcb

/**
 * A named slice of history: which facts to load, and how they fold into local state.
 *
 * The event set for the decider is derived from the [on] handlers plus [about].
 */
class Question<S>(
    val initial: S,
    val about: Set<Subject>,
    private val handlers: Map<String, (S, Fact) -> S>,
) {
    val types: Set<String> get() = handlers.keys

    val queryItem: QueryItem get() = QueryItem(types = types, tags = about)

    fun matches(recorded: RecordedFact): Boolean {
        if (handlers.isNotEmpty() && recorded.type !in handlers) return false
        return about.all { it in recorded.tags }
    }

    fun apply(state: S, fact: Fact): S {
        val handler = handlers[fact.type] ?: return state
        return handler(state, fact)
    }

    fun fold(facts: List<RecordedFact>): S {
        var state = initial
        for (recorded in facts) {
            if (matches(recorded)) state = apply(state, recorded.payload)
        }
        return state
    }
}

class QuestionBuilder<S> internal constructor(
    private val initial: S,
    private val about: Set<Subject>,
) {
    private val handlers = linkedMapOf<String, (S, Fact) -> S>()

    inline fun <reified E : Fact> on(noinline handler: S.(E) -> S) {
        register(typeName<E>(), handler)
    }

    fun <E : Fact> register(type: String, handler: S.(E) -> S) {
        @Suppress("UNCHECKED_CAST")
        handlers[type] = { state, fact -> state.handler(fact as E) }
    }

    fun build(): Question<S> = Question(initial, about, handlers.toMap())
}

inline fun <reified E : Fact> typeName(): String =
    E::class.simpleName ?: error("Fact must be a named class")

fun <S> question(
    initial: S,
    about: About,
    build: QuestionBuilder<S>.() -> Unit,
): Question<S> = question(initial, subjects(about), build)

fun <S> question(
    initial: S,
    about: Set<Subject>,
    build: QuestionBuilder<S>.() -> Unit,
): Question<S> = QuestionBuilder(initial, about).apply(build).build()
