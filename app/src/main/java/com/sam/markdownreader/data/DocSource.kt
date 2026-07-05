package com.sam.markdownreader.data

import android.net.Uri

sealed interface DocSource {
    data class FromUri(val uri: Uri) : DocSource
    data class FromText(val text: String, val title: String) : DocSource
}
