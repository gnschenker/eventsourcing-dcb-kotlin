package dcb.testing

import dcb.EventStore
import dcb.Fact
import dcb.InMemoryEventStore
import dcb.Outcome
import dcb.PreparedDecision
import dcb.handle
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

fun given(vararg facts: Fact): Scenario = Scenario(facts.toList())

class Scenario internal constructor(
    private val history: List<Fact>,
    private val storeFactory: () -> EventStore = { InMemoryEventStore() },
) {
    fun against(store: EventStore): Scenario =
        Scenario(history) { store }

    fun whenever(decision: () -> PreparedDecision): Expectation {
        val store = storeFactory()
        if (history.isNotEmpty()) store.append(history)
        return Expectation(store.handle(decision()), store)
    }
}

class Expectation internal constructor(
    val outcome: Outcome,
    val store: EventStore,
) {
    fun expect(vararg facts: Fact): Expectation {
        val accepted = assertIs<Outcome.Accepted>(outcome, "Expected accepted facts, but the decision was rejected: $outcome")
        assertEquals(facts.toList(), accepted.facts)
        return this
    }

    fun expectRejection(reason: String): Expectation {
        val rejected = assertIs<Outcome.Rejected>(outcome, "Expected a rejection, but the decision was accepted: $outcome")
        assertEquals(reason, rejected.reason)
        return this
    }

    fun expectRejectionContaining(part: String): Expectation {
        val rejected = assertIs<Outcome.Rejected>(outcome, "Expected a rejection, but the decision was accepted: $outcome")
        assertTrue(part in rejected.reason, "Expected rejection to contain '$part', was '${rejected.reason}'")
        return this
    }
}
