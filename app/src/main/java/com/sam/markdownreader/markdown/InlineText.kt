package com.sam.markdownreader.markdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.sam.markdownreader.ui.theme.MonoFamily
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.image.attributes.ImageAttributes
import org.commonmark.ext.ins.Ins
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text

data class InlineStyleSet(
    val linkColor: Color,
    val codeColor: Color,
    val codeBackground: Color,
    val markColor: Color,
    val markBackground: Color,
    val footnoteColor: Color,
    val monoFamily: FontFamily,
)

@Composable
fun rememberInlineStyleSet(): InlineStyleSet {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        InlineStyleSet(
            linkColor = cs.primary,
            codeColor = cs.onSecondaryContainer,
            codeBackground = cs.secondaryContainer.copy(alpha = 0.45f),
            markColor = cs.onTertiaryContainer,
            markBackground = cs.tertiaryContainer,
            footnoteColor = cs.secondary,
            monoFamily = MonoFamily,
        )
    }
}

/**
 * Walks commonmark inline nodes and produces an [AnnotatedString] plus the
 * inline content map used for images embedded in the middle of text.
 */
class InlineBuilder(
    private val styles: InlineStyleSet,
    private val footnoteNumbers: Map<String, Int>,
    private val onLink: (String) -> Unit,
) {
    private val builder = AnnotatedString.Builder()
    private val inlineContent = mutableMapOf<String, InlineTextContent>()
    private var imageCount = 0

    private data class OpenTag(val name: String, val start: Int, val spanStyle: SpanStyle?, val href: String? = null)

    private val htmlStack = mutableListOf<OpenTag>()

    fun build(parent: Node): Pair<AnnotatedString, Map<String, InlineTextContent>> {
        renderChildren(parent)
        // Unterminated inline HTML: style whatever came after the open tag.
        htmlStack.forEach { tag -> applyTag(tag, builder.length) }
        htmlStack.clear()
        return builder.toAnnotatedString() to inlineContent
    }

    private fun renderChildren(node: Node) {
        var child = node.firstChild
        while (child != null) {
            render(child)
            child = child.next
        }
    }

    private fun spanned(node: Node, style: SpanStyle) {
        val start = builder.length
        renderChildren(node)
        builder.addStyle(style, start, builder.length)
    }

    private fun render(node: Node) {
        when (node) {
            is Text -> builder.append(node.literal ?: "")
            is Emphasis -> spanned(node, SpanStyle(fontStyle = FontStyle.Italic))
            is StrongEmphasis -> spanned(node, SpanStyle(fontWeight = FontWeight.Bold))
            is Strikethrough -> spanned(node, SpanStyle(textDecoration = TextDecoration.LineThrough))
            is Ins -> spanned(node, SpanStyle(textDecoration = TextDecoration.Underline))
            is Code -> {
                val start = builder.length
                builder.append(node.literal ?: "")
                builder.addStyle(
                    SpanStyle(
                        fontFamily = styles.monoFamily,
                        color = styles.codeColor,
                        background = styles.codeBackground,
                        fontSize = 0.88.em,
                    ),
                    start, builder.length,
                )
            }
            is Link -> {
                val start = builder.length
                renderChildren(node)
                if (builder.length == start) builder.append(node.destination ?: "")
                addLink(node.destination ?: "", start, builder.length)
            }
            is Image -> appendImage(node.destination, node.innerText())
            is HardLineBreak -> builder.append('\n')
            is SoftLineBreak -> builder.append(' ')
            is HtmlInline -> htmlInline(node.literal ?: "")
            is FootnoteReference -> footnoteRef(node)
            is TaskListItemMarker -> Unit // drawn as a checkbox by the list block
            is ImageAttributes -> Unit    // consumed by block image rendering
            else -> renderChildren(node)
        }
    }

    private fun addLink(destination: String, start: Int, end: Int) {
        if (start >= end) return
        builder.addStyle(
            SpanStyle(color = styles.linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
            start, end,
        )
        builder.addLink(
            LinkAnnotation.Clickable(tag = destination, linkInteractionListener = { onLink(destination) }),
            start, end,
        )
    }

    private fun footnoteRef(node: FootnoteReference) {
        val label = node.label?.lowercase() ?: return
        val number = footnoteNumbers[label] ?: return
        val start = builder.length
        builder.append("[$number]")
        builder.addStyle(
            SpanStyle(
                color = styles.footnoteColor,
                fontWeight = FontWeight.Bold,
                fontSize = 0.72.em,
                baselineShift = BaselineShift.Superscript,
            ),
            start, builder.length,
        )
        builder.addLink(
            LinkAnnotation.Clickable(tag = "footnote:$label", linkInteractionListener = { onLink("footnote:$label") }),
            start, builder.length,
        )
    }

    private fun appendImage(destination: String?, alt: String) {
        val id = "inlineImage${imageCount++}"
        inlineContent[id] = InlineTextContent(
            Placeholder(1.4.em, 1.4.em, PlaceholderVerticalAlign.TextCenter)
        ) {
            AsyncImage(
                model = destination,
                contentDescription = alt.ifBlank { null },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        builder.appendInlineContent(id, alt.ifBlank { "￼" })
    }

    // ---- minimal inline HTML support -------------------------------------

    private val tagRegex = Regex("^<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>$", RegexOption.DOT_MATCHES_ALL)

    private fun htmlInline(literal: String) {
        val trimmed = literal.trim()
        if (trimmed.startsWith("<!--")) return
        val match = tagRegex.find(trimmed) ?: return
        val closing = match.groupValues[1] == "/"
        val name = match.groupValues[2].lowercase()
        val attrs = match.groupValues[3]

        if (!closing) {
            when (name) {
                "br" -> { builder.append('\n'); return }
                "img" -> {
                    appendImage(attr(attrs, "src"), attr(attrs, "alt") ?: "")
                    return
                }
                "hr", "input", "meta", "link", "source", "wbr" -> return
            }
            val style = spanFor(name)
            val href = if (name == "a") attr(attrs, "href") else null
            if (style != null || href != null) {
                htmlStack.add(OpenTag(name, builder.length, style, href))
            }
        } else {
            val index = htmlStack.indexOfLast { it.name == name }
            if (index >= 0) {
                val tag = htmlStack.removeAt(index)
                applyTag(tag, builder.length)
            }
        }
    }

    private fun applyTag(tag: OpenTag, end: Int) {
        if (tag.start >= end) return
        tag.spanStyle?.let { builder.addStyle(it, tag.start, end) }
        tag.href?.let { addLink(it, tag.start, end) }
    }

    private fun spanFor(name: String): SpanStyle? = when (name) {
        "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
        "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
        "u", "ins" -> SpanStyle(textDecoration = TextDecoration.Underline)
        "s", "del", "strike" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        "code", "tt", "samp" -> SpanStyle(
            fontFamily = styles.monoFamily,
            color = styles.codeColor,
            background = styles.codeBackground,
            fontSize = 0.88.em,
        )
        "kbd" -> SpanStyle(
            fontFamily = styles.monoFamily,
            background = styles.codeBackground,
            fontSize = 0.85.em,
            fontWeight = FontWeight.SemiBold,
        )
        "mark" -> SpanStyle(color = styles.markColor, background = styles.markBackground)
        "sub" -> SpanStyle(fontSize = 0.72.em, baselineShift = BaselineShift.Subscript)
        "sup" -> SpanStyle(fontSize = 0.72.em, baselineShift = BaselineShift.Superscript)
        "a" -> null // handled via href
        else -> null
    }

    private fun attr(attrs: String, name: String): String? {
        val quoted = Regex("$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(attrs)
            ?: Regex("$name\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(attrs)
        if (quoted != null) return quoted.groupValues[1]
        return Regex("$name\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
    }
}

fun buildInlineText(
    node: Node,
    styles: InlineStyleSet,
    footnoteNumbers: Map<String, Int>,
    onLink: (String) -> Unit,
): Pair<AnnotatedString, Map<String, InlineTextContent>> =
    InlineBuilder(styles, footnoteNumbers, onLink).build(node)
