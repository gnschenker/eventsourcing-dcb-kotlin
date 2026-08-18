package dcb

@JvmInline
value class Position(val value: Long) : Comparable<Position> {
    override fun compareTo(other: Position): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()
}

data class RecordedFact(
    val position: Position,
    val type: String,
    val tags: Set<Subject>,
    val payload: Fact,
)

data class ReadResult(
    val facts: List<RecordedFact>,
    /** Highest position in the store at read time, not the last match. */
    val head: Position?,
)

data class AppendCondition(
    val failIfEventsMatch: Query,
    val after: Position? = null,
)

class ConcurrencyConflict(message: String) : RuntimeException(message)

interface EventStore {
    fun read(query: Query, after: Position? = null): ReadResult

    fun append(facts: List<Fact>, condition: AppendCondition? = null): Position

    fun subscribe(query: Query, after: Position): Sequence<RecordedFact>

    /**
     * Block until a fact may have been appended after [after], or [timeoutMillis] elapses.
     * Returns true if the caller should read again. The default implementation polls.
     */
    fun awaitAppend(after: Position?, timeoutMillis: Long): Boolean {
        val wait = timeoutMillis.coerceIn(1, 50)
        Thread.sleep(wait)
        return true
    }
}

fun EventStore.append(vararg facts: Fact, condition: AppendCondition? = null): Position =
    append(facts.toList(), condition)

fun EventStore.handle(decision: PreparedDecision): Outcome = decision.run(this)

fun Position?.isBefore(other: Position): Boolean =
    this == null || other.value > value
