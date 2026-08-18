package harbor

import dcb.postgres.jsonFactCodec

fun harborFactCodec() = jsonFactCodec {
    register(ProductDefined.serializer())
    register(RateCardPublished.serializer())
    register(RiderCatalogued.serializer())
    register(CampaignOpened.serializer())
    register(JourneyStarted.serializer())
    register(QuoteAbandoned.serializer())
    register(PackagePicked.serializer())
    register(CoverageChosen.serializer())
    register(RiderAdded.serializer())
    register(RiderRemoved.serializer())
    register(ApplicantDescribed.serializer())
    register(QuotePriced.serializer())
    register(BeneficiaryNamed.serializer())
    register(PromoApplied.serializer())
    register(PolicyIssued.serializer())
}
