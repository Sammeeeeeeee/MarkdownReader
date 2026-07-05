package com.sam.markdownreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import com.sam.markdownreader.data.DocSource
import com.sam.markdownreader.ui.theme.MarkdownReaderTheme

class MainActivity : ComponentActivity() {

    private val source = mutableStateOf<DocSource?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        source.value = extractSource(intent)
        setContent {
            MarkdownReaderTheme {
                val current by source
                MarkdownReaderApp(
                    source = current,
                    onOpen = { source.value = it },
                    onClose = { source.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSource(intent)?.let { source.value = it }
    }

    private fun extractSource(intent: Intent?): DocSource? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data?.let { DocSource.FromUri(it) }
        Intent.ACTION_SEND -> {
            val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            stream?.let { DocSource.FromUri(it) }
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    DocSource.FromText(it, intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "Shared markdown")
                }
        }
        else -> null
    }
}
