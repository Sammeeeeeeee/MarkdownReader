package com.sam.markdownreader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sam.markdownreader.data.DocSource
import com.sam.markdownreader.home.HomeScreen
import com.sam.markdownreader.reader.ReaderScreen

@Composable
fun MarkdownReaderApp(
    source: DocSource?,
    onOpen: (DocSource) -> Unit,
    onClose: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = source,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith (fadeOut() + scaleOut(targetScale = 1.05f))
            },
            label = "screens",
        ) { current ->
            if (current == null) {
                HomeScreen(onOpen = onOpen)
            } else {
                BackHandler(onBack = onClose)
                ReaderScreen(source = current, onBack = onClose, onOpen = onOpen)
            }
        }
    }
}
