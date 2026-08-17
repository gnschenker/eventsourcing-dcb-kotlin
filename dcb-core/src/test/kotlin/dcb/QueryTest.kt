package dcb

import dcb.support.BoxClosed
import dcb.support.BoxLabeled
import dcb.support.BoxOpened
import dcb.support.ItemPlaced
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryTest {
    private val opened = recorded(1, BoxOpened("b1"))
    private val closed = recorded(2, BoxClosed("b1"))
    private val other = recorded(3, BoxOpened("b2"))
    private val labeled = recorded(4, BoxLabeled("b1", "attic"))
    private val placed = recorded(5, ItemPlaced("b1", "key"))

    @Test
    fun `empty query matches every fact`() {
        assertTrue(Query.all().matches(opened))
        assertTrue(Query.all().matches(placed))
    }

    @Test
    fun `type filter matches any listed type`() {
        val query = Query.of(QueryItem(types = setOf("BoxOpened", "BoxClosed")))
        assertTrue(query.matches(opened))
        assertTrue(query.matches(closed))
        assertFalse(query.matches(labeled))
    }

    @Test
    fun `tag filter requires every listed subject`() {
        val box = Query.of(QueryItem(tags = subjects(Subject("box:b1"))))
        assertTrue(box.matches(opened))
        assertTrue(box.matches(placed))
        assertFalse(box.matches(other))

        val boxAndItem = Query.of(
            QueryItem(tags = subjects(Subject("box:b1"), Subject("item:key"))),
        )
        assertTrue(boxAndItem.matches(placed))
        assertFalse(boxAndItem.matches(opened))
    }

    @Test
    fun `query items are combined with OR`() {
        val query = Query.of(
            QueryItem(types = setOf("BoxOpened"), tags = subjects(Subject("box:b1"))),
            QueryItem(types = setOf("ItemPlaced"), tags = subjects(Subject("item:key"))),
        )
        assertTrue(query.matches(opened))
        assertTrue(query.matches(placed))
        assertFalse(query.matches(other))
        assertFalse(query.matches(closed))
    }

    @Test
    fun `empty types means any type with those tags`() {
        val query = Query.of(QueryItem(tags = subjects(Subject("box:b1"))))
        assertTrue(query.matches(opened))
        assertTrue(query.matches(labeled))
    }
}

private fun recorded(position: Long, fact: Fact) = RecordedFact(
    position = Position(position),
    type = fact.type,
    tags = fact.about,
    payload = fact,
)
