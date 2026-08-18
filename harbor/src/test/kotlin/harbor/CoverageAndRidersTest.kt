package harbor

import dcb.testing.given
import kotlin.test.Test

class CoverageAndRidersTest {
    @Test
    fun `a customer can choose 20 years and $500,000`() {
        given(*openQuote()).whenever {
            chooseCoverage(q1, 20, 500_000)
        }.expect(CoverageChosen(q1, 20, 500_000))
    }

    @Test
    fun `30 years is offered, 15 years is not`() {
        given(*openQuote()).whenever {
            chooseCoverage(q1, 15, 250_000)
        }.expectRejection("Harbor Term is not offered for 15 years")
    }

    @Test
    fun `a tobacco user cannot choose $1,000,000`() {
        given(
            *openQuote(),
            ApplicantDescribed(q1, ada, age = 40, tobacco = true, sex = Sex.Female),
        ).whenever {
            chooseCoverage(q1, 20, 1_000_000)
        }.expectRejection("Tobacco users can choose up to \$500,000 in this sample")
    }

    @Test
    fun `children's term cannot stay selected below $250,000`() {
        given(
            *openQuote(),
            CoverageChosen(q1, 20, 500_000),
            RiderAdded(q1, ChildrensTerm),
        ).whenever {
            chooseCoverage(q1, 20, 100_000)
        }.expectRejection("Children's term needs at least \$250,000 of coverage")
    }

    @Test
    fun `the family package records coverage and riders in one decision`() {
        given(*openQuote()).whenever {
            pickPackage(q1, PackageKind.Family)
        }.expect(
            PackagePicked(q1, PackageKind.Family),
            CoverageChosen(q1, 20, 500_000),
            RiderAdded(q1, AccidentalDeath),
            RiderAdded(q1, ChildrensTerm),
        )
    }

    @Test
    fun `accidental death can be added and removed`() {
        given(*openQuote(), CoverageChosen(q1, 20, 250_000)).whenever {
            addRider(q1, AccidentalDeath)
        }.expect(RiderAdded(q1, AccidentalDeath))

        given(
            *openQuote(),
            CoverageChosen(q1, 20, 250_000),
            RiderAdded(q1, AccidentalDeath),
        ).whenever {
            removeRider(q1, AccidentalDeath)
        }.expect(RiderRemoved(q1, AccidentalDeath))
    }

    @Test
    fun `a rider cannot be added twice`() {
        given(*openQuote(), RiderAdded(q1, AccidentalDeath)).whenever {
            addRider(q1, AccidentalDeath)
        }.expectRejection("Accidental death is already on this quote")
    }

    @Test
    fun `waiver of premium is refused at age 57`() {
        given(
            *openQuote(),
            CoverageChosen(q1, 20, 500_000),
            ApplicantDescribed(q1, ada, age = 57, tobacco = false, sex = Sex.Male),
        ).whenever {
            addRider(q1, WaiverOfPremium)
        }.expectRejection("Waiver of premium is available up to age 55")
    }

    @Test
    fun `describing a 57-year-old is refused while waiver is selected`() {
        given(
            *openQuote(),
            RiderAdded(q1, WaiverOfPremium),
        ).whenever {
            describeApplicant(q1, ada, age = 57, tobacco = false, sex = Sex.Female)
        }.expectRejection("Waiver of premium is available up to age 55")
    }

    @Test
    fun `a customer can describe themselves`() {
        given(*openQuote()).whenever {
            describeApplicant(q1, ada, age = 42, tobacco = false, sex = Sex.Female)
        }.expect(ApplicantDescribed(q1, ada, 42, tobacco = false, sex = Sex.Female))
    }
}
