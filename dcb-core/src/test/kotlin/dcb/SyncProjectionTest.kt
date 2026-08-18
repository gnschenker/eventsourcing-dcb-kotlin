package dcb

import dcb.support.BoxClosed
import dcb.support.BoxOpened
import dcb.support.ItemPlaced
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncProjectionTest {
    @Test
    fun `append updates the projection before it returns`() {
        val store = InMemoryEventStore()
        val projection = store.projectSync("box-b1", boxIsOpen("b1"))

        store.append(BoxOpened("b1"), BoxOpened("b2"))
        assertTrue(projection.state)
        assertEquals(Position(2), projection.asOf)
    }

    @Test
    fun `a rejected append does not change the projection`() {
        val store = InMemoryEventStore()
        val projection = store.projectSync("box-b1", boxIsOpen("b1"))
        store.append(BoxOpened("b1"))

        assertFailsWith<ConcurrencyConflict> {
            store.append(
                listOf(BoxClosed("b1")),
                AppendCondition(
                    failIfEventsMatch = Query.of(QueryItem(tags = subjects(Subject("box:b1")))),
                    after = Position(0),
                ),
            )
        }
        assertTrue(projection.state)
        assertEquals(Position(1), projection.asOf)
    }

    @Test
    fun `a failing projection rolls the facts back`() {
        val store = InMemoryEventStore()
        store.projectSync("boom", exploding())
        assertFailsWith<IllegalStateException> { store.append(Boom()) }
        assertTrue(store.read(Query.all()).facts.isEmpty())
    }

    @Test
    fun `closing the projection stops further updates`() {
        val store = InMemoryEventStore()
        val projection = store.projectSync("box-b1", boxIsOpen("b1"))
        store.append(BoxOpened("b1"))
        projection.close()
        store.append(BoxClosed("b1"))
        assertTrue(projection.state)
        assertEquals(Position(1), projection.asOf)
    }

    @Test
    fun `unrelated facts still advance asOf`() {
        val store = InMemoryEventStore()
        val projection = store.projectSync("box-b1", boxIsOpen("b1"))
        store.append(ItemPlaced("b2", "key"))
        assertFalse(projection.state)
        assertEquals(Position(1), projection.asOf)
    }

    @Test
    fun `install catch-up includes facts appended before projectSync`() {
        val store = InMemoryEventStore()
        store.append(BoxOpened("b1"), BoxClosed("b1"))
        val projection = store.projectSync("box-b1", boxIsOpen("b1"))
        assertFalse(projection.state)
        assertEquals(Position(2), projection.asOf)
    }
}

private fun boxIsOpen(box: String) = question(initial = false, about = Subject("box:$box")) {
    on<BoxOpened> { true }
    on<BoxClosed> { false }
}

private data class Boom(val id: String = "x") : Fact {
    override val about = emptySet<Subject>()
}

private fun exploding() = question(initial = 0, about = emptySet()) {
    on<Boom> { error("boom") }
}
