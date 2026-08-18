package harbor

import dcb.testing.given
import kotlin.test.Test

class BuyPolicyTest {
    @Test
    fun `review records the premium the customer was shown`() {
        val monthly = harborRateCard().monthly(20, 500_000, tobacco = false)!! + 8
        given(
            *openQuote(),
            CoverageChosen(q1, 20, 500_000),
            ApplicantDescribed(q1, ada, 42, tobacco = false, sex = Sex.Female),
            RiderAdded(q1, AccidentalDeath),
        ).whenever {
            priceQuote(q1)
        }.expect(QuotePriced(q1, HarborTerm, monthly, cardVersion = 1))
    }

    @Test
    fun `a later rate card does not change a premium already recorded`() {
        val first = harborRateCard(version = 1)
        val priced = first.monthly(20, 250_000, false)!!
        given(
            ProductDefined(HarborTerm, "Harbor Term Life", HarborTerms, HarborFaces),
            first,
            JourneyStarted(q1, ada),
            CoverageChosen(q1, 20, 250_000),
            ApplicantDescribed(q1, ada, 42, false, Sex.Female),
            QuotePriced(q1, HarborTerm, priced, 1),
            harborRateCard(version = 2),
        ).whenever {
            startJourney(ada, q2)
        }.expect(QuoteAbandoned(q1, ada), JourneyStarted(q2, ada))
    }

    @Test
    fun `a complete quote can be bought`() {
        val monthly = harborRateCard().monthly(20, 500_000, false)!!
        given(*pricedQuote()).whenever {
            buyPolicy(q1, ada, p1)
        }.expect(PolicyIssued(p1, q1, ada, monthly))
    }

    @Test
    fun `buy is refused when the beneficiary is missing`() {
        given(
            *openQuote(),
            CoverageChosen(q1, 20, 500_000),
            ApplicantDescribed(q1, ada, 42, false, Sex.Female),
            QuotePriced(q1, HarborTerm, 28, 1),
        ).whenever {
            buyPolicy(q1, ada, p1)
        }.expectRejection("Name a beneficiary")
    }

    @Test
    fun `a customer cannot hold two Harbor policies`() {
        given(
            *pricedQuote(quote = q2),
            PolicyIssued(p1, q1, ada, monthly = 28),
        ).whenever {
            buyPolicy(q2, ada, p2)
        }.expectRejection("You already have a Harbor Term policy")
    }

    @Test
    fun `the last Harbor Spring seat can be taken`() {
        val monthly = harborRateCard().monthly(20, 500_000, false)!!
        given(*pricedQuote(promo = true, capacity = 1)).whenever {
            buyPolicy(q1, ada, p1, Spring)
        }.expect(PolicyIssued(p1, q1, ada, monthly, Spring))
    }

    @Test
    fun `two buyers of the last seat, one binds, the other is told the promo is full`() {
        given(
            *pricedQuote(quote = q2, customer = grace, promo = true, capacity = 1),
            PolicyIssued(p1, q1, ada, monthly = 28, campaign = Spring),
        ).whenever {
            buyPolicy(q2, grace, p2, Spring)
        }.expectRejection("Harbor Spring is fully subscribed")
    }

    @Test
    fun `a customer can name a beneficiary`() {
        given(*openQuote()).whenever {
            nameBeneficiary(q1, "Sam Lee")
        }.expect(BeneficiaryNamed(q1, "Sam Lee"))
    }

    @Test
    fun `a customer can opt into Harbor Spring`() {
        given(*openQuote()).whenever {
            applyPromo(q1)
        }.expect(PromoApplied(q1, Spring))
    }
}
