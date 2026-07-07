package com.sam.markdownreader.markdown

import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.beta.Scanner

// ---------------------------------------------------------------- custom nodes

/** `H~2~O` */
class Subscript : CustomNode()

/** `19^th^` */
class Superscript : CustomNode()

/** `$E=mc^2$` (inline) or `$$…$$` (display). */
class MathNode(val latex: String, val display: Boolean) : CustomNode()

/** `<details><summary>…</summary>…</details>`, grouped back together after parsing. */
class DetailsBlock(val summary: Paragraph?, val initiallyOpen: Boolean) : CustomBlock()

/** Markdown-extra definition lists: a term line followed by `: definition` lines. */
class DefinitionList : CustomBlock()

/** One term inside a [DefinitionList]; children are the term's inline nodes. */
class DefinitionTerm : CustomBlock()

/** One definition inside a [DefinitionList]; children are the definition's inline nodes. */
class DefinitionDetail : CustomBlock()

// -------------------------------------------------- subscript / superscript

/**
 * Pandoc-style `~subscript~` / `^superscript^`: the content must be non-empty
 * and contain no whitespace, so `approx ~5 items` and `2^10 power` stay literal.
 * Strikethrough is configured to require `~~`, which keeps both syntaxes working.
 */
private class SubSupParser(private val marker: Char) : InlineContentParser {
    override fun tryParse(state: InlineParserState): ParsedInline? {
        val scanner = state.scanner()
        scanner.next() // opening marker
        if (scanner.peek() == marker) return ParsedInline.none() // ~~ belongs to strikethrough
        val contentStart = scanner.position()
        while (scanner.hasNext()) {
            val c = scanner.peek()
            if (c == marker) {
                val content = scanner.getSource(contentStart, scanner.position()).content
                if (content.isEmpty()) return ParsedInline.none()
                scanner.next() // closing marker
                val node = if (marker == '^') Superscript() else Subscript()
                node.appendChild(Text(content))
                return ParsedInline.of(node, scanner.position())
            }
            if (c.isWhitespace()) return ParsedInline.none()
            scanner.next()
        }
        return ParsedInline.none()
    }
}

class SubscriptParserFactory : InlineContentParserFactory {
    override fun getTriggerCharacters(): Set<Char> = setOf('~')
    override fun create(): InlineContentParser = SubSupParser('~')
}

class SuperscriptParserFactory : InlineContentParserFactory {
    override fun getTriggerCharacters(): Set<Char> = setOf('^')
    override fun create(): InlineContentParser = SubSupParser('^')
}

// ------------------------------------------------------------------- math

/**
 * `$…$` and `$$…$$` TeX math. An opener must be followed by non-whitespace and
 * a closer preceded by non-whitespace, so plain prices (`$5 and $10`) survive.
 */
private class MathParser : InlineContentParser {
    override fun tryParse(state: InlineParserState): ParsedInline? {
        val scanner = state.scanner()
        scanner.next() // opening $
        val display = scanner.peek() == '$'
        if (display) scanner.next()
        if (!display && (scanner.peek() == Scanner.END || scanner.peek().isWhitespace())) {
            return ParsedInline.none()
        }
        val contentStart = scanner.position()
        var prev = ' '
        while (scanner.hasNext()) {
            val c = scanner.peek()
            if (c == '$') {
                if (display) {
                    val contentEnd = scanner.position()
                    scanner.next()
                    if (scanner.peek() == '$') {
                        scanner.next()
                        val latex = scanner.getSource(contentStart, contentEnd).content.trim()
                        if (latex.isEmpty()) return ParsedInline.none()
                        return ParsedInline.of(MathNode(latex, true), scanner.position())
                    }
                    prev = c
                    continue
                } else {
                    if (prev.isWhitespace()) return ParsedInline.none()
                    val latex = scanner.getSource(contentStart, scanner.position()).content
                    if (latex.isBlank()) return ParsedInline.none()
                    scanner.next()
                    return ParsedInline.of(MathNode(latex, false), scanner.position())
                }
            }
            prev = if (c == Scanner.END) ' ' else c
            scanner.next()
        }
        return ParsedInline.none()
    }
}

class MathParserFactory : InlineContentParserFactory {
    override fun getTriggerCharacters(): Set<Char> = setOf('$')
    override fun create(): InlineContentParser = MathParser()
}

// ------------------------------------------------------- paragraph rewriting

/** Splits a paragraph's inline children into visual lines (soft/hard break boundaries). */
private fun Paragraph.inlineLines(): List<List<Node>> {
    val lines = mutableListOf<MutableList<Node>>(mutableListOf())
    childList().forEach { child ->
        when (child) {
            is SoftLineBreak, is HardLineBreak -> lines += mutableListOf<Node>()
            else -> lines.last() += child
        }
    }
    return lines.filter { it.isNotEmpty() }
}

private val abbreviationRegex = Regex("""^\*\[(.+?)]:\s*(.+)$""")

/**
 * `*[HTML]: HyperText Markup Language` paragraphs. Returns the extracted
 * abbreviations, or null when the paragraph is not an abbreviation block.
 */
fun extractAbbreviations(paragraph: Paragraph): Map<String, String>? {
    val lines = paragraph.inlineLines()
    if (lines.isEmpty()) return null
    val found = linkedMapOf<String, String>()
    for (line in lines) {
        if (line.any { it !is Text }) return null
        val text = line.joinToString("") { (it as Text).literal.orEmpty() }
        val match = abbreviationRegex.find(text) ?: return null
        found[match.groupValues[1]] = match.groupValues[2].trim()
    }
    return found.takeIf { it.isNotEmpty() }
}

/**
 * Turns `Term` / `: definition` paragraphs into a [DefinitionList],
 * or returns null when the paragraph doesn't look like one.
 */
fun buildDefinitionList(paragraph: Paragraph): DefinitionList? {
    val lines = paragraph.inlineLines()
    if (lines.size < 2) return null
    fun isDefinition(line: List<Node>): Boolean {
        val first = line.firstOrNull() as? Text ?: return false
        val literal = first.literal.orEmpty()
        return literal == ":" || (literal.startsWith(":") && literal.length > 1 && literal[1].isWhitespace())
    }
    if (isDefinition(lines.first()) || lines.none(::isDefinition)) return null

    val list = DefinitionList()
    lines.forEach { line ->
        val item: Node = if (isDefinition(line)) {
            (line.first() as Text).literal = (line.first() as Text).literal.orEmpty()
                .removePrefix(":").trimStart()
            DefinitionDetail()
        } else {
            DefinitionTerm()
        }
        line.forEach { node -> node.unlink(); item.appendChild(node) }
        list.appendChild(item)
    }
    return list
}

// ------------------------------------------------------------ details blocks

private val detailsOpenRegex = Regex("""<details\b[^>]*>""", RegexOption.IGNORE_CASE)
private val detailsCloseRegex = Regex("""</details\s*>""", RegexOption.IGNORE_CASE)
private val summaryRegex = Regex("""<summary\b[^>]*>([\s\S]*?)</summary\s*>""", RegexOption.IGNORE_CASE)

/**
 * commonmark splits `<details>` sections that contain blank lines into an
 * opening [HtmlBlock], plain markdown blocks, and a closing [HtmlBlock].
 * This pass stitches them back into a single [DetailsBlock] whose children
 * are real markdown nodes, so the body renders (and collapses) natively.
 */
fun groupDetailsBlocks(blocks: List<Node>, parseMarkdown: (String) -> List<Node>): List<Node> {
    val out = mutableListOf<Node>()
    var i = 0
    while (i < blocks.size) {
        val node = blocks[i]
        val literal = (node as? HtmlBlock)?.literal?.trim().orEmpty()
        val open = detailsOpenRegex.find(literal)
        if (node !is HtmlBlock || open == null || literal.indexOf(open.value) != 0) {
            out += node
            i++
            continue
        }

        val initiallyOpen = Regex("""\bopen\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(open.value.removePrefix("<details"))
        val summaryMatch = summaryRegex.find(literal)
        val summary = summaryMatch?.groupValues?.get(1)?.trim()
            ?.let { parseMarkdown(it).firstOrNull() as? Paragraph }

        var depth = detailsOpenRegex.findAll(literal).count() - detailsCloseRegex.findAll(literal).count()
        val bodyChildren = mutableListOf<Node>()

        // Markdown-parse whatever sits inside the opening HTML chunk itself.
        var inner = literal.removeRange(open.range)
        if (summaryMatch != null) inner = inner.replaceFirst(summaryMatch.value, "")
        if (depth <= 0) inner = inner.substringBeforeLast("</details", inner).removeSuffix(">")
        inner = inner.replace(detailsCloseRegex, "").trim()
        if (inner.isNotEmpty()) bodyChildren += parseMarkdown(inner)

        i++
        while (depth > 0 && i < blocks.size) {
            val next = blocks[i]
            if (next is HtmlBlock) {
                val nextLiteral = next.literal.orEmpty()
                depth += detailsOpenRegex.findAll(nextLiteral).count()
                depth -= detailsCloseRegex.findAll(nextLiteral).count()
                if (depth <= 0) {
                    val before = nextLiteral.substringBefore("</details").trim()
                    if (before.isNotEmpty()) bodyChildren += parseMarkdown(before)
                    i++
                    break
                }
            }
            bodyChildren += next
            i++
        }

        val details = DetailsBlock(summary, initiallyOpen)
        groupDetailsBlocks(bodyChildren, parseMarkdown).forEach { child ->
            child.unlink()
            details.appendChild(child)
        }
        out += details
    }
    return out
}
