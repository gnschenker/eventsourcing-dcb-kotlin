package harbor

import dcb.Outcome
import dcb.append
import dcb.handle
import dcb.postgres.PostgresProjectionStore
import dcb.projectAsync
import dcb.testing.given
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.parallel.ResourceLock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("postgresIsUp")
@ResourceLock("postgres")
class PostgresHarborTest {
    private val store by lazy { PostgresSupport.store() }
    private val json = Json { encodeDefaults = true }
    private val snapshots by lazy {
        PostgresProjectionStore(
            connect = PostgresSupport::connect,
            encode = { json.encodeToString(PolicyBook.serializer(), it) },
            decode = { json.decodeFromString(PolicyBook.serializer(), it) },
        )
    }

    @BeforeAll
    fun schema() {
        store.ensureSchema()
        snapshots.ensureSchema()
    }

    @BeforeEach
    fun wipe() {
        PostgresSupport.wipe()
        snapshots.delete("policy-book")
    }

    @Test
    fun `a complete quote can be bought against postgres`() {
        val monthly = harborRateCard().monthly(20, 500_000, false)!!
        given(*pricedQuote()).against(store).whenever {
            buyPolicy(q1, ada, p1)
        }.expect(PolicyIssued(p1, q1, ada, monthly))
    }

    @Test
    fun `two buyers of the last seat, one binds, the other is told the promo is full`() {
        given(
            *pricedQuote(quote = q2, customer = grace, promo = true, capacity = 1),
            PolicyIssued(p1, q1, ada, monthly = 28, campaign = Spring),
        ).against(store).whenever {
            buyPolicy(q2, grace, p2, Spring)
        }.expectRejection("Harbor Spring is fully subscribed")
    }

    @Test
    fun `the policy book waits for buy on postgres`() {
        val book = store.projectAsync("policy-book", policyBook(), snapshots)
        store.append(*pricedQuote())
        val outcome = store.handle(buyPolicy(q1, ada, p1))
        val position = (outcome as Outcome.Accepted).position!!
        book.catchUpTo(position)
        assertEquals(ada, book.state[p1]?.customer)
        assertTrue(book.state.promoSeatsTaken == 0)
    }

    companion object {
        @JvmStatic
        fun postgresIsUp(): Boolean = PostgresSupport.canConnect()
    }
}
