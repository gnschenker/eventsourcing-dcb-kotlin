package harbor

import dcb.EventStore
import dcb.ask
import dcb.project

data class OfferScreen(
    val openQuote: QuoteId?,
    val terms: Set<Int>,
    val faces: Set<Int>,
)

data class ReviewScreen(
    val coverage: CoverageChosen?,
    val riders: Set<RiderCode>,
    val applicant: ApplicantDescribed?,
    val price: QuotePriced?,
    val promo: Boolean,
)

fun EventStore.offerFor(customer: CustomerId): OfferScreen = project {
    val open by lookingAt { openQuoteFor(customer) }
    val product by lookingAt { productDefinition(HarborTerm) }
    answer {
        OfferScreen(openQuote = open, terms = product.terms, faces = product.faces)
    }
}.state

fun EventStore.reviewOf(quote: QuoteId): ReviewScreen = project {
    val coverage by lookingAt { coverageOf(quote) }
    val riders by lookingAt { ridersOn(quote) }
    val applicant by lookingAt { applicantOf(quote) }
    val price by lookingAt { latestPrice(quote) }
    val promo by lookingAt { promoOn(quote) }
    answer { ReviewScreen(coverage, riders, applicant, price, promo) }
}.state

fun EventStore.premiumOn(quote: QuoteId): QuotePriced? = ask(latestPrice(quote))
