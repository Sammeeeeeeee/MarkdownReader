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
import androidx.compose.ui.graphics.toArgb
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
    val abbreviationColor: Color,
    val mathColorArgb: Int,
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
            abbreviationColor = cs.secondary,
            mathColorArgb = cs.onSurface.toArgb(),
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
    private val abbreviations: Map<String, String>,
    private val onLink: (String) -> Unit,
) {
    private val builder = AnnotatedString.Builder()
    private val inlineContent = mutableMapOf<String, InlineTextContent>()
    private var imageCount = 0
    private var mathCount = 0

    private val abbreviationRegex: Regex? =
        if (abbreviations.isEmpty()) null
        else Regex(
            abbreviations.keys.sortedByDescending { it.length }
                .joinToString("|", prefix = "(?<![\\p{L}\\p{N}])(?:", postfix = ")(?![\\p{L}\\p{N}])") { Regex.escape(it) }
        )

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
            is Text -> appendText(node.literal ?: "")
            is Emphasis -> spanned(node, SpanStyle(fontStyle = FontStyle.Italic))
            is StrongEmphasis -> spanned(node, SpanStyle(fontWeight = FontWeight.Bold))
            is Strikethrough -> spanned(node, SpanStyle(textDecoration = TextDecoration.LineThrough))
            is Ins -> spanned(node, SpanStyle(textDecoration = TextDecoration.Underline))
            is Subscript -> spanned(node, SpanStyle(fontSize = 0.72.em, baselineShift = BaselineShift.Subscript))
            is Superscript -> spanned(node, SpanStyle(fontSize = 0.72.em, baselineShift = BaselineShift.Superscript))
            is MathNode -> appendMath(node)
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
        // References parsed inside re-parsed fragments (e.g. a <details> body) have
        // no number in the document-wide map; keep them readable instead of dropping them.
        val number = footnoteNumbers[label]
        if (number == null) {
            builder.append("[^${node.label}]")
            return
        }
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
                model = imageModel(destination),
                contentDescription = alt.ifBlank { null },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        builder.appendInlineContent(id, alt.ifBlank { "￼" })
    }

    /** Appends plain text, marking any known abbreviations as tappable spans. */
    private fun appendText(literal: String) {
        val regex = abbreviationRegex
        if (regex == null) {
            builder.append(literal)
            return
        }
        var consumed = 0
        regex.findAll(literal).forEach { match ->
            builder.append(literal.substring(consumed, match.range.first))
            val start = builder.length
            builder.append(match.value)
            builder.addStyle(
                SpanStyle(color = styles.abbreviationColor, textDecoration = TextDecoration.Underline),
                start, builder.length,
            )
            builder.addLink(
                LinkAnnotation.Clickable(
                    tag = "abbr:${match.value}",
                    linkInteractionListener = { onLink("abbr:${match.value}") },
                ),
                start, builder.length,
            )
            consumed = match.range.last + 1
        }
        builder.append(literal.substring(consumed))
    }

    /**
     * Renders LaTeX with JLaTeXMath into an inline placeholder. The drawable is
     * built at a fixed reference size and the placeholder is expressed in em, so
     * the equation follows the surrounding font size (and pinch zoom) for free.
     */
    private fun appendMath(node: MathNode) {
        val drawable = latexDrawable(node.latex, styles.mathColorArgb, MATH_REFERENCE_SIZE)
        if (drawable == null) {
            // Invalid TeX: fall back to the raw source in code styling.
            val start = builder.length
            builder.append(node.latex)
            builder.addStyle(
                SpanStyle(fontFamily = styles.monoFamily, color = styles.codeColor, fontSize = 0.9.em),
                start, builder.length,
            )
            return
        }
        val scale = if (node.display) 1.25f else 1.02f
        val widthEm = (drawable.intrinsicWidth * scale / MATH_REFERENCE_SIZE).coerceAtMost(24f)
        val heightEm = drawable.intrinsicHeight * scale / MATH_REFERENCE_SIZE *
            (widthEm / (drawable.intrinsicWidth * scale / MATH_REFERENCE_SIZE))
        val id = "math${mathCount++}"
        inlineContent[id] = InlineTextContent(
            Placeholder(widthEm.em, heightEm.em, PlaceholderVerticalAlign.TextCenter)
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawLatex(drawable) }
        }
        builder.appendInlineContent(id, node.latex)
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
            val style = spanFor(name, attrs)
            val href = if (name == "a") attr(attrs, "href") else null
            if (style != null || href != null || name in containerTags) {
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

    /** Tags that carry no style of their own but may carry color/style attributes. */
    private val containerTags = setOf("span", "font")

    private fun spanFor(name: String, attrs: String): SpanStyle? {
        val base = when (name) {
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
            else -> null
        }
        val attributeStyle = styleFromAttributes(name, attrs)
        return when {
            base != null && attributeStyle != null -> base.merge(attributeStyle)
            else -> base ?: attributeStyle
        }
    }

    /** `<font color="…">` and `style="color: …; background-color: …"` on any tag. */
    private fun styleFromAttributes(name: String, attrs: String): SpanStyle? {
        var span = SpanStyle()
        var present = false
        val css = attr(attrs, "style")
        fun declaration(property: String): String? =
            css?.let { Regex("(?:^|;)\\s*$property\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE).find(it) }
                ?.groupValues?.get(1)?.trim()

        (declaration("color") ?: if (name == "font") attr(attrs, "color") else null)
            ?.let(::parseCssColor)?.let { span = span.copy(color = it); present = true }
        declaration("background-color")?.let(::parseCssColor)
            ?.let { span = span.copy(background = it); present = true }
        declaration("font-weight")?.let { value ->
            if (value.equals("bold", true) || value.equals("bolder", true) || (value.toIntOrNull() ?: 0) >= 600) {
                span = span.copy(fontWeight = FontWeight.Bold); present = true
            }
        }
        declaration("font-style")?.let { value ->
            if (value.equals("italic", true)) { span = span.copy(fontStyle = FontStyle.Italic); present = true }
        }
        declaration("text-decoration")?.let { value ->
            when {
                value.contains("underline", true) -> { span = span.copy(textDecoration = TextDecoration.Underline); present = true }
                value.contains("line-through", true) -> { span = span.copy(textDecoration = TextDecoration.LineThrough); present = true }
            }
        }
        return span.takeIf { present }
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
    abbreviations: Map<String, String> = emptyMap(),
): Pair<AnnotatedString, Map<String, InlineTextContent>> =
    InlineBuilder(styles, footnoteNumbers, abbreviations, onLink).build(node)

/** Reference glyph size (px) used to measure LaTeX before em-scaling. */
internal const val MATH_REFERENCE_SIZE = 100f

/** Adds a background highlight over every case-insensitive [query] match. */
fun AnnotatedString.highlightMatches(query: String, style: SpanStyle): AnnotatedString {
    if (query.isBlank()) return this
    val haystack = text.lowercase()
    val needle = query.lowercase()
    var index = haystack.indexOf(needle)
    if (index < 0) return this
    val out = AnnotatedString.Builder(this)
    while (index >= 0) {
        out.addStyle(style, index, index + needle.length)
        index = haystack.indexOf(needle, index + needle.length)
    }
    return out.toAnnotatedString()
}

/**
 * Model Coil can load: decodes `data:` URIs into bytes, passes everything
 * else (http(s), content, file) straight through.
 */
fun imageModel(destination: String?): Any? {
    if (destination == null || !destination.startsWith("data:")) return destination
    val base64 = destination.substringAfter("base64,", "")
    if (base64.isEmpty()) return destination
    return runCatching { java.nio.ByteBuffer.wrap(android.util.Base64.decode(base64, android.util.Base64.DEFAULT)) }
        .getOrDefault(destination)
}

private val cssNamedColors = mapOf(
    "black" to 0xFF000000, "white" to 0xFFFFFFFF, "red" to 0xFFFF0000, "green" to 0xFF008000,
    "blue" to 0xFF0000FF, "yellow" to 0xFFFFFF00, "orange" to 0xFFFFA500, "purple" to 0xFF800080,
    "pink" to 0xFFFFC0CB, "brown" to 0xFFA52A2A, "gray" to 0xFF808080, "grey" to 0xFF808080,
    "cyan" to 0xFF00FFFF, "magenta" to 0xFFFF00FF, "lime" to 0xFF00FF00, "teal" to 0xFF008080,
    "navy" to 0xFF000080, "olive" to 0xFF808000, "maroon" to 0xFF800000, "silver" to 0xFFC0C0C0,
    "gold" to 0xFFFFD700, "indigo" to 0xFF4B0082, "violet" to 0xFFEE82EE, "coral" to 0xFFFF7F50,
    "salmon" to 0xFFFA8072, "khaki" to 0xFFF0E68C, "crimson" to 0xFFDC143C, "tomato" to 0xFFFF6347,
    "orchid" to 0xFFDA70D6, "plum" to 0xFFDDA0DD, "turquoise" to 0xFF40E0D0, "skyblue" to 0xFF87CEEB,
    "steelblue" to 0xFF4682B4, "hotpink" to 0xFFFF69B4, "deeppink" to 0xFFFF1493,
    "rebeccapurple" to 0xFF663399, "darkred" to 0xFF8B0000, "darkgreen" to 0xFF006400,
    "darkblue" to 0xFF00008B, "darkorange" to 0xFFFF8C00, "darkgray" to 0xFFA9A9A9,
    "darkgrey" to 0xFFA9A9A9, "lightgray" to 0xFFD3D3D3, "lightgrey" to 0xFFD3D3D3,
    "lightblue" to 0xFFADD8E6, "lightgreen" to 0xFF90EE90, "lightyellow" to 0xFFFFFFE0,
    "lightpink" to 0xFFFFB6C1, "beige" to 0xFFF5F5DC, "ivory" to 0xFFFFFFF0, "lavender" to 0xFFE6E6FA,
    "aqua" to 0xFF00FFFF, "fuchsia" to 0xFFFF00FF, "chocolate" to 0xFFD2691E, "tan" to 0xFFD2B48C,
    "slategray" to 0xFF708090, "slateblue" to 0xFF6A5ACD, "seagreen" to 0xFF2E8B57,
    "forestgreen" to 0xFF228B22, "royalblue" to 0xFF4169E1, "dodgerblue" to 0xFF1E90FF,
    "firebrick" to 0xFFB22222, "goldenrod" to 0xFFDAA520, "orangered" to 0xFFFF4500,
    "mediumpurple" to 0xFF9370DB, "cadetblue" to 0xFF5F9EA0, "peru" to 0xFFCD853F,
)

/** Named colors, `#rgb`, `#rrggbb`, `#rrggbbaa`, and `rgb()/rgba()` notations. */
fun parseCssColor(raw: String): Color? {
    val value = raw.trim().lowercase()
    cssNamedColors[value]?.let { return Color(it) }
    if (value.startsWith("#")) {
        val hex = value.drop(1)
        return runCatching {
            when (hex.length) {
                3 -> Color(
                    red = hex[0].digitToInt(16) * 17 / 255f,
                    green = hex[1].digitToInt(16) * 17 / 255f,
                    blue = hex[2].digitToInt(16) * 17 / 255f,
                )
                6 -> Color(0xFF000000 or hex.toLong(16))
                8 -> {
                    val rgba = hex.toLong(16)
                    Color(((rgba and 0xFF) shl 24) or (rgba ushr 8))
                }
                else -> null
            }
        }.getOrNull()
    }
    val rgb = Regex("rgba?\\(([^)]*)\\)").find(value)?.groupValues?.get(1) ?: return null
    val parts = rgb.split(',').map { it.trim() }
    if (parts.size < 3) return null
    val channels = parts.take(3).map { part ->
        if (part.endsWith("%")) ((part.dropLast(1).toFloatOrNull() ?: return null) * 2.55f).toInt()
        else part.toFloatOrNull()?.toInt() ?: return null
    }.map { it.coerceIn(0, 255) }
    val alpha = parts.getOrNull(3)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
    return Color(channels[0], channels[1], channels[2], (alpha * 255).toInt())
}
