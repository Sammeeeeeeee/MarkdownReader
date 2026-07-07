package com.sam.markdownreader

import com.sam.markdownreader.markdown.DefinitionDetail
import com.sam.markdownreader.markdown.DefinitionList
import com.sam.markdownreader.markdown.DefinitionTerm
import com.sam.markdownreader.markdown.DetailsBlock
import com.sam.markdownreader.markdown.MarkdownParser
import com.sam.markdownreader.markdown.MathNode
import com.sam.markdownreader.markdown.Subscript
import com.sam.markdownreader.markdown.Superscript
import com.sam.markdownreader.markdown.childList
import com.sam.markdownreader.markdown.innerText
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomNode
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private inline fun <reified T : Node> collect(blocks: List<Node>): List<T> {
        val out = mutableListOf<T>()
        blocks.forEach { block ->
            block.accept(object : AbstractVisitor() {
                override fun visit(customNode: CustomNode) {
                    if (customNode is T) out += customNode
                    visitChildren(customNode)
                }
            })
            if (block is T) out += block
        }
        return out
    }

    @Test
    fun `subscript and superscript parse while strikethrough survives`() {
        val doc = MarkdownParser.parse("Water is H~2~O in the 19^th^ century, ~~gone~~.")
        val subs = collect<Subscript>(doc.blocks)
        val sups = collect<Superscript>(doc.blocks)
        assertEquals(1, subs.size)
        assertEquals("2", subs[0].innerText())
        assertEquals(1, sups.size)
        assertEquals("th", sups[0].innerText())
        assertEquals(1, collect<Strikethrough>(doc.blocks).size)
    }

    @Test
    fun `literal tildes and carets stay literal`() {
        val doc = MarkdownParser.parse("Approx ~5 items and 2^10 power.")
        assertTrue(collect<Subscript>(doc.blocks).isEmpty())
        assertTrue(collect<Superscript>(doc.blocks).isEmpty())
        assertTrue(doc.searchTexts[0].contains("~5"))
        assertTrue(doc.searchTexts[0].contains("2^10"))
    }

    @Test
    fun `inline and display math parse, prices do not`() {
        val doc = MarkdownParser.parse(
            "Einstein said \$E=mc^2\$.\n\n\$\$\n\\frac{a}{b}\n\$\$\n\nCosts \$5 and \$10.",
        )
        val math = collect<MathNode>(doc.blocks)
        assertEquals(2, math.size)
        assertEquals("E=mc^2", math[0].latex)
        assertFalse(math[0].display)
        assertEquals("\\frac{a}{b}", math[1].latex)
        assertTrue(math[1].display)
        assertTrue(doc.searchTexts.last().contains("Costs \$5 and \$10"))
    }

    @Test
    fun `footnotes are numbered and definitions found`() {
        val doc = MarkdownParser.parse("A claim[^1] and another[^note].\n\n[^1]: First.\n[^note]: Second.")
        assertEquals(2, doc.footnotes.size)
        assertEquals(1, doc.footnotes[0].number)
        assertNotNull(doc.footnotes[0].definition)
        assertEquals(2, doc.footnoteNumbers.size)
        // The footnotes section is searchable as the extra final entry.
        assertEquals(doc.blocks.size + 1, doc.searchTexts.size)
        assertTrue(doc.searchTexts.last().contains("First."))
    }

    @Test
    fun `definition lists are recognised`() {
        val doc = MarkdownParser.parse("Term A\n: Definition one\n: Definition two\n\nPlain paragraph.")
        val list = doc.blocks.filterIsInstance<DefinitionList>().single()
        val children = list.childList()
        assertTrue(children[0] is DefinitionTerm)
        assertEquals("Term A", children[0].innerText())
        assertTrue(children[1] is DefinitionDetail)
        assertEquals("Definition one", children[1].innerText())
        assertEquals("Definition two", children[2].innerText())
        // The trailing plain paragraph is untouched.
        assertTrue(doc.blocks.last() is Paragraph)
    }

    @Test
    fun `abbreviations are extracted and removed from the body`() {
        val doc = MarkdownParser.parse("The HTML spec.\n\n*[HTML]: HyperText Markup Language")
        assertEquals(mapOf("HTML" to "HyperText Markup Language"), doc.abbreviations)
        assertEquals(1, doc.blocks.size)
    }

    @Test
    fun `details blocks group summary and split markdown body`() {
        val doc = MarkdownParser.parse(
            "<details open>\n<summary>Click **me**</summary>\n\nHidden *markdown* body.\n\n- one\n- two\n\n</details>\n\nAfter.",
        )
        val details = doc.blocks.filterIsInstance<DetailsBlock>().single()
        assertTrue(details.initiallyOpen)
        assertNotNull(details.summary)
        assertEquals("Click me", details.summary!!.innerText())
        val body = details.childList()
        assertEquals(2, body.size) // paragraph + list
        assertEquals("Hidden markdown body.", body[0].innerText())
        assertEquals("After.", doc.blocks.last().innerText())
    }

    @Test
    fun `single-block details with no blank lines still renders a body`() {
        val doc = MarkdownParser.parse("<details>\n<summary>Spoiler</summary>\nPlain body text.\n</details>")
        val details = doc.blocks.filterIsInstance<DetailsBlock>().single()
        assertFalse(details.initiallyOpen)
        assertEquals("Spoiler", details.summary!!.innerText())
        assertEquals("Plain body text.", details.childList().single().innerText())
    }

    @Test
    fun `search texts cover code blocks`() {
        val doc = MarkdownParser.parse("Intro.\n\n```kotlin\nval needle = 42\n```")
        assertTrue(doc.searchTexts[1].contains("needle"))
    }

    @Test
    fun `toc slugs and word counts still work`() {
        val doc = MarkdownParser.parse("# Hello World\n\nSome body text here.")
        assertEquals("hello-world", doc.toc.single().slug)
        assertTrue(doc.wordCount >= 5)
    }
}
