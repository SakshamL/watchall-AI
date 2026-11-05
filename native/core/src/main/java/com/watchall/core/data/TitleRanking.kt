package com.watchall.core.data

import java.text.Normalizer
import java.util.Locale

object TitleRanking {
    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    fun rank(items: List<MediaSummary>, query: String): List<MediaSummary> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return items

        fun score(item: MediaSummary): Int {
            val title = normalize(item.displayTitle)
            return when {
                title.isBlank() -> 4
                title == normalizedQuery -> 0
                title.startsWith("$normalizedQuery ") -> 1
                title.startsWith(normalizedQuery) -> 2
                title.contains(normalizedQuery) -> 3
                else -> 4
            }
        }

        return items
            .mapIndexed { index, item -> Triple(item, index, score(item)) }
            .sortedWith(compareBy<Triple<MediaSummary, Int, Int>> { it.third }.thenBy { it.second })
            .map { it.first }
    }
}
