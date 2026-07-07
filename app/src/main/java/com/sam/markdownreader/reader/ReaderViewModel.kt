package com.sam.markdownreader.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sam.markdownreader.data.DocSource
import com.sam.markdownreader.data.Recents
import com.sam.markdownreader.markdown.MarkdownParser
import com.sam.markdownreader.markdown.ParsedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val doc: ParsedDocument, val title: String) : ReaderState
    data class Failed(val message: String) : ReaderState
}

class ReaderViewModel : ViewModel() {

    var state: ReaderState by mutableStateOf(ReaderState.Loading)
        private set

    private var loadedSource: DocSource? = null

    fun load(context: Context, source: DocSource) {
        if (loadedSource == source && state is ReaderState.Ready) return
        loadedSource = source
        state = ReaderState.Loading
        val appContext = context.applicationContext
        viewModelScope.launch {
            state = withContext(Dispatchers.Default) {
                try {
                    val (text, title) = read(appContext, source)
                    ReaderState.Ready(MarkdownParser.parse(text), title)
                } catch (t: Throwable) {
                    ReaderState.Failed(t.message ?: "Couldn't open this file")
                }
            }
        }
    }

    private fun read(context: Context, source: DocSource): Pair<String, String> = when (source) {
        is DocSource.FromText -> source.text to source.title
        is DocSource.FromUri -> {
            val name = displayName(context, source.uri)
            requireMarkdownFile(context, source.uri, name)
            val bytes = context.contentResolver.openInputStream(source.uri)
                ?.use { it.readBytes() }
                ?: throw IllegalStateException("The document provider returned nothing")
            requireTextContent(bytes, name)
            runCatching { Recents.add(context, source.uri, name) }
            String(bytes, Charsets.UTF_8) to name
        }
    }

    /** Extensions this app is willing to render. Everything else is rejected up front. */
    private val markdownExtensions = setOf(
        "md", "markdown", "mdown", "mkd", "mkdn", "mdx", "txt", "text",
    )
    private val markdownMimeTypes = setOf(
        "text/markdown", "text/x-markdown", "text/plain",
    )

    private fun requireMarkdownFile(context: Context, uri: Uri, name: String) {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in markdownExtensions) return
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase()
        if (mime != null && (mime in markdownMimeTypes || mime.startsWith("text/"))) return
        val label = if (extension.isEmpty()) name else ".$extension"
        throw IllegalArgumentException(
            "\"$label\" isn't a markdown file. This app opens .md, .markdown and plain-text documents."
        )
    }

    /** Cheap binary sniff: a genuine text file never contains NUL bytes. */
    private fun requireTextContent(bytes: ByteArray, name: String) {
        val probe = minOf(bytes.size, 8192)
        for (i in 0 until probe) {
            if (bytes[i] == 0.toByte()) {
                throw IllegalArgumentException(
                    "\"$name\" looks like a binary file, not markdown."
                )
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)?.let { return it }
                        }
                    }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "Markdown"
    }
}
