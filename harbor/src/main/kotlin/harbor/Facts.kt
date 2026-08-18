package harbor

import dcb.Fact
import dcb.subjects
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ProductDefined(
    val product: ProductId,
    val name: String,
    val terms: List<Int>,
    val faces: List<Int>,
) : Fact {
    @Transient override val about = subjects(product)
}

@Serializable
data class RateRow(
    val termYears: Int,
    val face: Int,
    val tobacco: Boolean,
    val monthly: Int,
)

@Serializable
data class RateCardPublished(
    val product: ProductId,
    val version: Int,
    val rows: List<RateRow>,
) : Fact {
    @Transient override val about = subjects(product)

    fun monthly(termYears: Int, face: Int, tobacco: Boolean): Int? =
        rows.firstOrNull { it.termYears == termYears && it.face == face && it.tobacco == tobacco }?.monthly
}

@Serializable
data class RiderCatalogued(
    val product: ProductId,
    val rider: RiderCode,
    val extraMonthly: Int,
) : Fact {
    @Transient override val about = subjects(product, rider)
}

@Serializable
data class CampaignOpened(
    val campaign: CampaignId,
    val product: ProductId,
    val capacity: Int,
    val discount: Double,
) : Fact {
    @Transient override val about = subjects(campaign, product)
}

@Serializable
data class JourneyStarted(
    val quote: QuoteId,
    val customer: CustomerId,
) : Fact {
    @Transient override val about = subjects(quote, customer)
}

@Serializable
data class QuoteAbandoned(
    val quote: QuoteId,
    val customer: CustomerId,
) : Fact {
    @Transient override val about = subjects(quote, customer)
}

@Serializable
data class PackagePicked(
    val quote: QuoteId,
    val kind: PackageKind,
) : Fact {
    @Transient override val about = subjects(quote)
}

@Serializable
data class CoverageChosen(
    val quote: QuoteId,
    val termYears: Int,
    val face: Int,
) : Fact {
    @Transient override val about = subjects(quote)
}

@Serializable
data class RiderAdded(
    val quote: QuoteId,
    val rider: RiderCode,
) : Fact {
    @Transient override val about = subjects(quote, rider)
}

@Serializable
data class RiderRemoved(
    val quote: QuoteId,
    val rider: RiderCode,
) : Fact {
    @Transient override val about = subjects(quote, rider)
}

@Serializable
data class ApplicantDescribed(
    val quote: QuoteId,
    val customer: CustomerId,
    val age: Int,
    val tobacco: Boolean,
    val sex: Sex,
) : Fact {
    @Transient override val about = subjects(quote, customer)
}

@Serializable
data class QuotePriced(
    val quote: QuoteId,
    val product: ProductId,
    val monthly: Int,
    val cardVersion: Int,
) : Fact {
    @Transient override val about = subjects(quote, product)
}

@Serializable
data class BeneficiaryNamed(
    val quote: QuoteId,
    val name: String,
    val share: Int = 100,
) : Fact {
    @Transient override val about = subjects(quote)
}

@Serializable
data class PromoApplied(
    val quote: QuoteId,
    val campaign: CampaignId,
) : Fact {
    @Transient override val about = subjects(quote, campaign)
}

@Serializable
data class PolicyIssued(
    val policy: PolicyId,
    val quote: QuoteId,
    val customer: CustomerId,
    val monthly: Int,
    val campaign: CampaignId? = null,
) : Fact {
    @Transient override val about = subjects(listOfNotNull(policy, quote, customer, campaign))
}
