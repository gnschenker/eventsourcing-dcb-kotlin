package dcb

internal fun <S> catchUpProjection(
    name: String,
    definition: Question<S>,
    snapshots: ProjectionStore<S>,
    store: EventStore,
): Snapshot<S> {
    val hint = snapshots.load(name)
    val after = hint?.asOf ?: Position(0)
    val read = store.read(Query.of(definition.queryItem), after)
    val previous = snapshots.load(name)
    val head = read.head ?: return previous ?: Snapshot(definition.initial, null)
    return persistIfChanged(name, definition, snapshots, previous, read.facts, head)
}

internal fun <S> applyAppended(
    name: String,
    definition: Question<S>,
    snapshots: ProjectionStore<S>,
    recorded: List<RecordedFact>,
    head: Position,
    store: EventStore,
): Snapshot<S> {
    val previous = snapshots.load(name)
    val after = previous?.asOf?.value ?: 0L
    val firstNew = recorded.minOfOrNull { it.position.value }
    if (firstNew != null && firstNew > after + 1) {
        catchUpProjection(name, definition, snapshots, store)
    }
    return persistIfChanged(name, definition, snapshots, snapshots.load(name), recorded, head)
}

internal fun <S> persistIfChanged(
    name: String,
    definition: Question<S>,
    snapshots: ProjectionStore<S>,
    previous: Snapshot<S>?,
    facts: List<RecordedFact>,
    head: Position,
): Snapshot<S> {
    if (previous?.asOf != null && previous.asOf.value > head.value) return previous
    var state = previous?.state ?: definition.initial
    var applied = false
    for (fact in facts) {
        if (previous?.asOf != null && !previous.asOf.isBefore(fact.position)) continue
        if (head.isBefore(fact.position)) continue
        if (!definition.matches(fact)) continue
        state = definition.apply(state, fact.payload)
        applied = true
    }
    if (previous?.asOf == head && !applied) return previous
    val snap = Snapshot(state, head)
    snapshots.save(name, snap)
    return snap
}
