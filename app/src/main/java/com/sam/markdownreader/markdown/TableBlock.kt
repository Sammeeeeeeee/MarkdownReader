@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.sam.markdownreader.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow

private data class CellUi(
    val text: AnnotatedString,
    val inline: Map<String, InlineTextContent>,
    val alignment: TableCell.Alignment?,
    val header: Boolean,
)

@Composable
fun MdTable(table: TableBlock, ctx: RenderCtx, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val headStyle = MaterialTheme.typography.titleSmallEmphasized
    val bodyStyle = MaterialTheme.typography.bodyMedium

    val rows: List<List<CellUi>> = remember(table, ctx.styles) {
        val out = mutableListOf<List<CellUi>>()
        table.childList().forEach { section ->
            val header = section is TableHead
            if (section is TableHead || section is TableBody) {
                section.childList().filterIsInstance<TableRow>().forEach { row ->
                    out += row.childList().filterIsInstance<TableCell>().map { cell ->
                        val (text, inline) = buildInlineText(cell, ctx.styles, ctx.doc.footnoteNumbers, ctx.onLink)
                        CellUi(text, inline, cell.alignment, header)
                    }
                }
            }
        }
        out
    }
    if (rows.isEmpty()) return
    val columnCount = rows.maxOf { it.size }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val columnWidths: List<Dp> = remember(rows, density, headStyle, bodyStyle) {
        with(density) {
            val cap = 264.dp.roundToPx()
            (0 until columnCount).map { col ->
                var max = 40.dp.roundToPx()
                rows.forEach { row ->
                    row.getOrNull(col)?.let { cell ->
                        val measured = measurer.measure(
                            text = cell.text,
                            style = if (cell.header) headStyle else bodyStyle,
                            constraints = Constraints(maxWidth = cap),
                        ).size.width
                        if (measured > max) max = measured
                    }
                }
                (max + 26.dp.roundToPx()).toDp()
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            rows.forEachIndexed { rowIndex, row ->
                val background = when {
                    row.firstOrNull()?.header == true -> cs.surfaceContainerHigh
                    rowIndex % 2 == 0 -> Color.Transparent
                    else -> cs.surfaceContainerLow.copy(alpha = 0.7f)
                }
                Row(Modifier.background(background)) {
                    (0 until columnCount).forEach { col ->
                        val cell = row.getOrNull(col)
                        Box(
                            Modifier
                                .width(columnWidths[col])
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            contentAlignment = when (cell?.alignment) {
                                TableCell.Alignment.CENTER -> Alignment.Center
                                TableCell.Alignment.RIGHT -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            },
                        ) {
                            if (cell != null) {
                                Text(
                                    cell.text,
                                    inlineContent = cell.inline,
                                    style = if (cell.header) headStyle else bodyStyle,
                                    color = if (cell.header) cs.onSurface else cs.onSurfaceVariant,
                                    textAlign = when (cell.alignment) {
                                        TableCell.Alignment.CENTER -> TextAlign.Center
                                        TableCell.Alignment.RIGHT -> TextAlign.End
                                        else -> TextAlign.Start
                                    },
                                )
                            }
                        }
                    }
                }
                if (rowIndex == 0 && row.firstOrNull()?.header == true) {
                    HorizontalDivider(color = cs.outlineVariant)
                }
            }
        }
    }
}
