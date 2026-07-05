@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.sam.markdownreader.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.gfm.alerts.Alert
import org.commonmark.ext.gfm.alerts.AlertTitle
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.image.attributes.ImageAttributes
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text as MdText
import org.commonmark.node.ThematicBreak

data class RenderCtx(
    val styles: InlineStyleSet,
    val doc: ParsedDocument,
    val onLink: (String) -> Unit,
    val listDepth: Int = 0,
)

fun Node.childList(): List<Node> {
    val out = mutableListOf<Node>()
    var c = firstChild
    while (c != null) {
        out += c
        c = c.next
    }
    return out
}

@Composable
fun MdInlineText(
    node: Node,
    ctx: RenderCtx,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val (text, inline) = remember(node, ctx.styles) {
        buildInlineText(node, ctx.styles, ctx.doc.footnoteNumbers, ctx.onLink)
    }
    Text(
        text = text,
        inlineContent = inline,
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** Renders one top-level (or nested) markdown block node. */
@Composable
fun MarkdownBlock(node: Node, ctx: RenderCtx, modifier: Modifier = Modifier) {
    when (node) {
        is Heading -> MdHeading(node, ctx, modifier)
        is Paragraph -> MdParagraph(node, ctx, modifier)
        is Alert -> MdAlert(node, ctx, modifier)
        is BlockQuote -> MdBlockQuote(node, ctx, modifier)
        is BulletList -> MdList(node, ctx, modifier)
        is OrderedList -> MdList(node, ctx, modifier)
        is FencedCodeBlock -> MdCodeBlock(node.literal.orEmpty(), node.info, modifier)
        is IndentedCodeBlock -> MdCodeBlock(node.literal.orEmpty(), null, modifier)
        is ThematicBreak -> MdDivider(modifier)
        is TableBlock -> MdTable(node, ctx, modifier)
        is HtmlBlock -> MdHtmlBlock(node, ctx, modifier)
        is LinkReferenceDefinition -> Unit
        is FootnoteDefinition -> Unit
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            node.childList().forEach { MarkdownBlock(it, ctx) }
        }
    }
}

// ---------------------------------------------------------------- headings

@Composable
private fun MdHeading(node: Heading, ctx: RenderCtx, modifier: Modifier = Modifier) {
    val typ = MaterialTheme.typography
    val cs = MaterialTheme.colorScheme
    val (style, color) = when (node.level) {
        1 -> typ.displaySmallEmphasized to cs.onSurface
        2 -> typ.headlineLargeEmphasized to cs.primary
        3 -> typ.headlineMediumEmphasized to cs.onSurface
        4 -> typ.headlineSmallEmphasized to cs.secondary
        5 -> typ.titleLargeEmphasized to cs.onSurface
        else -> typ.titleMediumEmphasized to cs.onSurfaceVariant
    }
    Column(modifier.padding(top = if (node.level <= 2) 20.dp else 14.dp, bottom = 2.dp)) {
        MdInlineText(node, ctx, style, color = color)
        if (node.level <= 2) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(if (node.level == 1) 72.dp else 48.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(cs.primary, cs.tertiary)
                        )
                    )
            )
        }
    }
}

// -------------------------------------------------------------- paragraphs

private fun Paragraph.imageOnlyChildren(): List<Image>? {
    val images = mutableListOf<Image>()
    for (child in childList()) {
        when {
            child is Image -> images += child
            child is SoftLineBreak -> Unit
            child is MdText && child.literal.isNullOrBlank() -> Unit
            else -> return null
        }
    }
    return images.takeIf { it.isNotEmpty() }
}

@Composable
private fun MdParagraph(node: Paragraph, ctx: RenderCtx, modifier: Modifier = Modifier) {
    val images = node.imageOnlyChildren()
    if (images != null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            images.forEach { MdBlockImage(it, ctx) }
        }
    } else {
        MdInlineText(
            node, ctx,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
    }
}

// ------------------------------------------------------------------ images

@Composable
fun MdBlockImage(image: Image, ctx: RenderCtx, modifier: Modifier = Modifier) {
    val attrs = image.childList().filterIsInstance<ImageAttributes>().firstOrNull()?.attributes
    val alt = image.innerText().trim()
    val caption = image.title?.takeIf { it.isNotBlank() } ?: alt.takeIf { it.isNotBlank() }
    val widthDp: Dp? = attrs?.get("width")?.toIntOrNull()?.dp

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val imageModifier = if (widthDp != null) {
            Modifier.widthIn(max = widthDp)
        } else {
            Modifier.fillMaxWidth()
        }
        AsyncImage(
            model = image.destination,
            contentDescription = alt.ifBlank { null },
            modifier = imageModifier
                .clip(RoundedCornerShape(20.dp))
                .heightIn(max = 480.dp),
            contentScale = ContentScale.Fit,
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ------------------------------------------------------------- blockquotes

@Composable
private fun MdBlockQuote(node: BlockQuote, ctx: RenderCtx, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 6.dp),
        color = cs.surfaceContainerLow,
    ) {
        val barColor = cs.primary
        Column(
            Modifier
                .drawBehind {
                    drawRoundRect(
                        color = barColor,
                        size = Size(4.dp.toPx(), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    )
                }
                .padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            node.childList().forEach { MarkdownBlock(it, ctx) }
        }
    }
}

// ----------------------------------------------------------------- alerts

private data class AlertStyle(
    val accent: Color,
    val container: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

@Composable
private fun alertStyle(type: String): AlertStyle {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return when (type.uppercase()) {
        "TIP" -> AlertStyle(
            accent = if (dark) Color(0xFF8CD5A2) else Color(0xFF146C2E),
            container = if (dark) Color(0xFF12351C) else Color(0xFFC9F2D2),
            icon = Icons.Outlined.Lightbulb,
            label = "Tip",
        )
        "IMPORTANT" -> AlertStyle(cs.secondary, cs.secondaryContainer.copy(alpha = if (dark) 0.45f else 0.6f), Icons.Outlined.Campaign, "Important")
        "WARNING" -> AlertStyle(cs.tertiary, cs.tertiaryContainer.copy(alpha = if (dark) 0.5f else 0.7f), Icons.Outlined.WarningAmber, "Warning")
        "CAUTION" -> AlertStyle(cs.error, cs.errorContainer.copy(alpha = if (dark) 0.45f else 0.75f), Icons.Outlined.Report, "Caution")
        else -> AlertStyle(cs.primary, cs.primaryContainer.copy(alpha = if (dark) 0.45f else 0.6f), Icons.Outlined.Info, "Note")
    }
}

@Composable
private fun MdAlert(node: Alert, ctx: RenderCtx, modifier: Modifier = Modifier) {
    val style = alertStyle(node.type ?: "NOTE")
    val children = node.childList()
    val titleNode = children.firstOrNull() as? AlertTitle
    val body = if (titleNode != null) children.drop(1) else children

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = style.container,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(style.icon, contentDescription = null, tint = style.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    titleNode?.innerText()?.takeIf { it.isNotBlank() } ?: style.label,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = style.accent,
                )
            }
            body.forEach { MarkdownBlock(it, ctx) }
        }
    }
}

// ------------------------------------------------------------------ lists

private fun ListItem.taskMarker(): TaskListItemMarker? {
    val para = firstChild as? Paragraph ?: return null
    return para.firstChild as? TaskListItemMarker
}

@Composable
private fun MdList(list: ListBlock, ctx: RenderCtx, modifier: Modifier = Modifier) {
    val ordered = list as? OrderedList
    val tight = list.isTight
    val start = ordered?.markerStartNumber ?: 1
    val childCtx = ctx.copy(listDepth = ctx.listDepth + 1)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (tight) 6.dp else 14.dp)) {
        list.childList().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            val task = item.taskMarker()
            Row {
                ListMarker(
                    ordered = ordered != null,
                    number = start + index,
                    depth = ctx.listDepth,
                    task = task,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (tight) 4.dp else 10.dp),
                ) {
                    val blocks = item.childList()
                    if (blocks.isEmpty()) {
                        Spacer(Modifier.height(2.dp))
                    }
                    blocks.forEach { MarkdownBlock(it, childCtx) }
                }
            }
        }
    }
}

@Composable
private fun ListMarker(ordered: Boolean, number: Int, depth: Int, task: TaskListItemMarker?) {
    val cs = MaterialTheme.colorScheme
    when {
        task != null -> {
            val checked = task.isChecked
            Icon(
                if (checked) Icons.Rounded.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (checked) "Done" else "To do",
                tint = if (checked) cs.primary else cs.outline,
                modifier = Modifier
                    .padding(end = 10.dp, top = 3.dp)
                    .size(21.dp),
            )
        }
        ordered -> Text(
            "$number.",
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = cs.primary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(min = 24.dp)
                .padding(end = 8.dp, top = 3.dp),
        )
        else -> {
            // Expressive bullets: the shape and color rotate with nesting depth.
            val color = when (depth % 3) {
                0 -> cs.primary
                1 -> cs.secondary
                else -> cs.tertiary
            }
            Box(Modifier.padding(start = 2.dp, end = 12.dp, top = 10.dp)) {
                androidx.compose.foundation.Canvas(Modifier.size(9.dp)) {
                    when (depth % 3) {
                        0 -> drawCircle(color)
                        1 -> rotate(45f) {
                            drawRect(
                                color,
                                topLeft = Offset(size.width * 0.14f, size.height * 0.14f),
                                size = Size(size.width * 0.72f, size.height * 0.72f),
                            )
                        }
                        else -> drawCircle(color, style = Stroke(width = 2.2.dp.toPx()))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ extras

@Composable
fun MdDivider(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(
            Modifier
                .width(120.dp)
                .height(14.dp)
        ) {
            val amplitude = size.height * 0.32f
            val path = Path()
            val waves = 3f
            path.moveTo(0f, center.y)
            var x = 0f
            while (x <= size.width) {
                val angle = (x / size.width) * waves * 2f * Math.PI
                path.lineTo(x, center.y + (amplitude * kotlin.math.sin(angle)).toFloat())
                x += 2f
            }
            drawPath(
                path,
                brush = Brush.horizontalGradient(listOf(cs.primary, cs.tertiary)),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun MdHtmlBlock(node: HtmlBlock, ctx: RenderCtx, modifier: Modifier = Modifier) {
    val literal = node.literal?.trim().orEmpty()
    if (literal.isEmpty() || literal.startsWith("<!--")) return

    // README-style embedded <img> tags are common enough to deserve real rendering.
    val imgTags = Regex("<img[^>]*>", RegexOption.IGNORE_CASE).findAll(literal).toList()
    if (imgTags.isNotEmpty()) {
        FlowRow(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            imgTags.forEach { tag ->
                val src = htmlAttr(tag.value, "src")
                val alt = htmlAttr(tag.value, "alt")
                val width = htmlAttr(tag.value, "width")?.filter { it.isDigit() }?.toIntOrNull()
                if (src != null) {
                    AsyncImage(
                        model = src,
                        contentDescription = alt,
                        modifier = (if (width != null) Modifier.width(width.dp) else Modifier)
                            .heightIn(min = 20.dp, max = 400.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        return
    }

    if (literal.equals("<br>", true) || literal.equals("<br/>", true)) {
        Spacer(modifier.height(10.dp))
        return
    }

    // Anything else: show the raw source, honestly, in a quiet container.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                "HTML",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                literal,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = ctx.styles.monoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun htmlAttr(tag: String, name: String): String? {
    val quoted = Regex("$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(tag)
        ?: Regex("$name\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(tag)
    if (quoted != null) return quoted.groupValues[1]
    return Regex("$name\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
}

// ------------------------------------------------------------ front matter

@Composable
fun FrontMatterCard(frontMatter: List<Pair<String, List<String>>>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Sell, null, tint = cs.tertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "FRONT MATTER",
                    style = MaterialTheme.typography.labelMediumEmphasized.copy(letterSpacing = 1.8.sp),
                    color = cs.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                frontMatter.forEach { (key, values) ->
                    Surface(shape = CircleShape, color = cs.surfaceContainerHighest) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                key,
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                values.joinToString(", ").ifBlank { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------- footnotes

@Composable
fun FootnotesSection(ctx: RenderCtx, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FormatQuote, null, tint = cs.secondary)
            Spacer(Modifier.width(8.dp))
            Text("Footnotes", style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onSurface)
        }
        ctx.doc.footnotes.forEach { footnote ->
            Row {
                Surface(shape = CircleShape, color = cs.secondaryContainer) {
                    Text(
                        "${footnote.number}",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = cs.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val def = footnote.definition
                    if (def == null) {
                        Text(
                            "Missing definition for [^${footnote.label}]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.outline,
                        )
                    } else {
                        def.childList().forEach { MarkdownBlock(it, ctx) }
                    }
                }
            }
        }
    }
}
