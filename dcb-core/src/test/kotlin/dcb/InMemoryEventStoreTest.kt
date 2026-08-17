package dcb

import dcb.support.BoxClosed
import dcb.support.BoxOpened
import dcb.support.ItemPlaced
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryEventStoreTest {
    private val store = InMemoryEventStore()

    @Test
    fun `append assigns monotonic positions and read returns store head`() {
        store.append(BoxOpened("b1"))
        store.append(BoxOpened("b2"), BoxClosed("b1"))

        val all = store.read(Query.all())
        assertEquals(listOf(1L, 2L, 3L), all.facts.map { it.position.value })
        assertEquals(Position(3), all.head)

        val onlyB1 = store.read(Query.of(QueryItem(tags = subjects(Subject("box:b1")))))
        assertEquals(listOf("BoxOpened", "BoxClosed"), onlyB1.facts.map { it.type })
        assertEquals(Position(3), onlyB1.head)
    }

    @Test
    fun `read after position skips earlier facts but head is still the store head`() {
        store.append(BoxOpened("b1"), BoxOpened("b2"), BoxClosed("b1"))

        val afterFirst = store.read(Query.all(), after = Position(1))
        assertEquals(listOf(2L, 3L), afterFirst.facts.map { it.position.value })
        assertEquals(Position(3), afterFirst.head)
    }

    @Test
    fun `empty store read has no head`() {
        val result = store.read(Query.all())
        assertTrue(result.facts.isEmpty())
        assertNull(result.head)
    }

    @Test
    fun `append condition fails when a matching fact exists after the known head`() {
        store.append(BoxOpened("b1"))
        val head = store.read(Query.all()).head
        store.append(BoxClosed("b1"))

        assertFailsWith<ConcurrencyConflict> {
            store.append(
                listOf(ItemPlaced("b1", "key")),
                AppendCondition(
                    failIfEventsMatch = Query.of(QueryItem(tags = subjects(Subject("box:b1")))),
                    after = head,
                ),
            )
        }
    }

    @Test
    fun `append condition ignores facts before after`() {
        store.append(BoxOpened("b1"))
        val head = store.read(Query.all()).head

        val position = store.append(
            listOf(BoxClosed("b1")),
            AppendCondition(
                failIfEventsMatch = Query.of(QueryItem(tags = subjects(Subject("box:b1")))),
                after = head,
            ),
        )
        assertEquals(Position(2), position)
    }

    @Test
    fun `append condition without after fails if any matching fact exists`() {
        store.append(BoxOpened("b1"))

        assertFailsWith<ConcurrencyConflict> {
            store.append(
                listOf(BoxOpened("b1")),
                AppendCondition(
                    failIfEventsMatch = Query.of(
                        QueryItem(types = setOf("BoxOpened"), tags = subjects(Subject("box:b1"))),
                    ),
                ),
            )
        }
    }

    @Test
    fun `unrelated facts do not trip the append condition`() {
        store.append(BoxOpened("b2"))
        store.append(
            listOf(BoxOpened("b1")),
            AppendCondition(
                failIfEventsMatch = Query.of(
                    QueryItem(types = setOf("BoxOpened"), tags = subjects(Subject("box:b1"))),
                ),
            ),
        )
        assertEquals(2, store.read(Query.all()).facts.size)
    }

    @Test
    fun `cannot append an empty list`() {
        assertFailsWith<IllegalArgumentException> {
            store.append(emptyList())
        }
    }

    @Test
    fun `subscribe yields facts after the cursor`() {
        store.append(BoxOpened("b1"), BoxOpened("b2"), BoxClosed("b1"))
        val seen = store.subscribe(
            Query.of(QueryItem(tags = subjects(Subject("box:b1")))),
            after = Position(0),
        ).toList()
        assertEquals(listOf("BoxOpened", "BoxClosed"), seen.map { it.type })
    }
}
