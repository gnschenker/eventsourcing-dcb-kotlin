package dcb.postgres

import dcb.CheckpointStore
import dcb.Position
import java.sql.Connection

class PostgresCheckpointStore(
    private val connect: () -> Connection,
) : CheckpointStore {

    fun ensureSchema() {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS checkpoints (
                      name text PRIMARY KEY,
                      position bigint NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    override fun load(name: String): Position? {
        connect().use { connection ->
            connection.prepareStatement("SELECT position FROM checkpoints WHERE name = ?").use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { rows ->
                    return if (rows.next()) Position(rows.getLong(1)) else null
                }
            }
        }
    }

    override fun save(name: String, position: Position) {
        connect().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO checkpoints (name, position) VALUES (?, ?)
                ON CONFLICT (name) DO UPDATE SET position = EXCLUDED.position
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, name)
                statement.setLong(2, position.value)
                statement.executeUpdate()
            }
        }
    }
}
