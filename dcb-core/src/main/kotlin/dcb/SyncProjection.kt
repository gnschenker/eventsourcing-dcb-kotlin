package dcb

/**
 * A named read model updated in the same transaction as [EventStore.append].
 *
 * After [projectSync] returns, [state] already includes every fact that has
 * been appended on this store. A failed projection write rolls the append back.
 */
class SyncProjection<S>(
    val name: String,
    private val store: EventStore,
    private val definition: Question<S>,
    private val snapshots: ProjectionStore<S>,
) : SyncHandler, AutoCloseable {
    private val gate = Any()

    fun snapshot(): Snapshot<S> =
        snapshots.load(name) ?: Snapshot(definition.initial, asOf = null)

    val state: S get() = snapshot().state

    val asOf: Position? get() = snapshot().asOf

    fun catchUp(): Snapshot<S> {
        val hint = snapshots.load(name)
        val read = store.read(Query.of(definition.queryItem), hint?.asOf ?: Position(0))
        return synchronized(gate) {
            val previous = snapshots.load(name)
            val head = read.head ?: return@synchronized previous ?: Snapshot(definition.initial, null)
            persistIfChanged(name, definition, snapshots, previous, read.facts, head)
        }
    }

    override fun onAppend(recorded: List<RecordedFact>, head: Position) {
        synchronized(gate) {
            applyAppended(name, definition, snapshots, recorded, head, store)
        }
    }

    override fun captureRollback(): () -> Unit {
        val before = snapshots.load(name)
        return {
            if (before == null) snapshots.delete(name)
            else snapshots.save(name, before)
        }
    }

    override fun close() {
        store.detachSync(this)
    }
}

fun <S> EventStore.projectSync(
    name: String,
    definition: Question<S>,
    snapshots: ProjectionStore<S> = InMemoryProjectionStore(),
): SyncProjection<S> {
    val projection = SyncProjection(name, this, definition, snapshots)
    projection.catchUp()
    attachSync(projection)
    projection.catchUp()
    return projection
}
