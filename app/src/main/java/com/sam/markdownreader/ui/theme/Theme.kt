@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.sam.markdownreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@Composable
fun MarkdownReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) VibrantDarkColors else VibrantLightColors,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        content = content,
    )
}
