@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.sam.markdownreader.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.markdownreader.data.DocSource
import com.sam.markdownreader.data.Recents

@Composable
fun HomeScreen(onOpen: (DocSource) -> Unit) {
    val context = LocalContext.current
    val recents = remember { Recents.list(context) }
    val cs = MaterialTheme.colorScheme

    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onOpen(DocSource.FromUri(uri))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        // A little parade of expressive shapes, with a live morphing loader in the middle.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .rotate(-10f)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(cs.primaryContainer)
            )
            LoadingIndicator(Modifier.size(56.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .rotate(12f)
                    .clip(MaterialShapes.Sunny.toShape())
                    .background(cs.tertiaryContainer)
            )
            Box(
                Modifier
                    .size(52.dp)
                    .clip(MaterialShapes.Clover4Leaf.toShape())
                    .background(cs.secondaryContainer)
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Markdown\nReader",
            style = MaterialTheme.typography.displayLargeEmphasized.copy(lineHeight = 60.sp),
            color = cs.onSurface,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "A fast, expressive home for your .md files. Full CommonMark and GitHub-flavored markdown, rendered with Material 3 Expressive.",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { openDocLauncher.launch(arrayOf("*/*")) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Open a file", style = MaterialTheme.typography.titleMediumEmphasized)
        }
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = cs.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Make this the default app for .md files: open one from your file manager and choose \"Always\", or review it in system settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        val pkg = Uri.parse("package:" + context.packageName)
                        val intent = if (Build.VERSION.SDK_INT >= 31) {
                            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                        }
                        runCatching { context.startActivity(intent) }.onFailure {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Set as default in settings")
                }
            }
        }

        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(30.dp))
            Text(
                "Recent files",
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                recents.forEachIndexed { index, entry ->
                    val shape = segmentedShape(index, recents.lastIndex)
                    Surface(
                        onClick = { onOpen(DocSource.FromUri(Uri.parse(entry.uri))) },
                        shape = shape,
                        color = cs.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = MaterialShapes.Cookie4Sided.toShape(),
                                color = cs.primaryContainer,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = cs.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    DateUtils.getRelativeTimeSpanString(entry.openedAt).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = cs.outline,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

private fun segmentedShape(index: Int, lastIndex: Int): RoundedCornerShape {
    val big = 22.dp
    val small = 6.dp
    return when {
        lastIndex == 0 -> RoundedCornerShape(big)
        index == 0 -> RoundedCornerShape(big, big, small, small)
        index == lastIndex -> RoundedCornerShape(small, small, big, big)
        else -> RoundedCornerShape(small)
    }
}
