package harbor

import dcb.Fact

internal val ada = CustomerId("c17")
internal val grace = CustomerId("c22")
internal val q1 = QuoteId("q1")
internal val q2 = QuoteId("q2")
internal val p1 = PolicyId("p1")
internal val p2 = PolicyId("p2")

internal fun catalog(capacity: Int = 100): Array<Fact> = arrayOf(
    ProductDefined(HarborTerm, "Harbor Term Life", HarborTerms, HarborFaces),
    harborRateCard(version = 1),
    RiderCatalogued(HarborTerm, AccidentalDeath, 8),
    RiderCatalogued(HarborTerm, WaiverOfPremium, 5),
    RiderCatalogued(HarborTerm, ChildrensTerm, 6),
    CampaignOpened(Spring, HarborTerm, capacity, 0.05),
)

internal fun openQuote(customer: CustomerId = ada, quote: QuoteId = q1): Array<Fact> =
    catalog() + JourneyStarted(quote, customer)
