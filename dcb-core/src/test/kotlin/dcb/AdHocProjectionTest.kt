package dcb

import dcb.support.BoxClosed
import dcb.support.BoxLabeled
import dcb.support.BoxOpened
import dcb.support.ItemPlaced
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdHocProjectionTest {
    @Test
    fun `ask folds a question from the store without a checkpoint`() {
        val store = InMemoryEventStore()
        store.append(BoxOpened("b1"), BoxOpened("b2"), BoxClosed("b1"), BoxOpened("b1"))

        assertTrue(store.ask(boxIsOpen("b1")))
        assertTrue(store.ask(boxIsOpen("b2")))
        assertFalse(store.ask(boxIsOpen("missing")))
    }

    @Test
    fun `project reports store head as of the read`() {
        val store = InMemoryEventStore()
        store.append(BoxOpened("b1"), BoxOpened("b2"))

        val snapshot = store.project(boxIsOpen("b1"))
        assertTrue(snapshot.state)
        assertEquals(Position(2), snapshot.asOf)
    }

    @Test
    fun `an empty store answers with the initial state and no position`() {
        val store = InMemoryEventStore()
        val snapshot = store.project(boxIsOpen("b1"))
        assertFalse(snapshot.state)
        assertNull(snapshot.asOf)
    }

    @Test
    fun `unrelated facts are not loaded into the projection`() {
        val store = InMemoryEventStore()
        store.append(BoxOpened("b2"), ItemPlaced("b2", "key"), BoxLabeled("b1", "attic"))

        assertFalse(store.ask(boxIsOpen("b1")))
        assertEquals("attic", store.ask(boxLabel("b1")))
    }

    @Test
    fun `a composed ad-hoc projection reads once and answers a view`() {
        val store = InMemoryEventStore()
        store.append(
            BoxOpened("b1"),
            BoxLabeled("b1", "attic"),
            ItemPlaced("b1", "key"),
            BoxClosed("b1"),
        )

        val snapshot = store.project {
            val open by lookingAt { boxIsOpen("b1") }
            val label by lookingAt { boxLabel("b1") }
            answer { "${if (open) "open" else "closed"}:$label" }
        }

        assertEquals("closed:attic", snapshot.state)
        assertEquals(Position(4), snapshot.asOf)
    }

    @Test
    fun `composed projection requires lookingAt and answer`() {
        val store = InMemoryEventStore()
        assertFailsWith<IllegalStateException> {
            store.project<String> {
                val open by lookingAt { boxIsOpen("b1") }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            store.project<String> {
                answer { "nothing" }
            }
        }
    }

    @Test
    fun `projection is a question under another name`() {
        val definition = projection(initial = 0, about = Subject("box:b1")) {
            on<ItemPlaced> { this + 1 }
        }
        val store = InMemoryEventStore()
        store.append(ItemPlaced("b1", "key"), ItemPlaced("b1", "coin"))
        assertEquals(2, store.ask(definition))
    }
}

private fun boxIsOpen(box: String) = question(initial = false, about = Subject("box:$box")) {
    on<BoxOpened> { true }
    on<BoxClosed> { false }
}

private fun boxLabel(box: String) = question(initial = null as String?, about = Subject("box:$box")) {
    on<BoxLabeled> { it.label }
}
