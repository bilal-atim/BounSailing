package com.bilal.marmarisnav.library

/** One search hit: the topic, why it matched, and a snippet to show under it. */
data class SearchHit(
    val topic: Topic,
    val score: Int,
    val snippet: String,
    /** The matched keyword, when the hit came from the keyword list rather than the text. */
    val matchedKeyword: String?,
)

/**
 * Ranked keyword search across the training topics.
 *
 * Every query term must appear somewhere in the topic (AND), so typing two
 * words narrows rather than widens. Where a term matches decides the score:
 * the title outranks the declared keywords, which outrank the summary, which
 * outranks the body.
 */
class LibrarySearch(private val library: Library) {

    private data class Indexed(
        val topic: Topic,
        val title: String,
        val keywords: List<String>,
        val summary: String,
        val haystack: String,
    )

    private val index: List<Indexed> = library.topics.map { t ->
        Indexed(
            topic = t,
            title = t.title.foldCaseTr().deaccent(),
            keywords = t.keywords.map { it.foldCaseTr().deaccent() },
            summary = t.summary.foldCaseTr().deaccent(),
            haystack = t.haystack.deaccent(),
        )
    }

    fun search(query: String): List<SearchHit> {
        val terms = query.foldCaseTr().deaccent()
            .split(' ', '\t', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()

        val hits = mutableListOf<SearchHit>()
        for (entry in index) {
            // Every term has to land somewhere in this topic, or it is not a hit.
            if (terms.any { !entry.haystack.contains(it) }) continue

            var score = 0
            var matchedKeyword: String? = null
            for (term in terms) {
                when {
                    entry.title.contains(term) -> score += 100
                    else -> {
                        val kw = entry.keywords.indexOfFirst { it.contains(term) }
                        if (kw >= 0) {
                            score += 50
                            if (matchedKeyword == null) matchedKeyword = entry.topic.keywords[kw]
                        } else if (entry.summary.contains(term)) {
                            score += 25
                        } else {
                            score += 10
                        }
                    }
                }
                // A whole-word hit beats an incidental substring ("trim" in "trimci").
                if (Regex("(^|[^\\p{L}])" + Regex.escape(term) + "([^\\p{L}]|$)")
                        .containsMatchIn(entry.haystack)
                ) {
                    score += 15
                }
            }

            hits += SearchHit(
                topic = entry.topic,
                score = score,
                snippet = snippetFor(entry.topic, terms.first()),
                matchedKeyword = matchedKeyword,
            )
        }

        return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.topic.title })
    }

    /**
     * A line of context around the first match, so the result list shows why the
     * topic came back rather than just repeating its summary.
     */
    private fun snippetFor(topic: Topic, term: String): String {
        val plain = topic.body
            .lineSequence()
            .filterNot { it.startsWith("#") || it.startsWith("|") || it.startsWith("---") }
            .joinToString(" ")
            .replace(LINK) { m -> m.groupValues[2].ifEmpty { m.target().removePrefix("src:") } }
            .replace(Regex("[*`>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val at = plain.foldCaseTr().deaccent().indexOf(term)
        if (at < 0) return topic.summary

        val start = (at - 60).coerceAtLeast(0).let { s ->
            if (s == 0) 0 else plain.indexOf(' ', s).takeIf { it in 0..(at) } ?: s
        }
        val end = (at + term.length + 100).coerceAtMost(plain.length)
        return buildString {
            if (start > 0) append("…")
            append(plain.substring(start, end).trim())
            if (end < plain.length) append("…")
        }
    }
}
