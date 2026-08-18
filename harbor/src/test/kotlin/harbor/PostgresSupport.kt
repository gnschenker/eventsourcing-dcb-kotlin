package harbor

import dcb.postgres.PostgresEventStore
import java.sql.DriverManager

internal object PostgresSupport {
    val url: String = System.getenv("DCB_PG_URL") ?: "jdbc:postgresql://localhost:5433/dcb"
    val user: String = System.getenv("DCB_PG_USER") ?: "dcb"
    val password: String = System.getenv("DCB_PG_PASSWORD") ?: "dcb"

    fun canConnect(): Boolean = runCatching {
        DriverManager.getConnection(url, user, password).use { }
    }.isSuccess

    fun connect() = DriverManager.getConnection(url, user, password)

    fun store(): PostgresEventStore {
        val store = PostgresEventStore(connect = ::connect, codec = harborFactCodec())
        store.ensureSchema()
        return store
    }

    fun wipe() {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE events RESTART IDENTITY CASCADE")
                runCatching { statement.execute("TRUNCATE projections") }
            }
        }
    }
}
