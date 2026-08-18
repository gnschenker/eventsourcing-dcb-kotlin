package harbor

import dcb.About
import dcb.Subject
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CustomerId(val value: String) : About {
    override fun asSubject(): Subject = Subject("customer:$value")
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class QuoteId(val value: String) : About {
    override fun asSubject(): Subject = Subject("quote:$value")
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class ProductId(val value: String) : About {
    override fun asSubject(): Subject = Subject("product:$value")
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class CampaignId(val value: String) : About {
    override fun asSubject(): Subject = Subject("campaign:$value")
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class PolicyId(val value: String) : About {
    override fun asSubject(): Subject = Subject("policy:$value")
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class RiderCode(val value: String) : About {
    override fun asSubject(): Subject = Subject("rider:$value")
    override fun toString(): String = value
}

val HarborTerm = ProductId("harbor-term")
val Spring = CampaignId("spring")
val AccidentalDeath = RiderCode("adb")
val WaiverOfPremium = RiderCode("waiver")
val ChildrensTerm = RiderCode("child")

fun RiderCode.displayName(): String = when (this) {
    AccidentalDeath -> "Accidental death"
    WaiverOfPremium -> "Waiver of premium"
    ChildrensTerm -> "Children's term"
    else -> value
}

enum class PackageKind { Essential, Family, LongView, Custom }

enum class Sex { Female, Male }
