package harbor

import dcb.InMemoryEventStore
import dcb.append
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdHocScreenTest {
    @Test
    fun `the offer screen shows no open quote for a new customer`() {
        val store = InMemoryEventStore()
        store.append(*catalog())
        val screen = store.offerFor(ada)
        assertNull(screen.openQuote)
        assertTrue(20 in screen.terms)
        assertTrue(500_000 in screen.faces)
    }

    @Test
    fun `the offer screen offers to resume an open quote`() {
        val store = InMemoryEventStore()
        store.append(*openQuote())
        assertEquals(q1, store.offerFor(ada).openQuote)
    }

    @Test
    fun `review rebuilds coverage, riders, and the recorded premium`() {
        val store = InMemoryEventStore()
        val monthly = harborRateCard().monthly(20, 500_000, false)!! + 8
        store.append(*pricedQuote(riders = setOf(AccidentalDeath)))
        val screen = store.reviewOf(q1)
        assertEquals(20, screen.coverage?.termYears)
        assertEquals(setOf(AccidentalDeath), screen.riders)
        assertEquals(monthly, screen.price?.monthly)
        assertEquals(false, screen.promo)
    }

    @Test
    fun `a later rate card does not change a premium already recorded`() {
        val store = InMemoryEventStore()
        val first = harborRateCard(version = 1).monthly(20, 500_000, false)!!
        store.append(*pricedQuote())
        store.append(harborRateCard(version = 2).copy(rows = harborRateCard().rows.map { it.copy(monthly = it.monthly + 50) }))
        assertEquals(first, store.premiumOn(q1)?.monthly)
        assertEquals(1, store.premiumOn(q1)?.cardVersion)
    }
}
