package dcb

/** Persists a named projection snapshot so catch-up can resume after a restart. */
interface ProjectionStore<S> {
    fun load(name: String): Snapshot<S>?
    fun save(name: String, snapshot: Snapshot<S>)
    fun delete(name: String)
}

class InMemoryProjectionStore<S> : ProjectionStore<S> {
    private val lock = Any()
    private val snapshots = mutableMapOf<String, Snapshot<S>>()

    override fun load(name: String): Snapshot<S>? = synchronized(lock) { snapshots[name] }

    override fun save(name: String, snapshot: Snapshot<S>) {
        synchronized(lock) { snapshots[name] = snapshot }
    }

    override fun delete(name: String) {
        synchronized(lock) { snapshots.remove(name) }
    }
}

class ProjectionLag(message: String) : RuntimeException(message)
