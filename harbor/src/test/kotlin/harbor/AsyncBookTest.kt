package harbor

import dcb.InMemoryEventStore
import dcb.Outcome
import dcb.append
import dcb.handle
import dcb.projectAsync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncBookTest {
    @Test
    fun `the pipeline tracks an open quote and drops it when bought`() {
        val store = InMemoryEventStore()
        val pipeline = store.projectAsync("sales-pipeline", salesPipeline())
        store.append(*pricedQuote())
        pipeline.catchUp()
        assertEquals("confirm", pipeline.state[q1]?.step)
        assertEquals(false, pipeline.state[q1]?.promo)

        store.append(PolicyIssued(p1, q1, ada, monthly = 28))
        pipeline.catchUp()
        assertNull(pipeline.state[q1])
    }

    @Test
    fun `the policy book records an issued policy`() {
        val store = InMemoryEventStore()
        val book = store.projectAsync("policy-book", policyBook())
        val monthly = harborRateCard().monthly(20, 500_000, false)!!
        store.append(*pricedQuote(promo = true))
        val outcome = store.handle(buyPolicy(q1, ada, p1, Spring))
        val position = (outcome as Outcome.Accepted).position!!
        book.catchUpTo(position)
        val issued = book.state[p1]!!
        assertEquals(ada, issued.customer)
        assertEquals(monthly, issued.monthly)
        assertEquals(Spring, issued.campaign)
        assertEquals(1, book.state.promoSeatsTaken)
    }

    @Test
    fun `a running book sees a purchase without a manual catch-up call from the writer`() {
        val store = InMemoryEventStore()
        val book = store.projectAsync("policy-book", policyBook())
        store.append(*pricedQuote())
        book.start(pollMillis = 20).use {
            val outcome = store.handle(buyPolicy(q1, ada, p1))
            val position = (outcome as Outcome.Accepted).position!!
            book.catchUpTo(position)
            assertTrue(book.state[p1] != null)
        }
    }
}
