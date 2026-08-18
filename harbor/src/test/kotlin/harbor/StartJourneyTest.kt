package harbor

import dcb.testing.given
import kotlin.test.Test

class StartJourneyTest {
    @Test
    fun `Harbor Term can be seeded once`() {
        given().whenever { seedHarbor() }.expect(
            ProductDefined(HarborTerm, "Harbor Term Life", HarborTerms, HarborFaces),
            harborRateCard(version = 1),
            RiderCatalogued(HarborTerm, AccidentalDeath, 8),
            RiderCatalogued(HarborTerm, WaiverOfPremium, 5),
            RiderCatalogued(HarborTerm, ChildrensTerm, 6),
            CampaignOpened(Spring, HarborTerm, 100, 0.05),
        )
    }

    @Test
    fun `Harbor Term cannot be seeded twice`() {
        given(ProductDefined(HarborTerm, "Harbor Term Life", HarborTerms, HarborFaces)).whenever {
            seedHarbor()
        }.expectRejection("Harbor Term is already defined")
    }

    private val ada = CustomerId("c17")
    private val q1 = QuoteId("q1")
    private val q2 = QuoteId("q2")

    @Test
    fun `a customer can start a journey`() {
        given().whenever {
            startJourney(ada, q1)
        }.expect(JourneyStarted(q1, ada))
    }

    @Test
    fun `a quote id cannot be started twice`() {
        given(JourneyStarted(q1, ada)).whenever {
            startJourney(ada, q1)
        }.expectRejection("Quote q1 is already started")
    }

    @Test
    fun `starting a second journey abandons the open quote`() {
        given(JourneyStarted(q1, ada)).whenever {
            startJourney(ada, q2)
        }.expect(
            QuoteAbandoned(q1, ada),
            JourneyStarted(q2, ada),
        )
    }
}
