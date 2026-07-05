package com.sam.markdownreader.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.front.matter.YamlFrontMatterBlock
import org.commonmark.ext.front.matter.YamlFrontMatterNode
import org.commonmark.ext.gfm.alerts.AlertsExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.image.attributes.ImageAttributesExtension
import org.commonmark.ext.ins.InsExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.CustomNode
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.text.TextContentRenderer

data class TocEntry(
    val level: Int,
    val text: String,
    val slug: String,
    val blockIndex: Int,
)

data class FootnoteInfo(
    val number: Int,
    val label: String,
    val definition: FootnoteDefinition?,
)

data class ParsedDocument(
    val blocks: List<Node>,
    val toc: List<TocEntry>,
    val frontMatter: List<Pair<String, List<String>>>,
    val footnotes: List<FootnoteInfo>,
    val footnoteNumbers: Map<String, Int>,
    val slugToBlock: Map<String, Int>,
    val wordCount: Int,
    val firstHeading: String?,
) {
    val readMinutes: Int get() = (wordCount / 215).coerceAtLeast(1)
}

/** Extracts the plain text inside any node (for headings, TOC, alt text…). */
fun Node.innerText(): String {
    val sb = StringBuilder()
    accept(object : AbstractVisitor() {
        override fun visit(text: Text) { sb.append(text.literal) }
        override fun visit(code: Code) { sb.append(code.literal) }
        override fun visit(softLineBreak: SoftLineBreak) { sb.append(' ') }
        override fun visit(hardLineBreak: HardLineBreak) { sb.append(' ') }
    })
    return sb.toString()
}

object MarkdownParser {

    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AlertsExtension.create(),
        AutolinkExtension.create(),
        FootnotesExtension.create(),
        TaskListItemsExtension.create(),
        InsExtension.create(),
        YamlFrontMatterExtension.create(),
        ImageAttributesExtension.create(),
    )

    private val parser: Parser = Parser.builder().extensions(extensions).build()
    private val textRenderer = TextContentRenderer.builder().extensions(extensions).build()

    fun parse(markdown: String): ParsedDocument {
        val document = parser.parse(markdown)

        val blocks = mutableListOf<Node>()
        val frontMatter = mutableListOf<Pair<String, List<String>>>()
        val definitions = linkedMapOf<String, FootnoteDefinition>()

        var child = document.firstChild
        while (child != null) {
            val next = child.next
            when (child) {
                is YamlFrontMatterBlock -> {
                    var entry = child.firstChild
                    while (entry != null) {
                        if (entry is YamlFrontMatterNode) {
                            frontMatter += entry.key to entry.values.toList()
                        }
                        entry = entry.next
                    }
                }
                is FootnoteDefinition -> definitions[child.label.lowercase()] = child
                else -> blocks += child
            }
            child = next
        }

        // Number footnotes by order of first reference, like GitHub does.
        val numbers = linkedMapOf<String, Int>()
        document.accept(object : AbstractVisitor() {
            override fun visit(customNode: CustomNode) {
                if (customNode is FootnoteReference) {
                    numbers.getOrPut(customNode.label.lowercase()) { numbers.size + 1 }
                }
                visitChildren(customNode)
            }
        })
        val footnotes = numbers.map { (label, number) ->
            FootnoteInfo(number, label, definitions[label])
        }

        // Table of contents with GitHub-style slugs.
        val slugger = Slugger()
        val toc = mutableListOf<TocEntry>()
        val slugToBlock = mutableMapOf<String, Int>()
        blocks.forEachIndexed { index, node ->
            if (node is Heading) {
                val text = node.innerText().trim()
                val slug = slugger.slug(text)
                toc += TocEntry(node.level, text, slug, index)
                slugToBlock[slug] = index
            }
        }

        val plain = runCatching { textRenderer.render(document) }.getOrDefault(markdown)
        val wordCount = plain.split(Regex("\\s+")).count { it.isNotBlank() }

        return ParsedDocument(
            blocks = blocks,
            toc = toc,
            frontMatter = frontMatter,
            footnotes = footnotes,
            footnoteNumbers = numbers,
            slugToBlock = slugToBlock,
            wordCount = wordCount,
            firstHeading = toc.firstOrNull { it.level == 1 }?.text ?: toc.firstOrNull()?.text,
        )
    }
}

/** GitHub's heading anchor algorithm: lowercase, strip punctuation, hyphens for spaces, -n dedupe. */
class Slugger {
    private val seen = mutableMapOf<String, Int>()

    fun slug(text: String): String {
        val base = text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
        val count = seen.getOrDefault(base, 0)
        seen[base] = count + 1
        return if (count == 0) base else "$base-$count"
    }
}
