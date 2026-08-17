package dcb

import dcb.support.BoxClosed
import dcb.support.BoxOpened
import dcb.support.ItemPlaced
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectorTest {
    @Test
    fun `catch-up applies only new facts and records a checkpoint`() {
        val store = InMemoryEventStore()
        val checkpoints = InMemoryCheckpointStore()
        val seen = mutableListOf<String>()
        val projector = Projector("boxes", store, checkpoints, Query.all())

        store.append(BoxOpened("b1"), BoxOpened("b2"))
        projector.catchUp { seen += "${it.type}:${(it.payload as? BoxOpened)?.box ?: (it.payload as BoxClosed).box}" }

        assertEquals(listOf("BoxOpened:b1", "BoxOpened:b2"), seen)
        assertEquals(Position(2), checkpoints.load("boxes"))

        store.append(BoxClosed("b1"))
        projector.catchUp { seen += "${it.type}:${(it.payload as? BoxOpened)?.box ?: (it.payload as BoxClosed).box}" }
        assertEquals(listOf("BoxOpened:b1", "BoxOpened:b2", "BoxClosed:b1"), seen)
        assertEquals(Position(3), checkpoints.load("boxes"))
    }

    @Test
    fun `a folding projector rebuilds a question as a read model`() {
        val store = InMemoryEventStore()
        val open = question(initial = false, about = Subject("box:b1")) {
            on<BoxOpened> { true }
            on<BoxClosed> { false }
        }
        val projector = FoldingProjector("box-b1-open", store, InMemoryCheckpointStore(), open)

        store.append(BoxOpened("b1"), ItemPlaced("b1", "key"), BoxClosed("b1"), BoxOpened("b1"))
        assertEquals(true, projector.catchUp())
        assertEquals(true, projector.state)
    }
}
