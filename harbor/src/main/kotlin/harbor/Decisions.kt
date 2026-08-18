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

fun pickPackage(quote: QuoteId, kind: PackageKind) = decision {
    val journey by requiring { journeyOf(quote) }
    decide {
        unless(journey.isOpen) { "This quote is no longer open" }
        then(PackagePicked(quote, kind))
        when (kind) {
            PackageKind.Essential -> then(CoverageChosen(quote, 10, 250_000))
            PackageKind.Family -> then(
                CoverageChosen(quote, 20, 500_000),
                RiderAdded(quote, AccidentalDeath),
                RiderAdded(quote, ChildrensTerm),
            )
            PackageKind.LongView -> then(
                CoverageChosen(quote, 30, 500_000),
                RiderAdded(quote, AccidentalDeath),
                RiderAdded(quote, WaiverOfPremium),
            )
            PackageKind.Custom -> Unit
        }
    }
}

fun chooseCoverage(quote: QuoteId, termYears: Int, face: Int) = decision {
    val journey by requiring { journeyOf(quote) }
    val product by requiring { productDefinition(HarborTerm) }
    val riders by requiring { ridersOn(quote) }
    val applicant by requiring { applicantOf(quote) }
    val rates by considering { currentRateCard(HarborTerm) }
    decide {
        unless(journey.isOpen) { "This quote is no longer open" }
        unless(termYears in product.terms) { "Harbor Term is not offered for $termYears years" }
        unless(face in product.faces) { "Harbor Term is not offered at that amount" }
        val blocked = riders.firstOrNull { !riderAllowed(it, face, applicant) }
        unless(blocked == null) {
            riderRefusal(blocked!!, face, applicant)
                ?: "A selected rider is not available with this coverage"
        }
        unless(applicant?.tobacco != true || face < 1_000_000) {
            "Tobacco users can choose up to \$500,000 in this sample"
        }
        then(CoverageChosen(quote, termYears, face))
        rates
    }
}

fun addRider(quote: QuoteId, rider: RiderCode) = decision {
    val journey by requiring { journeyOf(quote) }
    val selected by requiring { riderSelected(quote, rider) }
    val coverage by requiring { coverageOf(quote) }
    val applicant by requiring { applicantOf(quote) }
    decide {
        unless(journey.isOpen) { "This quote is no longer open" }
        unless(!selected) { "${rider.displayName()} is already on this quote" }
        val refusal = riderRefusal(rider, coverage?.face, applicant)
        unless(refusal == null) { refusal!! }
        then(RiderAdded(quote, rider))
    }
}

fun removeRider(quote: QuoteId, rider: RiderCode) = decision {
    val journey by requiring { journeyOf(quote) }
    val selected by requiring { riderSelected(quote, rider) }
    decide {
        unless(journey.isOpen) { "This quote is no longer open" }
        unless(selected) { "${rider.displayName()} is not on this quote" }
        then(RiderRemoved(quote, rider))
    }
}

fun describeApplicant(
    quote: QuoteId,
    customer: CustomerId,
    age: Int,
    tobacco: Boolean,
    sex: Sex,
) = decision {
    val journey by requiring { journeyOf(quote) }
    val riders by requiring { ridersOn(quote) }
    decide {
        unless(journey.isOpen) { "This quote is no longer open" }
        unless(journey.customer == customer) { "This quote is not yours" }
        unless(age in 18..60) { "Coverage is offered from age 18 to 60" }
        unless(WaiverOfPremium !in riders || age <= 55) {
            "Waiver of premium is available up to age 55"
        }
        then(ApplicantDescribed(quote, customer, age, tobacco, sex))
    }
}

fun priceQuote(quote: QuoteId) = decision {
    val journey by requiring { journeyOf(quote) }
    val coverage by requiring { coverageOf(quote) }
    val applicant by requiring { applicantOf(quote) }
    val riders by requiring { ridersOn(quote) }
    val extras by requiring { riderExtras(HarborTerm) }
    val rates by considering { currentRateCard(HarborTerm) }
    decide {
        unless(journey.isOpen) { "This quote is no longer open" }
        unless(coverage != null) { "Choose a term and amount first" }
        unless(applicant != null) { "Tell us about the person being insured" }
        unless(rates != null) { "Harbor Term has no published rate card" }
        val chosen = coverage
        val person = applicant
        val card = rates
        if (chosen == null || person == null || card == null) return@decide
        val base = card.monthly(chosen.termYears, chosen.face, person.tobacco)
        unless(base != null) { "No premium is published for that combination" }
        val extra = riders.sumOf { extras[it] ?: 0 }
        then(QuotePriced(quote, HarborTerm, (base ?: 0) + extra, card.version))
    }
}

fun nameBeneficiary(quote: QuoteId, name: String) = decision {
    val journey by requiring { journeyOf(quote) }
    decide {
        unless(journey.isOpen) { "This quote is no longer open" }
        unless(name.isNotBlank()) { "Name a beneficiary" }
        then(BeneficiaryNamed(quote, name.trim()))
    }
}

fun applyPromo(quote: QuoteId, campaign: CampaignId = Spring) = decision {
    val journey by requiring { journeyOf(quote) }
    val campaignDef by requiring { campaignOf(campaign) }
    decide {
        unless(journey.isOpen) { "This quote is no longer open" }
        unless(campaignDef != null) { "That promotion is not open" }
        then(PromoApplied(quote, campaign))
    }
}

fun buyPolicy(
    quote: QuoteId,
    customer: CustomerId,
    policy: PolicyId,
    campaign: CampaignId? = Spring,
) = decision {
    val journey by requiring { journeyOf(quote) }
    val coverage by requiring { coverageOf(quote) }
    val applicant by requiring { applicantOf(quote) }
    val priced by requiring { latestPrice(quote) }
    val beneficiary by requiring { beneficiaryOf(quote) }
    val inForce by requiring { inForceHarborPolicy(customer) }
    val policyTaken by requiring { policyExists(policy) }
    val promo by requiring { promoOn(quote) }
    val seats by requiring { campaignSeatsTaken(campaign ?: Spring) }
    val campaignDef by requiring { campaignOf(campaign ?: Spring) }
    decide {
        unless(journey.isOpen && journey.customer == customer) { "This quote is not yours to buy" }
        unless(coverage != null) { "Choose a term and amount first" }
        unless(applicant != null) { "Tell us about the person being insured" }
        unless(priced != null) { "Review the premium before buying" }
        unless(beneficiary != null) { "Name a beneficiary" }
        unless(inForce == null) { "You already have a Harbor Term policy" }
        unless(!policyTaken) { "That policy number is already used" }
        val openCampaign = campaignDef
        unless(!promo || openCampaign == null || seats < openCampaign.capacity) {
            "Harbor Spring is fully subscribed"
        }
        then(
            PolicyIssued(
                policy,
                quote,
                customer,
                priced!!.monthly,
                campaign.takeIf { promo },
            ),
        )
    }
}
