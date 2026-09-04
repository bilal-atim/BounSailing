package com.bilal.marmarisnav.library

import android.content.Context
import org.json.JSONArray

/** A grouping of training topics, as listed in assets/library/categories.json. */
data class Category(
    val id: String,
    val title: String,
    val subtitle: String,
)

/** One training topic: a curated article assembled from the club's source documents. */
data class Topic(
    val id: String,
    val title: String,
    val categoryId: String,
    val order: Int,
    val summary: String,
    val keywords: List<String>,
    /** Ids of source documents this topic was drawn from. */
    val sourceIds: List<String>,
    val body: String,
) {
    /** Everything the search runs over, lower-cased once at load time. */
    val haystack: String = buildString {
        append(title).append('\n')
        append(summary).append('\n')
        append(keywords.joinToString(" ")).append('\n')
        append(body)
    }.foldCaseTr()
}

/** An original club document, cleaned of OCR artefacts at build time. */
data class SourceDoc(
    val id: String,
    val title: String,
    val body: String,
) {
    /** Everything search runs over, lower-cased once at load time. */
    val haystack: String = (title + "\n" + body).foldCaseTr()
}

class Library(
    val categories: List<Category>,
    val topics: List<Topic>,
    val sources: List<SourceDoc>,
) {
    private val topicsById = topics.associateBy { it.id }
    private val sourcesById = sources.associateBy { it.id }

    fun topic(id: String): Topic? = topicsById[id]

    fun source(id: String): SourceDoc? = sourcesById[id]

    fun topicsIn(categoryId: String): List<Topic> =
        topics.filter { it.categoryId == categoryId }.sortedBy { it.order }

    /** Topics that link to [id], so a detail page can show what leads here. */
    fun backlinks(id: String): List<Topic> =
        topics.filter { it.id != id && LINK.findAll(it.body).any { m -> m.target() == id } }
            .sortedBy { it.title }

    /** Resolves a link target to the title it should display. */
    fun labelFor(target: String): String = when {
        target.startsWith("src:") ->
            source(target.removePrefix("src:"))?.title ?: prettyLabel(target)
        else -> topic(target)?.title ?: prettyLabel(target)
    }

    companion object {
        private const val ROOT = "library"

        fun load(context: Context): Library {
            val assets = context.assets

            val categories = JSONArray(assets.readText("$ROOT/categories.json")).let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Category(o.getString("id"), o.getString("title"), o.optString("subtitle"))
                }
            }

            val sourceIndex = JSONArray(assets.readText("$ROOT/sources/index.json"))
            val sources = (0 until sourceIndex.length()).map { i ->
                val o = sourceIndex.getJSONObject(i)
                val id = o.getString("id")
                SourceDoc(id, o.getString("title"), assets.readText("$ROOT/sources/${o.getString("file")}"))
            }

            val topics = (assets.list("$ROOT/topics") ?: emptyArray())
                .filter { it.endsWith(".md") }
                .mapNotNull { parseTopic(it.removeSuffix(".md"), assets.readText("$ROOT/topics/$it")) }
                .sortedBy { it.order }

            return Library(categories, topics, sources)
        }

        /** Visible for tests, which parse the real asset files straight off disk. */
        internal fun parseTopic(id: String, raw: String): Topic? {
            val text = raw.replace("\r\n", "\n")
            if (!text.startsWith("---\n")) return null
            val end = text.indexOf("\n---\n", startIndex = 3)
            if (end < 0) return null

            val front = mutableMapOf<String, String>()
            for (line in text.substring(4, end).split("\n")) {
                val sep = line.indexOf(':')
                if (sep > 0) front[line.take(sep).trim()] = line.substring(sep + 1).trim()
            }

            return Topic(
                id = id,
                title = front["title"] ?: return null,
                categoryId = front["category"].orEmpty(),
                order = front["order"]?.toIntOrNull() ?: 999,
                summary = front["summary"].orEmpty(),
                keywords = front["keywords"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                sourceIds = front["sources"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                body = text.substring(end + 5).trim(),
            )
        }
    }
}

private fun android.content.res.AssetManager.readText(path: String): String =
    open(path).use { it.readBytes().toString(Charsets.UTF_8) }

/** Matches `[[topic-id]]`, `[[topic-id|label]]` and the `src:` variants of both. */
internal val LINK = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?\]\]""")

internal fun MatchResult.target(): String = groupValues[1].trim()

/**
 * Lower-casing for search. [String.lowercase] with the default locale turns a
 * dotted capital I into "i̇" on a Turkish device, so "İSKOTA" and "iskota" stop
 * matching. Folding the Turkish pairs by hand keeps the index locale-independent.
 */
fun String.foldCaseTr(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        sb.append(
            when (c) {
                'I' -> 'ı'
                'İ' -> 'i'
                'Ş' -> 'ş'
                'Ğ' -> 'ğ'
                'Ü' -> 'ü'
                'Ö' -> 'ö'
                'Ç' -> 'ç'
                else -> c.lowercaseChar()
            },
        )
    }
    return sb.toString()
}

/**
 * Turkish sailing vocabulary is routinely typed without its diacritics
 * ("kavanca" for "kavança", "ruzgar" for "rüzgâr"), so the index and the query
 * are both flattened to bare ASCII before matching.
 */
fun String.deaccent(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        sb.append(
            when (c) {
                'ı', 'î' -> 'i'
                'ş' -> 's'
                'ğ' -> 'g'
                'ü', 'û' -> 'u'
                'ö' -> 'o'
                'ç' -> 'c'
                'â' -> 'a'
                else -> c
            },
        )
    }
    return sb.toString()
}

/**
 * Fallback label for a link whose target the library could not resolve: turn
 * the slug back into words rather than showing the raw id.
 */
fun prettyLabel(target: String): String =
    target.removePrefix("src:").split('-').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
    }
