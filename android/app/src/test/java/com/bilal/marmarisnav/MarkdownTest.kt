package com.bilal.marmarisnav

import com.bilal.marmarisnav.library.Block
import com.bilal.marmarisnav.library.deaccent
import com.bilal.marmarisnav.library.foldCaseTr
import com.bilal.marmarisnav.library.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun `headings carry their level`() {
        val blocks = parseMarkdown("# Bir\n\n## İki\n\n### Üç")
        assertEquals(listOf(1, 2, 3), blocks.filterIsInstance<Block.Heading>().map { it.level })
        assertEquals("İki", (blocks[1] as Block.Heading).text)
    }

    @Test
    fun `wrapped lines join into one paragraph`() {
        val blocks = parseMarkdown("Tramola bir\nkontra degistirme\nmanevrasidir.\n\nIkinci paragraf.")
        val paragraphs = blocks.filterIsInstance<Block.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals("Tramola bir kontra degistirme manevrasidir.", paragraphs[0].text)
    }

    @Test
    fun `bullets keep their ordinal only when numbered`() {
        val blocks = parseMarkdown("- ilk\n- ikinci\n\n1. bir\n2. iki")
        val bullets = blocks.filterIsInstance<Block.Bullet>()
        assertEquals(listOf(null, null, "1", "2"), bullets.map { it.ordinal })
        assertEquals("ilk", bullets[0].text)
    }

    @Test
    fun `a pipe table needs its divider row`() {
        val table = parseMarkdown("| Terim | Anlam |\n|---|---|\n| Neta | Temiz |\n| Pupa | Kic |")
            .filterIsInstance<Block.Table>()
            .single()
        assertEquals(listOf("Terim", "Anlam"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Pupa", "Kic"), table.rows[1])
    }

    @Test
    fun `pipe lines without a divider stay prose`() {
        val blocks = parseMarkdown("| bu bir tablo degil")
        assertTrue(blocks.single() is Block.Paragraph)
    }

    @Test
    fun `consecutive quote lines merge`() {
        val quote = parseMarkdown("> Pruvaniz neta,\n> ruzgariniz kolayina olsun.")
            .filterIsInstance<Block.Quote>()
            .single()
        assertEquals("Pruvaniz neta, ruzgariniz kolayina olsun.", quote.text)
    }

    @Test
    fun `case folding does not depend on the Turkish locale`() {
        // lowercase() would give "i̇skota" for a Turkish default locale.
        assertEquals("iskota", "İSKOTA".foldCaseTr())
        assertEquals("kıç", "KIÇ".foldCaseTr())
        assertEquals("rüzgâr", "RÜZGÂR".foldCaseTr())
    }

    @Test
    fun `deaccenting lets undotted typing match`() {
        assertEquals("kavanca", "kavança".foldCaseTr().deaccent())
        assertEquals("ruzgar", "RÜZGÂR".foldCaseTr().deaccent())
        assertEquals("ayi bacagi", "Ayı Bacağı".foldCaseTr().deaccent())
    }
}
