package com.sam.markdownreader.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object Recents {
    data class Entry(val uri: String, val name: String, val openedAt: Long)

    private const val PREFS = "markdown_reader"
    private const val KEY = "recent_files"
    private const val MAX = 8

    fun list(context: Context): List<Entry> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Entry(obj.getString("uri"), obj.getString("name"), obj.optLong("openedAt"))
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, uri: Uri, name: String) {
        val entry = Entry(uri.toString(), name, System.currentTimeMillis())
        val entries = (listOf(entry) + list(context).filter { it.uri != entry.uri }).take(MAX)
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("uri", it.uri)
                    .put("name", it.name)
                    .put("openedAt", it.openedAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}
