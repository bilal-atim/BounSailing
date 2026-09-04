package com.bilal.marmarisnav.library

/** What a hit points at: a curated topic, or one of the original documents. */
sealed interface SearchTarget {
    data class TopicRef(val topic: Topic) : SearchTarget

    data class SourceRef(val doc: SourceDoc) : SearchTarget
}

/** One search hit: what matched, why it matched, and a snippet to show under it. */
data class SearchHit(
    val target: SearchTarget,
    val title: String,
    val score: Int,
    val snippet: String,
    /** The matched keyword, when the hit came from the keyword list rather than the text. */
    val matchedKeyword: String?,
) {
    val key: String get() = when (target) {
        is SearchTarget.TopicRef -> "t:${target.topic.id}"
        is SearchTarget.SourceRef -> "s:${target.doc.id}"
    }
}

/**
 * Ranked keyword search across the training topics and the original documents.
 *
 * Every query term must appear somewhere in the entry (AND), so typing two
 * words narrows rather than widens. Where a term matches decides the score:
 * the title outranks the declared keywords, which outrank the summary, which
 * outranks the body.
 *
 * Source documents are searched too, but they are scored on a lower scale than
 * the topics. A topic is written to answer a question; a source is the raw
 * lecture it was drawn from, so when both match the topic should come first.
 */
class LibrarySearch(private val library: Library) {

    private data class Indexed(
        val topic: Topic?,
        val doc: SourceDoc?,
        val title: String,
        val keywords: List<String>,
        val summary: String,
        val haystack: String,
        /** Points scored by a title hit; halved for sources so topics lead. */
        val titleWeight: Int,
        val bodyWeight: Int,
    )

    private val index: List<Indexed> =
        library.topics.map { t ->
            Indexed(
                topic = t,
                doc = null,
                title = t.title.foldCaseTr().deaccent(),
                keywords = t.keywords.map { it.foldCaseTr().deaccent() },
                summary = t.summary.foldCaseTr().deaccent(),
                haystack = t.haystack.deaccent(),
                titleWeight = 100,
                bodyWeight = 10,
            )
        } + library.sources.map { d ->
            Indexed(
                topic = null,
                doc = d,
                title = d.title.foldCaseTr().deaccent(),
                keywords = emptyList(),
                summary = "",
                haystack = d.haystack.deaccent(),
                titleWeight = 55,
                bodyWeight = 4,
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
            // Every term has to land somewhere in this entry, or it is not a hit.
            if (terms.any { !entry.haystack.contains(it) }) continue

            var score = 0
            var matchedKeyword: String? = null
            for (term in terms) {
                when {
                    entry.title.contains(term) -> score += entry.titleWeight
                    else -> {
                        val kw = entry.keywords.indexOfFirst { it.contains(term) }
                        if (kw >= 0) {
                            score += 50
                            if (matchedKeyword == null) {
                                matchedKeyword = entry.topic?.keywords?.get(kw)
                            }
                        } else if (entry.summary.isNotEmpty() && entry.summary.contains(term)) {
                            score += 25
                        } else {
                            score += entry.bodyWeight
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

            val term = terms.first()
            hits += if (entry.topic != null) {
                SearchHit(
                    target = SearchTarget.TopicRef(entry.topic),
                    title = entry.topic.title,
                    score = score,
                    snippet = snippetFor(entry.topic.body, term, entry.topic.summary),
                    matchedKeyword = matchedKeyword,
                )
            } else {
                val doc = entry.doc!!
                SearchHit(
                    target = SearchTarget.SourceRef(doc),
                    title = doc.title,
                    score = score,
                    snippet = snippetFor(doc.body, term, ""),
                    matchedKeyword = null,
                )
            }
        }

        return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title })
    }

    /**
     * A line of context around the first match, so the result list shows why the
     * entry came back rather than just repeating its summary.
     */
    private fun snippetFor(body: String, term: String, fallback: String): String {
        val plain = body
            .lineSequence()
            .filterNot { it.startsWith("#") || it.startsWith("|") || it.startsWith("---") }
            .joinToString(" ")
            .replace(LINK) { m -> m.groupValues[2].ifEmpty { library.labelFor(m.target()) } }
            .replace(Regex("[*`>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val at = plain.foldCaseTr().deaccent().indexOf(term)
        if (at < 0) return fallback
        val start = (at - 60).coerceAtLeast(0).let { s ->
            if (s == 0) 0 else plain.indexOf(' ', s).takeIf { it in 0..at } ?: s
        }
        val end = (at + term.length + 100).coerceAtMost(plain.length)
        return buildString {
            if (start > 0) append("…")
            append(plain.substring(start, end).trim())
            if (end < plain.length) append("…")
        }
    }
}
