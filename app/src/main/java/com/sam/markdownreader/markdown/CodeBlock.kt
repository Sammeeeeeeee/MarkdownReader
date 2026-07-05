@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.sam.markdownreader.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.markdownreader.ui.theme.MonoFamily
import kotlinx.coroutines.delay

@Composable
fun MdCodeBlock(code: String, info: String?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val language = info?.trim()?.takeWhile { !it.isWhitespace() }?.takeIf { it.isNotBlank() }
    val palette = CodePalette(
        keyword = cs.primary,
        string = cs.tertiary,
        comment = cs.outline,
        number = cs.secondary,
        annotation = cs.secondary,
    )
    val trimmed = code.trimEnd('\n')
    val highlighted = remember(trimmed, language, cs) {
        SyntaxHighlight.highlight(trimmed, language, palette)
    }

    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerLow,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = cs.primaryContainer) {
                    Text(
                        language ?: "code",
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(trimmed))
                    copied = true
                }) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                highlighted,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MonoFamily,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                ),
                color = cs.onSurface,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 6.dp),
            )
        }
    }
}
