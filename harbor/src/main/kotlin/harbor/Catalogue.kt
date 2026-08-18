package harbor

val HarborTerms = listOf(10, 20, 30)
val HarborFaces = listOf(100_000, 250_000, 500_000, 1_000_000)

fun harborRateCard(version: Int = 1) = RateCardPublished(
    product = HarborTerm,
    version = version,
    rows = buildList {
        for (term in HarborTerms) {
            for (face in HarborFaces) {
                add(RateRow(term, face, tobacco = false, monthly = baseMonthly(term, face)))
                add(RateRow(term, face, tobacco = true, monthly = (baseMonthly(term, face) * 18 + 9) / 10))
            }
        }
    },
)

fun riderExtra(rider: RiderCode): Int = when (rider) {
    AccidentalDeath -> 8
    WaiverOfPremium -> 5
    ChildrensTerm -> 6
    else -> 0
}

fun riderAllowed(
    rider: RiderCode,
    face: Int?,
    applicant: ApplicantDescribed?,
): Boolean = when (rider) {
    ChildrensTerm -> face == null || face >= 250_000
    WaiverOfPremium -> applicant == null || applicant.age <= 55
    else -> true
}

fun riderRefusal(rider: RiderCode, face: Int?, applicant: ApplicantDescribed?): String? {
    if (riderAllowed(rider, face, applicant)) return null
    return when (rider) {
        ChildrensTerm -> "Children's term needs at least \$250,000 of coverage"
        WaiverOfPremium -> "Waiver of premium is available up to age 55"
        else -> "${rider.displayName()} is not available"
    }
}

private fun baseMonthly(termYears: Int, face: Int): Int {
    val termLoad = when (termYears) {
        10 -> 0
        20 -> 6
        else -> 12
    }
    val faceLoad = face / 50_000
    return 8 + termLoad + faceLoad
}
