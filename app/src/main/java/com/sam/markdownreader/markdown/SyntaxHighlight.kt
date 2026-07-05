package com.sam.markdownreader.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

data class CodePalette(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val annotation: Color,
)

/** A deliberately small, fail-safe syntax highlighter for code fences. */
object SyntaxHighlight {

    private val commonKeywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for",
        "while", "do", "return", "break", "continue", "in", "is", "as", "null", "true",
        "false", "this", "super", "try", "catch", "finally", "throw", "import", "package",
        "private", "public", "protected", "internal", "override", "open", "abstract",
        "companion", "data", "sealed", "suspend", "lateinit", "by", "lazy", "it",
        "def", "elif", "lambda", "pass", "yield", "with", "from", "not", "and", "or",
        "None", "True", "False", "self", "raise", "except", "global", "nonlocal", "async",
        "await", "function", "const", "let", "new", "typeof", "instanceof", "void",
        "undefined", "export", "default", "extends", "implements", "static", "enum",
        "struct", "trait", "impl", "pub", "mut", "match", "loop", "use", "mod", "crate",
        "int", "long", "float", "double", "char", "boolean", "byte", "short", "String",
        "final", "switch", "case", "goto", "sizeof", "typedef", "union", "unsigned",
        "signed", "register", "volatile", "extern", "auto", "namespace", "using",
        "template", "typename", "virtual", "operator", "friend", "constexpr", "nullptr",
        "delete", "where", "select", "insert", "update", "create", "table", "echo",
        "then", "fi", "esac", "done", "local", "declare", "type", "func", "go", "defer",
        "chan", "map", "range", "fallthrough", "select",
    )

    private val stringPattern = Regex(
        "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`[^`\\n]*`"
    )
    private val numberPattern = Regex("\\b(0[xXbB][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFLuU]?)\\b")
    private val wordPattern = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
    private val annotationPattern = Regex("(?<![\\w])[@#]\\[?[A-Za-z_][A-Za-z0-9_.]*\\]?")
    private val lineCommentPattern = Regex("(//|#(?![0-9a-fA-F]{3,8}\\b)|--|;;).*$")
    private val blockCommentPattern = Regex("/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->")

    fun highlight(code: String, language: String?, palette: CodePalette): AnnotatedString {
        return runCatching { doHighlight(code, language, palette) }
            .getOrElse { AnnotatedString(code) }
    }

    private fun doHighlight(code: String, language: String?, palette: CodePalette): AnnotatedString {
        val lang = language?.lowercase()?.trim().orEmpty()
        val styles = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        val taken = BooleanArray(code.length)

        fun claim(range: IntRange, style: SpanStyle) {
            if (range.first < 0 || range.last >= code.length || range.isEmpty()) return
            if ((range.first..range.last).any { taken[it] }) return
            for (i in range) taken[i] = true
            styles += AnnotatedString.Range(style, range.first, range.last + 1)
        }

        val commentStyle = SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
        val stringStyle = SpanStyle(color = palette.string)
        val keywordStyle = SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)
        val numberStyle = SpanStyle(color = palette.number)
        val annotationStyle = SpanStyle(color = palette.annotation)

        // Order matters: comments first, then strings, then everything else.
        blockCommentPattern.findAll(code).forEach { claim(it.range, commentStyle) }
        if (lang !in setOf("html", "xml", "css", "json")) {
            code.lineSequence().fold(0) { offset, line ->
                lineCommentPattern.find(line)?.let { m ->
                    val usesHash = lang in setOf("python", "py", "ruby", "rb", "shell", "sh", "bash", "zsh", "yaml", "yml", "toml", "r", "make", "makefile", "dockerfile", "")
                    val usesSlash = lang !in setOf("python", "py", "ruby", "rb", "shell", "sh", "bash", "zsh", "yaml", "yml", "toml", "r")
                    val token = m.groupValues[1]
                    val ok = when (token) {
                        "#" -> usesHash
                        "//" -> usesSlash
                        "--" -> lang in setOf("sql", "lua", "haskell")
                        ";;" -> lang in setOf("lisp", "clojure", "scheme")
                        else -> false
                    }
                    if (ok) claim((offset + m.range.first)..(offset + m.range.last), commentStyle)
                }
                offset + line.length + 1
            }
        }
        stringPattern.findAll(code).forEach { claim(it.range, stringStyle) }
        annotationPattern.findAll(code).forEach {
            if (lang !in setOf("python", "py", "shell", "sh", "bash", "yaml", "yml", "toml", "ruby", "rb")) {
                claim(it.range, annotationStyle)
            }
        }
        numberPattern.findAll(code).forEach { claim(it.range, numberStyle) }

        if (lang == "json") {
            // Object keys get the keyword color.
            Regex("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)").findAll(code).forEach { claim(it.range, keywordStyle) }
        } else if (lang in setOf("xml", "html")) {
            Regex("</?\\s*([A-Za-z][A-Za-z0-9-]*)").findAll(code).forEach { m ->
                m.groups[1]?.let { claim(it.range, keywordStyle) }
            }
            Regex("\\b[a-zA-Z-]+(?==)").findAll(code).forEach { claim(it.range, annotationStyle) }
        } else {
            wordPattern.findAll(code).forEach {
                if (it.value in commonKeywords) claim(it.range, keywordStyle)
            }
        }

        return buildAnnotatedString {
            append(code)
            styles.forEach { addStyle(it.item, it.start, it.end) }
        }
    }
}
