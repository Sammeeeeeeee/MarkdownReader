@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalTextApi::class)

package com.sam.markdownreader.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sam.markdownreader.R

private fun grotesk(weight: FontWeight) = Font(
    resId = R.font.space_grotesk,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun mono(weight: FontWeight) = Font(
    resId = R.font.jetbrains_mono,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val GroteskFamily = FontFamily(
    grotesk(FontWeight.Light),
    grotesk(FontWeight.Normal),
    grotesk(FontWeight.Medium),
    grotesk(FontWeight.SemiBold),
    grotesk(FontWeight.Bold),
)

val MonoFamily = FontFamily(
    mono(FontWeight.Normal),
    mono(FontWeight.Medium),
    mono(FontWeight.SemiBold),
    mono(FontWeight.Bold),
)

private fun TextStyle.grotesk(weight: FontWeight? = null): TextStyle =
    copy(fontFamily = GroteskFamily, fontWeight = weight ?: fontWeight)

// Visually emphasized typography: the display/headline/title scale runs on
// Space Grotesk, with the emphasized variants pushed to heavier weights.
val AppTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.grotesk(FontWeight.Bold).copy(letterSpacing = (-1).sp),
        displayMedium = displayMedium.grotesk(FontWeight.Bold).copy(letterSpacing = (-0.5).sp),
        displaySmall = displaySmall.grotesk(FontWeight.Bold),
        displayLargeEmphasized = displayLargeEmphasized.grotesk(FontWeight.Bold).copy(letterSpacing = (-1).sp),
        displayMediumEmphasized = displayMediumEmphasized.grotesk(FontWeight.Bold).copy(letterSpacing = (-0.5).sp),
        displaySmallEmphasized = displaySmallEmphasized.grotesk(FontWeight.Bold),
        headlineLarge = headlineLarge.grotesk(FontWeight.Bold),
        headlineMedium = headlineMedium.grotesk(FontWeight.Bold),
        headlineSmall = headlineSmall.grotesk(FontWeight.SemiBold),
        headlineLargeEmphasized = headlineLargeEmphasized.grotesk(FontWeight.Bold),
        headlineMediumEmphasized = headlineMediumEmphasized.grotesk(FontWeight.Bold),
        headlineSmallEmphasized = headlineSmallEmphasized.grotesk(FontWeight.Bold),
        titleLarge = titleLarge.grotesk(FontWeight.SemiBold),
        titleMedium = titleMedium.grotesk(FontWeight.SemiBold),
        titleSmall = titleSmall.grotesk(FontWeight.Medium),
        titleLargeEmphasized = titleLargeEmphasized.grotesk(FontWeight.Bold),
        titleMediumEmphasized = titleMediumEmphasized.grotesk(FontWeight.Bold),
        titleSmallEmphasized = titleSmallEmphasized.grotesk(FontWeight.SemiBold),
        labelLarge = labelLarge.grotesk(FontWeight.Medium),
        labelMedium = labelMedium.grotesk(FontWeight.Medium),
        labelSmall = labelSmall.grotesk(FontWeight.Medium),
    )
}
