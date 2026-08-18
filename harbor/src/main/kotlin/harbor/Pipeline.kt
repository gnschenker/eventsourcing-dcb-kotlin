package harbor

import dcb.question
import kotlinx.serialization.Serializable

@Serializable
data class PipelineQuote(
    val quote: QuoteId,
    val customer: CustomerId,
    val step: String,
    val monthly: Int? = null,
    val promo: Boolean = false,
)

@Serializable
data class SalesPipeline(val open: List<PipelineQuote> = emptyList()) {
    operator fun get(id: QuoteId): PipelineQuote? = open.find { it.quote == id }

    fun upsert(row: PipelineQuote) = copy(open = open.filterNot { it.quote == row.quote } + row)

    fun update(id: QuoteId, change: PipelineQuote.() -> PipelineQuote): SalesPipeline {
        val current = this[id] ?: return this
        return upsert(current.change())
    }

    fun drop(id: QuoteId) = copy(open = open.filterNot { it.quote == id })
}

@Serializable
data class IssuedPolicy(
    val policy: PolicyId,
    val quote: QuoteId,
    val customer: CustomerId,
    val monthly: Int,
    val campaign: CampaignId? = null,
)

@Serializable
data class PolicyBook(val policies: List<IssuedPolicy> = emptyList()) {
    operator fun get(id: PolicyId): IssuedPolicy? = policies.find { it.policy == id }

    val promoSeatsTaken: Int get() = policies.count { it.campaign != null }
}

fun salesPipeline() = question(initial = SalesPipeline(), about = emptySet()) {
    on<JourneyStarted> { upsert(PipelineQuote(it.quote, it.customer, step = "offer")) }
    on<QuoteAbandoned> { drop(it.quote) }
    on<PackagePicked> { update(it.quote) { copy(step = "coverage") } }
    on<CoverageChosen> { update(it.quote) { copy(step = "riders") } }
    on<ApplicantDescribed> { update(it.quote) { copy(step = "review") } }
    on<QuotePriced> { update(it.quote) { copy(monthly = it.monthly, step = "review") } }
    on<PromoApplied> { update(it.quote) { copy(promo = true) } }
    on<BeneficiaryNamed> { update(it.quote) { copy(step = "confirm") } }
    on<PolicyIssued> { drop(it.quote) }
}

fun policyBook() = question(initial = PolicyBook(), about = emptySet()) {
    on<PolicyIssued> {
        copy(
            policies = policies + IssuedPolicy(it.policy, it.quote, it.customer, it.monthly, it.campaign),
        )
    }
}
