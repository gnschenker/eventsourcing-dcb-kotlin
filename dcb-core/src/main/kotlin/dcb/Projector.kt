package dcb

interface CheckpointStore {
    fun load(name: String): Position?
    fun save(name: String, position: Position)
}

class InMemoryCheckpointStore : CheckpointStore {
    private val lock = Any()
    private val positions = mutableMapOf<String, Position>()

    override fun load(name: String): Position? = synchronized(lock) { positions[name] }

    override fun save(name: String, position: Position) {
        synchronized(lock) { positions[name] = position }
    }
}

/**
 * Catch-up projector: apply facts after the last checkpoint, then record progress.
 * The same [Question] used for decisions can drive a read model.
 */
class Projector(
    val name: String,
    private val store: EventStore,
    private val checkpoints: CheckpointStore,
    private val query: Query = Query.all(),
) {
    constructor(
        name: String,
        store: EventStore,
        checkpoints: CheckpointStore,
        question: Question<*>,
    ) : this(name, store, checkpoints, Query.of(question.queryItem))

    fun catchUp(handle: (RecordedFact) -> Unit): Position? {
        val after = checkpoints.load(name) ?: Position(0)
        var last = after
        for (fact in store.subscribe(query, after)) {
            handle(fact)
            last = fact.position
        }
        if (last != after) checkpoints.save(name, last)
        return checkpoints.load(name)
    }
}

class FoldingProjector<S>(
    val name: String,
    private val store: EventStore,
    private val checkpoints: CheckpointStore,
    private val question: Question<S>,
) {
    var state: S = question.initial
        private set

    private val projector = Projector(name, store, checkpoints, question)

    fun catchUp(): S {
        projector.catchUp { recorded ->
            if (question.matches(recorded)) {
                state = question.apply(state, recorded.payload)
            }
        }
        return state
    }
}
