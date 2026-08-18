package dcb.postgres

import java.sql.Connection

/**
 * Binds the append connection so projection snapshots can be written in the
 * same transaction without opening a second session.
 */
internal object PostgresSession {
    private val bound = ThreadLocal<Connection>()

    fun <T> bind(connection: Connection, block: () -> T): T {
        val previous = bound.get()
        bound.set(connection)
        return try {
            block()
        } finally {
            if (previous == null) bound.remove() else bound.set(previous)
        }
    }

    fun current(): Connection? = bound.get()
}
