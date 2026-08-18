package dcb

class InMemoryEventStore : EventStore {
    private val lock = Object()
    private val recorded = mutableListOf<RecordedFact>()

    override fun read(query: Query, after: Position?): ReadResult = synchronized(lock) {
        val head = recorded.lastOrNull()?.position
        val facts = recorded.filter { rec ->
            after.isBefore(rec.position) && query.matches(rec)
        }
        ReadResult(facts, head)
    }

    override fun append(facts: List<Fact>, condition: AppendCondition?): Position = synchronized(lock) {
        require(facts.isNotEmpty()) { "Cannot append an empty list of facts" }
        if (condition != null && hasConflict(condition)) {
            throw ConcurrencyConflict("A conflicting fact was recorded")
        }
        var last = recorded.lastOrNull()?.position?.value ?: 0L
        for (fact in facts) {
            last += 1
            recorded += RecordedFact(
                position = Position(last),
                type = fact.type,
                tags = fact.about,
                payload = fact,
            )
        }
        lock.notifyAll()
        Position(last)
    }

    override fun awaitAppend(after: Position?, timeoutMillis: Long): Boolean = synchronized(lock) {
        if (hasNews(after)) return true
        if (timeoutMillis <= 0) return false
        lock.wait(timeoutMillis)
        hasNews(after)
    }

    override fun subscribe(query: Query, after: Position): Sequence<RecordedFact> = sequence {
        var cursor = after
        while (true) {
            val batch = read(query, cursor).facts
            if (batch.isEmpty()) return@sequence
            for (fact in batch) {
                yield(fact)
                cursor = fact.position
            }
        }
    }

    private fun hasConflict(condition: AppendCondition): Boolean =
        recorded.any { rec ->
            condition.after.isBefore(rec.position) &&
                condition.failIfEventsMatch.matches(rec)
        }

    private fun hasNews(after: Position?): Boolean {
        val head = recorded.lastOrNull()?.position ?: return false
        return after.isBefore(head)
    }
}
