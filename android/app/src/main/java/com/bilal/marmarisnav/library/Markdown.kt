package com.bilal.marmarisnav.library

/**
 * The small Markdown subset the library content is authored in. Parsing it here
 * rather than pulling in a renderer keeps the app's offline-only dependency set
 * unchanged, and the content is ours so the subset stays predictable.
 */
sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Bullet(val text: String, val ordinal: String?) : Block
    data class Quote(val text: String) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
    data object Rule : Block
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^\s*[-*]\s+(.*)$""")
private val ORDERED = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
private val TABLE_DIVIDER = Regex("""^\|[\s:|-]+\|$""")

fun parseMarkdown(source: String): List<Block> {
    val lines = source.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks += Block.Paragraph(text)
        paragraph.setLength(0)
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> flushParagraph()

            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks += Block.Rule
            }

            HEADING.matches(trimmed) -> {
                flushParagraph()
                val m = HEADING.find(trimmed)!!
                blocks += Block.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            }

            // A table is a run of pipe rows whose second line is the divider.
            trimmed.startsWith("|") && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1].trim()) -> {
                flushParagraph()
                val header = splitRow(trimmed)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += splitRow(lines[i].trim())
                    i++
                }
                blocks += Block.Table(header, rows)
                continue
            }

            trimmed.startsWith("> ") || trimmed == ">" -> {
                flushParagraph()
                // Consecutive "> " lines are one quote.
                val quote = StringBuilder(trimmed.removePrefix(">").trim())
                while (i + 1 < lines.size && lines[i + 1].trim().startsWith(">")) {
                    i++
                    quote.append(' ').append(lines[i].trim().removePrefix(">").trim())
                }
                blocks += Block.Quote(quote.toString().trim())
            }

            ORDERED.matches(line) -> {
                flushParagraph()
                val m = ORDERED.find(line)!!
                blocks += Block.Bullet(m.groupValues[2].trim(), m.groupValues[1])
            }

            BULLET.matches(line) -> {
                flushParagraph()
                blocks += Block.Bullet(BULLET.find(line)!!.groupValues[1].trim(), null)
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }
