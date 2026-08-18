package harbor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactsTest {
    private val ada = CustomerId("c17")
    private val quote = QuoteId("q9f3")
    private val policy = PolicyId("p441")

    @Test
    fun `journey started is about the quote and the customer`() {
        val fact = JourneyStarted(quote, ada)
        assertTrue(quote.asSubject() in fact.about)
        assertTrue(ada.asSubject() in fact.about)
    }

    @Test
    fun `rider added is about the quote and the rider`() {
        val fact = RiderAdded(quote, AccidentalDeath)
        assertEquals(setOf(quote.asSubject(), AccidentalDeath.asSubject()), fact.about)
    }

    @Test
    fun `policy issued includes the campaign only when a promo is taken`() {
        val withPromo = PolicyIssued(policy, quote, ada, monthly = 28, campaign = Spring)
        assertTrue(Spring.asSubject() in withPromo.about)

        val without = PolicyIssued(policy, quote, ada, monthly = 28)
        assertTrue(Spring.asSubject() !in without.about)
        assertEquals(3, without.about.size)
    }

    @Test
    fun `the rate card has a row for every term, face, and tobacco class`() {
        val card = harborRateCard()
        assertEquals(HarborTerms.size * HarborFaces.size * 2, card.rows.size)
        assertTrue((card.monthly(20, 500_000, tobacco = false) ?: 0) > 0)
        assertTrue(
            (card.monthly(20, 500_000, tobacco = true) ?: 0) >
                (card.monthly(20, 500_000, tobacco = false) ?: 0),
        )
    }
}
