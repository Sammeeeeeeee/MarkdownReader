package com.sam.markdownreader.markdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.markdownreader.ui.theme.MonoFamily
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.min

/** Renders LaTeX to a drawable, or null when the input isn't valid TeX. */
fun latexDrawable(latex: String, colorArgb: Int, textSizePx: Float): JLatexMathDrawable? =
    runCatching {
        JLatexMathDrawable.builder(latex)
            .textSize(textSizePx)
            .color(colorArgb)
            .padding(0)
            .build()
    }.getOrNull()?.takeIf { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }

/** Draws [drawable] scaled uniformly to fit the current draw scope. */
fun DrawScope.drawLatex(drawable: JLatexMathDrawable) {
    val s = min(size.width / drawable.intrinsicWidth, size.height / drawable.intrinsicHeight)
    scale(s, s, pivot = Offset.Zero) {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            drawable.draw(canvas.nativeCanvas)
        }
    }
}

/** A `$$…$$` display equation: centered, horizontally scrollable when wide. */
@Composable
fun MdMathBlock(latex: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textSizePx = with(density) { 21.sp.toPx() }
    val colorArgb = cs.onSurface.toArgb()
    val drawable = remember(latex, colorArgb, textSizePx) {
        latexDrawable(latex, colorArgb, textSizePx)
    }
    if (drawable == null) {
        Text(
            latex,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
            color = cs.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val width = with(density) { drawable.intrinsicWidth.toDp() }
    val height = with(density) { drawable.intrinsicHeight.toDp() }
    Box(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width, height)) { drawLatex(drawable) }
    }
}
