package harbor

import dcb.decision

fun seedHarbor(capacity: Int = 100) = decision {
    val exists by requiring { productExists(HarborTerm) }
    decide {
        unless(!exists) { "Harbor Term is already defined" }
        then(
            ProductDefined(HarborTerm, "Harbor Term Life", HarborTerms, HarborFaces),
            harborRateCard(version = 1),
            RiderCatalogued(HarborTerm, AccidentalDeath, riderExtra(AccidentalDeath)),
            RiderCatalogued(HarborTerm, WaiverOfPremium, riderExtra(WaiverOfPremium)),
            RiderCatalogued(HarborTerm, ChildrensTerm, riderExtra(ChildrensTerm)),
            CampaignOpened(Spring, HarborTerm, capacity, discount = 0.05),
        )
    }
}

fun startJourney(customer: CustomerId, quote: QuoteId) = decision {
    val already by requiring { quoteExists(quote) }
    val open by requiring { openQuoteFor(customer) }
    decide {
        unless(!already) { "Quote $quote is already started" }
        open?.let { then(QuoteAbandoned(it, customer)) }
        then(JourneyStarted(quote, customer))
    }
}
