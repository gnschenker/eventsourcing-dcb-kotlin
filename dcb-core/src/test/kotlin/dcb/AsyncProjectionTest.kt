package dcb

import dcb.support.BoxClosed
import dcb.support.BoxLabeled
import dcb.support.BoxOpened
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncProjectionTest {
    @Test
    fun `catch-up folds matching facts and records the store head`() {
        val store = InMemoryEventStore()
        store.append(BoxOpened("b1"), BoxOpened("b2"), BoxClosed("b1"))

        val projection = store.projectAsync("box-b1", boxIsOpen("b1"))
        val snap = projection.catchUp()

        assertFalse(snap.state)
        assertEquals(Position(3), snap.asOf)
        assertEquals(false, projection.state)
    }

    @Test
    fun `unmatched facts still advance the snapshot to the store head`() {
        val store = InMemoryEventStore()
        val projection = store.projectAsync("box-b1", boxIsOpen("b1"))

        store.append(BoxOpened("b2"), BoxLabeled("b2", "cellar"))
        val first = projection.catchUp()
        assertFalse(first.state)
        assertEquals(Position(2), first.asOf)

        store.append(BoxOpened("b1"))
        val second = projection.catchUp()
        assertTrue(second.state)
        assertEquals(Position(3), second.asOf)
    }

    @Test
    fun `a new instance with the same snapshot store continues from the last head`() {
        val store = InMemoryEventStore()
        val snapshots = InMemoryProjectionStore<Boolean>()
        store.append(BoxOpened("b1"))

        store.projectAsync("box-b1", boxIsOpen("b1"), snapshots).catchUp()
        store.append(BoxClosed("b1"))

        val resumed = store.projectAsync("box-b1", boxIsOpen("b1"), snapshots)
        assertTrue(resumed.state)
        assertEquals(false, resumed.catchUp().state)
        assertEquals(Position(2), resumed.asOf)
    }

    @Test
    fun `rebuild folds the whole history again`() {
        val store = InMemoryEventStore()
        val snapshots = InMemoryProjectionStore<Boolean>()
        store.append(BoxOpened("b1"), BoxClosed("b1"))
        val projection = store.projectAsync("box-b1", boxIsOpen("b1"), snapshots)
        projection.catchUp()

        snapshots.save("box-b1", Snapshot(state = true, asOf = Position(2)))
        assertEquals(false, projection.rebuild().state)
    }

    @Test
    fun `catch-up to a position waits for a later append`() {
        val store = InMemoryEventStore()
        val projection = store.projectAsync("box-b1", boxIsOpen("b1"))
        val appender = Thread {
            Thread.sleep(50)
            store.append(BoxOpened("b1"))
        }
        appender.start()
        val snap = projection.catchUpTo(Position(1), timeoutMillis = 2_000)
        appender.join()
        assertTrue(snap.state)
        assertEquals(Position(1), snap.asOf)
    }

    @Test
    fun `catch-up to a position times out when nothing is appended`() {
        val store = InMemoryEventStore()
        val projection = store.projectAsync("box-b1", boxIsOpen("b1"))
        assertFailsWith<ProjectionLag> {
            projection.catchUpTo(Position(1), timeoutMillis = 30)
        }
    }

    @Test
    fun `a running projection picks up facts appended after it started`() {
        val store = InMemoryEventStore()
        val projection = store.projectAsync("box-b1", boxIsOpen("b1"))
        projection.start(pollMillis = 20).use {
            store.append(BoxOpened("b1"))
            val snap = projection.catchUpTo(Position(1), timeoutMillis = 2_000)
            assertTrue(snap.state)
        }
    }

    @Test
    fun `an empty store has no snapshot position`() {
        val projection = InMemoryEventStore().projectAsync("box-b1", boxIsOpen("b1"))
        val snap = projection.catchUp()
        assertFalse(snap.state)
        assertNull(snap.asOf)
    }
}

private fun boxIsOpen(box: String) = question(initial = false, about = Subject("box:$box")) {
    on<BoxOpened> { true }
    on<BoxClosed> { false }
}
