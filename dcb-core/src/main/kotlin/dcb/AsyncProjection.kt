package dcb

/**
 * A named read model that catches up after facts are appended.
 *
 * State and the store head are saved together. A new instance with the same
 * name and store continues from that snapshot instead of folding from scratch.
 */
class AsyncProjection<S>(
    val name: String,
    private val store: EventStore,
    private val definition: Question<S>,
    private val snapshots: ProjectionStore<S>,
) {
    private val gate = Any()

    @Volatile
    private var running = false
    private var worker: Thread? = null

    val query: Query get() = Query.of(definition.queryItem)

    fun snapshot(): Snapshot<S> =
        snapshots.load(name) ?: Snapshot(definition.initial, asOf = null)

    val state: S get() = snapshot().state

    val asOf: Position? get() = snapshot().asOf

    fun catchUp(): Snapshot<S> = synchronized(gate) { catchUpUnlocked() }

    /**
     * Catch up until this projection has seen at least [atLeast], waiting for
     * new appends if needed.
     */
    fun catchUpTo(atLeast: Position, timeoutMillis: Long = 5_000): Snapshot<S> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            val snap = catchUp()
            if (snap.asOf != null && !snap.asOf.isBefore(atLeast)) return snap
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                throw ProjectionLag("Projection '$name' did not reach $atLeast (asOf=${snap.asOf})")
            }
            store.awaitAppend(snap.asOf, remaining)
        }
    }

    fun rebuild(): Snapshot<S> = synchronized(gate) {
        snapshots.delete(name)
        catchUpUnlocked()
    }

    fun start(pollMillis: Long = 50): AutoCloseable {
        synchronized(gate) {
            check(worker == null) { "Projection '$name' is already running" }
            running = true
            worker = Thread(
                {
                    while (running) {
                        try {
                            val snap = catchUp()
                            if (!running) break
                            store.awaitAppend(snap.asOf, pollMillis)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                },
                "dcb-projection-$name",
            ).apply {
                isDaemon = true
                start()
            }
        }
        return AutoCloseable { stop() }
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker?.join(1_000)
        synchronized(gate) { worker = null }
    }

    private fun catchUpUnlocked(): Snapshot<S> {
        val previous = snapshots.load(name)
        val after = previous?.asOf ?: Position(0)
        val read = store.read(query, after)
        var state = previous?.state ?: definition.initial
        for (fact in read.facts) {
            state = definition.apply(state, fact.payload)
        }
        val head = read.head ?: return previous ?: Snapshot(definition.initial, null)
        if (previous?.asOf == head && read.facts.isEmpty()) return previous
        val snap = Snapshot(state, head)
        snapshots.save(name, snap)
        return snap
    }
}

fun <S> EventStore.projectAsync(
    name: String,
    definition: Question<S>,
    snapshots: ProjectionStore<S> = InMemoryProjectionStore(),
): AsyncProjection<S> = AsyncProjection(name, this, definition, snapshots)
