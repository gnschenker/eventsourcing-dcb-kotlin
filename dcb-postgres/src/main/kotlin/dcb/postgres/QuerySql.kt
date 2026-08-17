package dcb.postgres

import dcb.Position
import dcb.Query

internal data class BoundSql(val sql: String, val bindings: List<Any>)

internal fun Query.selectSql(
    after: Position?,
    columns: String,
    limitOne: Boolean = false,
): BoundSql {
    val where = mutableListOf<String>()
    val bindings = mutableListOf<Any>()
    if (after != null) {
        where += "e.position > ?"
        bindings += after.value
    }
    if (items.isNotEmpty()) {
        val itemClauses = items.map { item -> itemClause(item.types, item.tags.map { it.value }, bindings) }
        where += "(" + itemClauses.joinToString(" OR ") + ")"
    }
    val sql = buildString {
        append(columns)
        append(" FROM events e")
        if (where.isNotEmpty()) {
            append(" WHERE ")
            append(where.joinToString(" AND "))
        }
        if (!limitOne) append(" ORDER BY e.position")
        if (limitOne) append(" LIMIT 1")
    }
    return BoundSql(sql, bindings)
}

private fun itemClause(
    types: Set<String>,
    tags: List<String>,
    bindings: MutableList<Any>,
): String {
    val parts = mutableListOf<String>()
    if (types.isNotEmpty()) {
        parts += "e.type = ANY (?)"
        bindings.add(types.toTypedArray())
    }
    if (tags.size == 1) {
        parts += "EXISTS (SELECT 1 FROM event_tags t WHERE t.position = e.position AND t.tag = ?)"
        bindings += tags.single()
    } else if (tags.size > 1) {
        parts += """
            EXISTS (
              SELECT 1 FROM event_tags t
              WHERE t.position = e.position AND t.tag = ANY (?)
              GROUP BY t.position
              HAVING COUNT(DISTINCT t.tag) = ?
            )
        """.trimIndent()
        bindings.add(tags.toTypedArray())
        bindings += tags.size.toLong()
    }
    return if (parts.isEmpty()) "TRUE" else parts.joinToString(" AND ")
}
