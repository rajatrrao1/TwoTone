package com.hmj.twotone.notes

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(val id: Long, val text: String, val updated: Long) {
    val title: String
        get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "empty note"
}

/** Plain-text notes, kept in one file inside the app. Newest first. */
class NotesStore(context: Context) {
    private val file = File(context.filesDir, "notes.json")

    var notes by mutableStateOf(load())
        private set

    fun get(id: Long): Note? = notes.firstOrNull { it.id == id }

    fun create(): Long {
        val id = System.currentTimeMillis()
        notes = listOf(Note(id, "", id)) + notes
        persist()
        return id
    }

    fun update(id: Long, text: String) {
        val old = get(id) ?: return
        if (old.text == text) return
        notes = listOf(old.copy(text = text, updated = System.currentTimeMillis())) + notes.filter { it.id != id }
        persist()
    }

    fun delete(id: Long) {
        notes = notes.filter { it.id != id }
        persist()
    }

    private fun load(): List<Note> = try {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Note(o.getLong("id"), o.getString("text"), o.getLong("updated"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist() {
        val arr = JSONArray()
        notes.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("updated", it.updated)) }
        file.writeText(arr.toString())
    }
}
