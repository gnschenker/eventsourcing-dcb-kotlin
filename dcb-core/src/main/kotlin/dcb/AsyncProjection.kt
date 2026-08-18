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

    @Volatile
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
            check(worker?.isAlive != true) { "Projection '$name' is already running" }
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
                        } catch (_: Exception) {
                            if (!running) break
                            try {
                                store.awaitAppend(asOf, pollMillis)
                            } catch (_: InterruptedException) {
                                break
                            } catch (_: Exception) {
                                Thread.sleep(pollMillis.coerceAtLeast(1))
                            }
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
        val thread = worker
        thread?.interrupt()
        thread?.join(5_000)
        synchronized(gate) {
            if (worker === thread && thread?.isAlive != true) worker = null
        }
    }

    private fun catchUpUnlocked(): Snapshot<S> =
        catchUpProjection(name, definition, snapshots, store)
}

fun <S> EventStore.projectAsync(
    name: String,
    definition: Question<S>,
    snapshots: ProjectionStore<S> = InMemoryProjectionStore(),
): AsyncProjection<S> = AsyncProjection(name, this, definition, snapshots)
