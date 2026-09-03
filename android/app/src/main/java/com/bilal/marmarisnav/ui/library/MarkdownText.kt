package com.bilal.marmarisnav.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilal.marmarisnav.library.Block
import com.bilal.marmarisnav.library.parseMarkdown

/** Where a tapped link points. */
sealed interface LinkTarget {
    data class TopicRef(val id: String) : LinkTarget
    data class SourceRef(val id: String) : LinkTarget
    data class External(val url: String) : LinkTarget
}

const val LINK_TAG = "link"

/**
 * Renders the library's Markdown subset. Links are hit-tested against the laid
 * out text rather than using ClickableText, which is deprecated, or withLink,
 * which is newer than the Compose version pinned here.
 */
@Composable
fun MarkdownBody(
    markdown: String,
    onLink: (LinkTarget) -> Unit,
    modifier: Modifier = Modifier,
    /** Title to show for a `[[link]]` written without an explicit label. */
    labelFor: (String) -> String = ::prettyLabel,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier) {
        for (block in blocks) {
            when (block) {
                is Block.Heading -> {
                    Spacer(Modifier.height(if (block.level <= 2) 20.dp else 14.dp))
                    Text(
                        text = block.text,
                        style = when (block.level) {
                            1 -> TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            2 -> TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            else -> TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        },
                        color = if (block.level <= 2) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }

                is Block.Paragraph -> {
                    LinkedText(block.text, onLink, labelFor = labelFor)
                    Spacer(Modifier.height(10.dp))
                }

                is Block.Bullet -> {
                    Row(Modifier.padding(bottom = 6.dp)) {
                        Text(
                            text = block.ordinal?.let { "$it." } ?: "•",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(if (block.ordinal != null) 26.dp else 18.dp),
                        )
                        LinkedText(block.text, onLink, labelFor = labelFor)
                    }
                }

                is Block.Quote -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 10.dp),
                    ) {
                        Box(
                            Modifier
                                .padding(start = 10.dp, end = 10.dp)
                                .width(3.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        LinkedText(block.text, onLink, Modifier.padding(end = 12.dp), labelFor = labelFor)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                is Block.Table -> {
                    MarkdownTable(block, onLink, labelFor)
                    Spacer(Modifier.height(12.dp))
                }

                Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 14.dp))
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    table: Block.Table,
    onLink: (LinkTarget) -> Unit,
    labelFor: (String) -> String,
) {
    // Wide tables scroll sideways instead of squeezing the page.
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
    ) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            for (cell in table.header) {
                Text(
                    text = stripInline(cell, labelFor),
                    modifier = Modifier.width(cellWidth(table)).padding(8.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
        for ((index, row) in table.rows.withIndex()) {
            Row(
                Modifier.background(
                    if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surface,
                ),
            ) {
                for (i in table.header.indices) {
                    Box(Modifier.width(cellWidth(table)).padding(8.dp)) {
                        LinkedText(row.getOrElse(i) { "" }, onLink, fontSize = 13.sp, labelFor = labelFor)
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/** Two-column tables read best wide; wider ones get narrower, scrollable columns. */
private fun cellWidth(table: Block.Table) = when (table.header.size) {
    1 -> 300.dp
    2 -> 190.dp
    3 -> 150.dp
    else -> 130.dp
}

@Composable
private fun LinkedText(
    raw: String,
    onLink: (LinkTarget) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    labelFor: (String) -> String = ::prettyLabel,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(raw, linkColor) { buildInline(raw, linkColor, labelFor) }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { pos ->
                val result = layout.value ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(pos)
                annotated.getStringAnnotations(LINK_TAG, offset, offset)
                    .firstOrNull()
                    ?.let { onLink(it.item.toLinkTarget()) }
            }
        },
        onTextLayout = { layout.value = it },
        fontSize = fontSize,
        lineHeight = fontSize * 1.45f,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun String.toLinkTarget(): LinkTarget = when {
    startsWith("src:") -> LinkTarget.SourceRef(removePrefix("src:"))
    startsWith("http://") || startsWith("https://") -> LinkTarget.External(this)
    else -> LinkTarget.TopicRef(this)
}

private val INLINE = Regex(
    // [[topic|label]] | [label](url) | **bold** | *italic* | `code`
    """\[\[([^\]|]+)(?:\|([^\]]+))?\]\]|\[([^\]]+)\]\(([^)]+)\)|\*\*([^*]+)\*\*|\*([^*]+)\*|`([^`]+)`""",
)

private fun buildInline(
    raw: String,
    linkColor: Color,
    labelFor: (String) -> String,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (m in INLINE.findAll(raw)) {
        if (m.range.first > cursor) append(raw.substring(cursor, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> {
                val target = g[1].trim()
                val label = g[2].ifEmpty { labelFor(target) }
                pushStringAnnotation(LINK_TAG, target)
                withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                    append(label)
                }
                pop()
            }
            g[3].isNotEmpty() -> {
                pushStringAnnotation(LINK_TAG, g[4])
                withStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ) { append(g[3]) }
                pop()
            }
            g[5].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[5]) }
            g[6].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
            g[7].isNotEmpty() ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(g[7]) }
        }
        cursor = m.range.last + 1
    }
    if (cursor < raw.length) append(raw.substring(cursor))
}

/**
 * Fallback label for a link whose target the library could not resolve: turn
 * the slug back into words rather than showing the raw id.
 */
fun prettyLabel(target: String): String =
    target.removePrefix("src:").split('-').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
    }

/** Strips inline markup for places that render plain text, such as table headers. */
private fun stripInline(raw: String, labelFor: (String) -> String): String = INLINE.replace(raw) { m ->
    val g = m.groupValues
    when {
        g[1].isNotEmpty() -> g[2].ifEmpty { labelFor(g[1].trim()) }
        g[3].isNotEmpty() -> g[3]
        g[5].isNotEmpty() -> g[5]
        g[6].isNotEmpty() -> g[6]
        else -> g[7]
    }
}
