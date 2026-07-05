@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.sam.markdownreader.reader

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sam.markdownreader.data.DocSource
import com.sam.markdownreader.markdown.FootnotesSection
import com.sam.markdownreader.markdown.FrontMatterCard
import com.sam.markdownreader.markdown.MarkdownBlock
import com.sam.markdownreader.markdown.ParsedDocument
import com.sam.markdownreader.markdown.RenderCtx
import com.sam.markdownreader.markdown.TocEntry
import com.sam.markdownreader.markdown.rememberInlineStyleSet
import kotlinx.coroutines.launch
import java.net.URLDecoder
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    source: DocSource,
    onBack: () -> Unit,
    onOpen: (DocSource) -> Unit,
    viewModel: ReaderViewModel = viewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(source) { viewModel.load(context, source) }
    val state = viewModel.state

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var fontZoom by rememberSaveable { mutableFloatStateOf(1f) }
    var showToc by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    val tocSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val ready = state as? ReaderState.Ready
    val doc = ready?.doc
    val fmOffset = if (doc?.frontMatter?.isNotEmpty() == true) 1 else 0
    val footnotesIndex = fmOffset + (doc?.blocks?.size ?: 0)

    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onOpen(DocSource.FromUri(uri))
        }
    }

    fun scrollTo(index: Int) {
        scope.launch { listState.animateScrollToItem(index) }
    }

    val linkHandler = rememberUpdatedState<(String) -> Unit> { dest ->
        when {
            dest.startsWith("#") -> {
                val slug = runCatching { URLDecoder.decode(dest.removePrefix("#"), "UTF-8") }
                    .getOrDefault(dest.removePrefix("#")).lowercase()
                val block = doc?.slugToBlock?.get(slug)
                if (block != null) {
                    scrollTo(fmOffset + block)
                } else {
                    scope.launch { snackbar.showSnackbar("No heading “$slug” in this document") }
                }
            }
            dest.startsWith("footnote:") -> scrollTo(footnotesIndex)
            dest.startsWith("http://") || dest.startsWith("https://") ||
                dest.startsWith("mailto:") || dest.startsWith("tel:") -> {
                runCatching { uriHandler.openUri(dest) }
                    .onFailure { scope.launch { snackbar.showSnackbar("Nothing can open $dest") } }
            }
            else -> scope.launch { snackbar.showSnackbar("Local link: $dest") }
        }
    }
    val onLink = remember { { d: String -> linkHandler.value(d) } }

    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) 0f
            else {
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                ((last + 1f) / info.totalItemsCount).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            ready?.title ?: "Markdown",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    subtitle = {
                        doc?.let {
                            // Only visible while the app bar is expanded (top of page).
                            Text(
                                "${it.wordCount} words · ${it.readMinutes} min read",
                                modifier = Modifier.graphicsLayer {
                                    alpha = (1f - scrollBehavior.state.collapsedFraction * 2f).coerceIn(0f, 1f)
                                },
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        AnimatedVisibility(visible = fontZoom != 1f) {
                            TextButton(onClick = { fontZoom = 1f }) {
                                Text("${(fontZoom * 100).roundToInt()}%  ×")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                LinearWavyProgressIndicator(
                    progress = { progress },
                    // A static wave: expressive shape without the distracting motion.
                    waveSpeed = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                )
            }
        },
        floatingActionButton = {
            if (doc != null) {
                ReaderFabMenu(
                    expanded = fabExpanded,
                    onExpandedChange = { fabExpanded = it },
                    onContents = { showToc = true },
                    onTop = { scrollTo(0) },
                    onOpenFile = { openDocLauncher.launch(arrayOf("*/*")) },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state) {
                is ReaderState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator(Modifier.size(72.dp))
                        Text(
                            "Rendering markdown…",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is ReaderState.Failed -> ErrorPane(
                    message = state.message,
                    onOpenFile = { openDocLauncher.launch(arrayOf("*/*")) },
                )
                is ReaderState.Ready -> ReaderBody(
                    doc = state.doc,
                    listState = listState,
                    fontZoom = fontZoom,
                    onZoom = { fontZoom = it },
                    onLink = onLink,
                    fmOffset = fmOffset,
                )
            }
            // Tapping anywhere outside the open FAB menu closes it.
            if (fabExpanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { fabExpanded = false } }
                )
            }
        }
    }
    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    if (showToc && doc != null) {
        val currentLazyIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        TocSheet(
            doc = doc,
            sheetState = tocSheetState,
            currentLazyIndex = currentLazyIndex,
            fmOffset = fmOffset,
            onSelect = { entry ->
                scope.launch { tocSheetState.hide() }.invokeOnCompletion { showToc = false }
                scrollTo(fmOffset + entry.blockIndex)
            },
            onDismiss = { showToc = false },
        )
    }
}

@Composable
private fun ReaderBody(
    doc: ParsedDocument,
    listState: LazyListState,
    fontZoom: Float,
    onZoom: (Float) -> Unit,
    onLink: (String) -> Unit,
    fmOffset: Int,
) {
    val styles = rememberInlineStyleSet()
    val ctx = remember(doc, styles, onLink) { RenderCtx(styles, doc, onLink) }
    val baseDensity = LocalDensity.current
    val zoomState = rememberUpdatedState(fontZoom)

    Box(
        Modifier
            .fillMaxSize()
            // Two-finger pinch anywhere in the body rescales the text. Single-finger
            // events pass straight through to the list, so scrolling is untouched.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                onZoom((zoomState.value * zoomChange).coerceIn(0.55f, 3f))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontZoom)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (doc.frontMatter.isNotEmpty()) {
                    item(key = "front-matter") { FrontMatterCard(doc.frontMatter) }
                }
                itemsIndexed(doc.blocks, key = { index, _ -> index }) { _, block ->
                    MarkdownBlock(block, ctx)
                }
                if (doc.footnotes.isNotEmpty()) {
                    item(key = "footnotes") { FootnotesSection(ctx) }
                }
            }
        }
    }
}

@Composable
private fun ErrorPane(message: String, onOpenFile: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    "Couldn't open that",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Button(onClick = onOpenFile) { Text("Open a different file") }
            }
        }
    }
}

@Composable
private fun ReaderFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onContents: () -> Unit,
    onTop: () -> Unit,
    onOpenFile: () -> Unit,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
            ) {
                Icon(
                    if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.MenuBook,
                    contentDescription = if (expanded) "Close menu" else "Reader menu",
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { onExpandedChange(false); onContents() },
            icon = { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = null) },
            text = { Text("Contents") },
        )
        FloatingActionButtonMenuItem(
            onClick = { onExpandedChange(false); onTop() },
            icon = { Icon(Icons.Rounded.VerticalAlignTop, contentDescription = null) },
            text = { Text("Back to top") },
        )
        FloatingActionButtonMenuItem(
            onClick = { onExpandedChange(false); onOpenFile() },
            icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
            text = { Text("Open file") },
        )
    }
}

@Composable
private fun TocSheet(
    doc: ParsedDocument,
    sheetState: androidx.compose.material3.SheetState,
    currentLazyIndex: Int,
    fmOffset: Int,
    onSelect: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val current = doc.toc.lastOrNull { fmOffset + it.blockIndex <= currentLazyIndex }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Text("Contents", style = MaterialTheme.typography.headlineSmallEmphasized)
            }
            if (doc.toc.isEmpty()) {
                Text(
                    "No headings in this document.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    itemsIndexed(doc.toc) { _, entry ->
                        val selected = entry == current
                        Surface(
                            onClick = { onSelect(entry) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                        ) {
                            Row(
                                Modifier.padding(
                                    start = (14 + (entry.level - 1) * 16).dp,
                                    end = 16.dp,
                                    top = 11.dp,
                                    bottom = 11.dp,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(if (entry.level == 1) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (entry.level) {
                                                1 -> cs.primary
                                                2 -> cs.secondary
                                                else -> cs.outlineVariant
                                            }
                                        )
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    entry.text,
                                    style = when (entry.level) {
                                        1 -> MaterialTheme.typography.titleMediumEmphasized
                                        2 -> MaterialTheme.typography.bodyLarge
                                        else -> MaterialTheme.typography.bodyMedium
                                    },
                                    color = when {
                                        selected -> cs.onSecondaryContainer
                                        entry.level <= 2 -> cs.onSurface
                                        else -> cs.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
