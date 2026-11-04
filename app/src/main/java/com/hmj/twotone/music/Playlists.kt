package com.hmj.twotone.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(val id: Long, val name: String, val trackIds: List<Long>)

/** Your playlists, kept in one file inside the app. Songs are remembered by their id. */
object Playlists {
    var all by mutableStateOf<List<Playlist>>(emptyList())
        private set
    private var file: File? = null

    fun load(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "playlists.json")
        file = f
        all = try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("tracks")
                Playlist(o.getLong("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) })
            }
        } catch (e: Exception) { emptyList() }
    }

    fun byId(id: Long) = all.firstOrNull { it.id == id }

    fun create(name: String, first: Track? = null): Playlist {
        val p = Playlist(System.currentTimeMillis(), name.ifBlank { "playlist ${all.size + 1}" }, listOfNotNull(first?.id))
        all = all + p
        persist()
        return p
    }

    fun add(id: Long, t: Track) = update(id) { if (t.id in it.trackIds) it else it.copy(trackIds = it.trackIds + t.id) }
    fun remove(id: Long, t: Track) = update(id) { it.copy(trackIds = it.trackIds - t.id) }
    fun rename(id: Long, name: String) = update(id) { it.copy(name = name.ifBlank { it.name }) }
    fun delete(id: Long) { all = all.filter { it.id != id }; persist() }

    /** The playlist's songs that are still on the phone, in playlist order. */
    fun tracksOf(p: Playlist, library: List<Track>): List<Track> {
        val byId = library.associateBy { it.id }
        return p.trackIds.mapNotNull { byId[it] }
    }

    private fun update(id: Long, f: (Playlist) -> Playlist) {
        all = all.map { if (it.id == id) f(it) else it }
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        all.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", JSONArray(p.trackIds)))
        }
        try { file?.writeText(arr.toString()) } catch (e: Exception) { }
    }
}
