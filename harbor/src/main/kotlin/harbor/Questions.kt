package harbor

import dcb.question

data class Journey(
    val customer: CustomerId? = null,
    val open: Boolean = false,
) {
    val isOpen: Boolean get() = open
}

data class ProductDef(
    val name: String = "",
    val terms: Set<Int> = emptySet(),
    val faces: Set<Int> = emptySet(),
)

fun quoteExists(quote: QuoteId) = question(initial = false, about = quote) {
    on<JourneyStarted> { true }
}

fun openQuoteFor(customer: CustomerId) = question(initial = null as QuoteId?, about = customer) {
    on<JourneyStarted> { it.quote }
    on<QuoteAbandoned> { if (this == it.quote) null else this }
    on<PolicyIssued> { if (this == it.quote) null else this }
}

fun journeyOf(quote: QuoteId) = question(initial = Journey(), about = quote) {
    on<JourneyStarted> { Journey(it.customer, open = true) }
    on<QuoteAbandoned> { copy(open = false) }
    on<PolicyIssued> { copy(open = false) }
}

fun productExists(product: ProductId) = question(initial = false, about = product) {
    on<ProductDefined> { true }
}

fun productDefinition(product: ProductId) = question(initial = ProductDef(), about = product) {
    on<ProductDefined> { ProductDef(it.name, it.terms.toSet(), it.faces.toSet()) }
}

fun currentRateCard(product: ProductId) = question(initial = null as RateCardPublished?, about = product) {
    on<RateCardPublished> { it }
}

fun riderExtras(product: ProductId) = question(initial = emptyMap<RiderCode, Int>(), about = product) {
    on<RiderCatalogued> { this + (it.rider to it.extraMonthly) }
}

fun ridersOn(quote: QuoteId) = question(initial = emptySet<RiderCode>(), about = quote) {
    on<RiderAdded> { this + it.rider }
    on<RiderRemoved> { this - it.rider }
}

fun riderSelected(quote: QuoteId, rider: RiderCode) = question(
    initial = false,
    about = dcb.subjects(quote, rider),
) {
    on<RiderAdded> { true }
    on<RiderRemoved> { false }
}

fun coverageOf(quote: QuoteId) = question(initial = null as CoverageChosen?, about = quote) {
    on<CoverageChosen> { it }
}

fun applicantOf(quote: QuoteId) = question(initial = null as ApplicantDescribed?, about = quote) {
    on<ApplicantDescribed> { it }
}

fun latestPrice(quote: QuoteId) = question(initial = null as QuotePriced?, about = quote) {
    on<QuotePriced> { it }
}

fun beneficiaryOf(quote: QuoteId) = question(initial = null as BeneficiaryNamed?, about = quote) {
    on<BeneficiaryNamed> { it }
}

fun promoOn(quote: QuoteId) = question(initial = false, about = quote) {
    on<PromoApplied> { true }
    on<PromoRemoved> { false }
}

fun policyExists(policy: PolicyId) = question(initial = false, about = policy) {
    on<PolicyIssued> { true }
}

fun inForceHarborPolicy(customer: CustomerId) = question(initial = null as PolicyId?, about = customer) {
    on<PolicyIssued> { it.policy }
}

fun campaignOf(campaign: CampaignId) = question(initial = null as CampaignOpened?, about = campaign) {
    on<CampaignOpened> { it }
}

fun campaignSeatsTaken(campaign: CampaignId) = question(initial = 0, about = campaign) {
    on<PolicyIssued> { this + 1 }
}
