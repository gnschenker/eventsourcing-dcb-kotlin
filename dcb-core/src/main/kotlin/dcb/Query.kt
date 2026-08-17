package dcb

/**
 * One clause of a DCB query.
 *
 * An event matches when:
 * - its type is in [types], or [types] is empty, and
 * - it carries every subject in [tags] ([tags] empty means no tag filter).
 */
data class QueryItem(
    val types: Set<String> = emptySet(),
    val tags: Set<Subject> = emptySet(),
)

/**
 * A set of [QueryItem]s combined with OR.
 * An empty item list matches every event (`Query.all()`).
 */
data class Query(val items: List<QueryItem>) {
    fun matches(recorded: RecordedFact): Boolean {
        if (items.isEmpty()) return true
        return items.any { it.matches(recorded) }
    }

    companion object {
        fun all(): Query = Query(emptyList())

        fun of(vararg items: QueryItem): Query = Query(items.toList())

        fun of(items: List<QueryItem>): Query = Query(items)
    }
}

fun QueryItem.matches(recorded: RecordedFact): Boolean {
    val typeOk = types.isEmpty() || recorded.type in types
    val tagsOk = tags.all { it in recorded.tags }
    return typeOk && tagsOk
}
