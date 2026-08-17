package dcb

import dcb.support.BoxClosed
import dcb.support.BoxLabeled
import dcb.support.BoxOpened
import dcb.support.ItemPlaced
import dcb.testing.given
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecisionTest {
    @Test
    fun `question folds only matching facts`() {
        val open = boxIsOpen("b1")
        val store = InMemoryEventStore()
        store.append(BoxOpened("b1"), BoxOpened("b2"), BoxClosed("b1"), BoxOpened("b1"))

        val state = open.fold(store.read(Query.of(open.queryItem)).facts)
        assertTrue(state)
    }

    @Test
    fun `requiring questions become the lock query, considering does not`() {
        val prepared = putItem("b1", "key")
        val lock = prepared.lockQuery()!!
        val read = prepared.readQuery()

        assertEquals(setOf("BoxOpened", "BoxClosed"), lock.items.single().types)
        assertTrue(read.items.any { "BoxLabeled" in it.types })
        assertTrue(read.items.any { "BoxOpened" in it.types })
    }

    @Test
    fun `a decision records a fact when the rules pass`() {
        given(BoxOpened("b1")).whenever {
            putItem("b1", "key")
        }.expect(ItemPlaced("b1", "key"))
    }

    @Test
    fun `a decision rejects with a business reason`() {
        given(BoxOpened("b1"), BoxClosed("b1")).whenever {
            putItem("b1", "key")
        }.expectRejection("Box b1 is not open")
    }

    @Test
    fun `considering a fact does not lock on it`() {
        val store = InMemoryEventStore()
        store.append(BoxOpened("b1"), BoxLabeled("b1", "attic"))
        val head = store.read(Query.all()).head

        store.append(BoxLabeled("b1", "cellar"))

        val condition = AppendCondition(putItem("b1", "key").lockQuery()!!, head)
        store.append(listOf(ItemPlaced("b1", "key")), condition)
        assertEquals(4, store.read(Query.all()).facts.size)
    }

    @Test
    fun `requiring a fact locks on it`() {
        val store = InMemoryEventStore()
        store.append(BoxOpened("b1"))
        val head = store.read(Query.all()).head
        store.append(BoxClosed("b1"))

        assertFailsWith<ConcurrencyConflict> {
            store.append(
                listOf(ItemPlaced("b1", "key")),
                AppendCondition(putItem("b1", "key").lockQuery()!!, head),
            )
        }
    }

    @Test
    fun `decision with only considering appends unconditionally`() {
        val prepared = decision {
            val label by considering { boxLabel("b1") }
            decide {
                then(BoxLabeled("b1", label ?: "none"))
            }
        }
        assertNull(prepared.lockQuery())
        given().whenever { prepared }.expect(BoxLabeled("b1", "none"))
    }
}

private fun boxIsOpen(box: String) = question(initial = false, about = Subject("box:$box")) {
    on<BoxOpened> { true }
    on<BoxClosed> { false }
}

private fun boxLabel(box: String) = question(initial = null as String?, about = Subject("box:$box")) {
    on<BoxLabeled> { it.label }
}

private fun putItem(box: String, item: String) = decision {
    val open by requiring { boxIsOpen(box) }
    val label by considering { boxLabel(box) }
    decide {
        unless(open) { "Box $box is not open" }
        then(ItemPlaced(box, item))
    }
}
