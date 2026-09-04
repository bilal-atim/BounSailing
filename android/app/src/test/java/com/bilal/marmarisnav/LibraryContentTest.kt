package com.bilal.marmarisnav

import com.bilal.marmarisnav.library.Library
import com.bilal.marmarisnav.library.LibrarySearch
import com.bilal.marmarisnav.library.SearchHit
import com.bilal.marmarisnav.library.SearchTarget
import com.bilal.marmarisnav.library.SourceDoc
import com.bilal.marmarisnav.library.Topic
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the library content itself rather than the code that renders it: a
 * topic renamed or a cross-reference mistyped in the assets would otherwise only
 * show up as a dead link on a phone at sea.
 */
class LibraryContentTest {

    private val root = File("src/main/assets/library")

    private val topics: List<Topic> = File(root, "topics")
        .listFiles { f -> f.name.endsWith(".md") }
        .orEmpty()
        .sortedBy { it.name }
        .mapNotNull { Library.parseTopic(it.name.removeSuffix(".md"), it.readText()) }

    // org.json is an unimplemented stub in local unit tests, so the two index
    // files are read with a plain regex here rather than a JSON parser.
    private val sources: List<SourceDoc> =
        Regex("""\{[^}]*?"id"\s*:\s*"([^"]+)"[^}]*?"title"\s*:\s*"([^"]+)"[^}]*?"file"\s*:\s*"([^"]+)"[^}]*?\}""")
            .findAll(File(root, "sources/index.json").readText())
            .map { m ->
                SourceDoc(
                    m.groupValues[1],
                    m.groupValues[2],
                    File(root, "sources/${m.groupValues[3]}").readText(),
                )
            }
            .toList()

    private val categoryIds: Set<String> =
        Regex(""""id"\s*:\s*"([^"]+)"""")
            .findAll(File(root, "categories.json").readText())
            .map { it.groupValues[1] }
            .toSet()

    private val library = Library(emptyList(), topics, sources)

    private val linkPattern = Regex("""\[\[([^\]|]+)(?:\|[^\]]+)?\]\]""")

    private val SearchHit.topicId: String?
        get() = (target as? SearchTarget.TopicRef)?.topic?.id

    private val SearchHit.sourceId: String?
        get() = (target as? SearchTarget.SourceRef)?.doc?.id

    @Test
    fun `every topic parses with the fields the UI needs`() {
        assertTrue("no topics found under $root", topics.isNotEmpty())
        for (t in topics) {
            assertTrue("${t.id}: empty title", t.title.isNotBlank())
            assertTrue("${t.id}: empty summary", t.summary.isNotBlank())
            assertTrue("${t.id}: no keywords", t.keywords.isNotEmpty())
            assertTrue("${t.id}: empty body", t.body.length > 200)
            assertTrue("${t.id}: unknown category '${t.categoryId}'", t.categoryId in categoryIds)
        }
    }

    @Test
    fun `every cross-reference resolves`() {
        val topicIds = topics.map { it.id }.toSet()
        val sourceIds = sources.map { it.id }.toSet()
        val dead = mutableListOf<String>()

        for (t in topics) {
            for (m in linkPattern.findAll(t.body)) {
                val target = m.groupValues[1].trim()
                if (target.startsWith("src:")) {
                    if (target.removePrefix("src:") !in sourceIds) dead += "${t.id} -> $target"
                } else if (target !in topicIds) {
                    dead += "${t.id} -> $target"
                }
            }
            for (s in t.sourceIds) {
                if (s !in sourceIds) dead += "${t.id} frontmatter -> $s"
            }
        }
        assertEquals("dead references: $dead", emptyList<String>(), dead)
    }

    @Test
    fun `no topic is unreachable from another topic`() {
        val linked = topics.flatMap { t ->
            linkPattern.findAll(t.body).map { it.groupValues[1].trim() }
        }.filterNot { it.startsWith("src:") }.toSet()

        val orphans = topics.map { it.id }.filterNot { it in linked }
        assertEquals("topics nothing links to: $orphans", emptyList<String>(), orphans)
    }

    @Test
    fun `searching a training heading returns its own topic first`() {
        val search = LibrarySearch(library)
        // The headings the crew actually type, including undotted spellings.
        val expected = mapOf(
            "kavanca" to "kavanca",
            "tramola" to "tramola",
            "ayı bacağı" to "ayi-bacagi",
            "ayi bacagi" to "ayi-bacagi",
            "zodi" to "zodi",
            "waste" to "waste-atma",
            "tuvalet" to "tuvalet-egitimi",
            "yedek yeke" to "yedek-yeke",
            "pob" to "pob",
            "telsiz" to "telsiz",
            "balon" to "balon",
            "dugumler" to "dugumler",
            "navigasyon" to "navigasyon",
            "basustu" to "basustu",
            "checklist" to "gezi-oncesi-checklist",
        )
        for ((query, topicId) in expected) {
            val hits = search.search(query)
            assertTrue("'$query' returned nothing", hits.isNotEmpty())
            assertEquals("'$query' ranked the wrong topic first", topicId, hits.first().topicId)
        }
    }

    @Test
    fun `a term buried in the body still finds its topic`() {
        val search = LibrarySearch(library)
        // "spinlock" is not in any title; it lives in the safety topic's text.
        val hits = search.search("spinlock").map { it.topicId }
        assertTrue("spinlock -> $hits", "yelken-basarken-guvenlik" in hits)

        val neta = search.search("neta").map { it.topicId }
        assertTrue("neta -> $neta", "teknenin-netalanmasi" in neta)
    }

    @Test
    fun `a term that only the source documents carry still comes back`() {
        val search = LibrarySearch(library)
        // Neither word appears in any topic; they are only in the raw lectures,
        // which is exactly the case the search used to miss entirely.
        assertEquals(
            listOf("2-yildiz-teorik-kitabi"),
            search.search("abandone").mapNotNull { it.sourceId },
        )
        assertEquals(
            listOf("yariscilik-1"),
            search.search("buyukada").mapNotNull { it.sourceId },
        )
    }

    @Test
    fun `a topic outranks the source document it was drawn from`() {
        val hits = LibrarySearch(library).search("balon")
        assertEquals("the written-up topic should lead", "balon", hits.first().topicId)
        assertTrue("the source should still be offered", "balon" in hits.mapNotNull { it.sourceId })
    }

    @Test
    fun `multiple terms narrow the result set`() {
        val search = LibrarySearch(library)
        val one = search.search("balon")
        val two = search.search("balon donatma")
        assertTrue("AND search should not widen", two.size <= one.size)
        assertTrue(two.isNotEmpty())
    }

    @Test
    fun `a nonsense query returns nothing`() {
        assertTrue(LibrarySearch(library).search("qwertyuiop").isEmpty())
    }

    @Test
    fun `backlinks are symmetric with the links that produce them`() {
        val backlinks = library.backlinks("tramola").map { it.id }
        assertTrue("kavanca should link to tramola", "kavanca" in backlinks)
        assertTrue("dumenci should link to tramola", "dumenci" in backlinks)
    }
}
