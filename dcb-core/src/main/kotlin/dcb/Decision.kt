package dcb

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

sealed class Outcome {
    data class Accepted(val facts: List<Fact>, val position: Position? = null) : Outcome()
    data class Rejected(val reason: String) : Outcome()
}

class BoundQuestion<S> internal constructor(
    val question: Question<S>,
    val lock: Boolean,
    internal var state: S = question.initial,
) : ReadOnlyProperty<Any?, S> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): S = state

    internal fun fold(facts: List<RecordedFact>) {
        state = question.fold(facts)
    }
}

class DecisionBuilder internal constructor() {
    internal val questions = mutableListOf<BoundQuestion<*>>()
    internal var decideBlock: (DecisionBuilder.() -> Unit)? = null
    private var rejected: String? = null
    private val pending = mutableListOf<Fact>()

    /**
     * Load this question and treat it as part of the consistency boundary.
     * If matching facts appear after we looked, the append fails.
     */
    fun <S> requiring(make: () -> Question<S>): BoundQuestion<S> {
        val bound = BoundQuestion(make(), lock = true)
        questions += bound
        return bound
    }

    /**
     * Load this question for the decision, but do not lock on it.
     * Concurrent facts that only affect this question will not bounce the write.
     */
    fun <S> considering(make: () -> Question<S>): BoundQuestion<S> {
        val bound = BoundQuestion(make(), lock = false)
        questions += bound
        return bound
    }

    fun decide(block: DecisionBuilder.() -> Unit) {
        decideBlock = block
    }

    fun unless(condition: Boolean, reason: () -> String) {
        if (!condition && rejected == null) {
            rejected = reason()
            pending.clear()
        }
    }

    fun then(vararg facts: Fact) {
        if (rejected == null) pending += facts
    }

    internal fun resetVerdict() {
        rejected = null
        pending.clear()
    }

    internal fun outcome(): Outcome {
        val reason = rejected
        return if (reason != null) Outcome.Rejected(reason) else Outcome.Accepted(pending.toList())
    }

    internal fun readQuery(): Query = Query.of(questions.map { it.question.queryItem })

    internal fun lockQuery(): Query? {
        val items = questions.filter { it.lock }.map { it.question.queryItem }
        return if (items.isEmpty()) null else Query.of(items)
    }
}

class PreparedDecision internal constructor(
    private val builder: DecisionBuilder,
) {
    fun readQuery(): Query = builder.readQuery()

    fun lockQuery(): Query? = builder.lockQuery()

    fun run(store: EventStore): Outcome {
        val decide = builder.decideBlock ?: error("Decision is missing a decide { } block")
        builder.resetVerdict()
        val read = store.read(builder.readQuery())
        for (question in builder.questions) {
            question.fold(read.facts)
        }
        decide(builder)
        return when (val outcome = builder.outcome()) {
            is Outcome.Rejected -> outcome
            is Outcome.Accepted -> {
                if (outcome.facts.isEmpty()) outcome
                else {
                    val lock = builder.lockQuery()
                    val condition = lock?.let { AppendCondition(it, read.head) }
                    val position = store.append(outcome.facts, condition)
                    outcome.copy(position = position)
                }
            }
        }
    }
}

fun decision(block: DecisionBuilder.() -> Unit): PreparedDecision {
    val builder = DecisionBuilder()
    builder.block()
    return PreparedDecision(builder)
}
