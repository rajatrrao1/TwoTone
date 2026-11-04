package com.hmj.twotone.coffee

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Bean(
    val id: Long,
    val name: String = "",
    val roaster: String = "",
    val origin: String = "",
    val process: String = "",
    val altitude: String = "",
    val roast: String = "",
    val notes: String = "",
    val link: String = ""
) {
    val title: String get() = name.ifBlank { "unnamed beans" }
}

data class Brew(
    val id: Long,
    val beanId: Long?,
    val time: Long,
    val method: String,
    val waterTempC: Int,
    val grind: Int,
    val doseG: Double,
    val waterMl: Int,
    val rating: Int,
    val note: String
)

val BREW_METHODS = listOf("v60", "aeropress", "french press", "espresso", "moka pot", "chemex", "kalita", "other")

/** Beans and brews, kept in one file inside the app. */
class CoffeeStore(context: Context) {
    private val file = File(context.filesDir, "coffee.json")

    var beans by mutableStateOf<List<Bean>>(emptyList())
        private set
    var brews by mutableStateOf<List<Brew>>(emptyList())
        private set

    init { load() }

    fun bean(id: Long?): Bean? = if (id == null) null else beans.firstOrNull { it.id == id }
    fun brew(id: Long): Brew? = brews.firstOrNull { it.id == id }

    fun saveBean(b: Bean) {
        beans = if (beans.any { it.id == b.id }) beans.map { if (it.id == b.id) b else it } else listOf(b) + beans
        persist()
    }

    fun deleteBean(id: Long) {
        beans = beans.filter { it.id != id }
        persist()
    }

    fun addBrew(b: Brew) {
        brews = listOf(b) + brews
        persist()
    }

    fun deleteBrew(id: Long) {
        brews = brews.filter { it.id != id }
        persist()
    }

    /** Settings for a new brew: the last brew of these beans, else the last brew of anything. */
    fun startingPoint(beanId: Long?): Brew {
        val last = brews.firstOrNull { it.beanId == beanId && beanId != null } ?: brews.firstOrNull()
        val now = System.currentTimeMillis()
        return if (last != null) last.copy(id = now, time = now, beanId = beanId ?: last.beanId, rating = 0, note = "")
        else Brew(now, beanId ?: beans.firstOrNull()?.id, now, "v60", 93, 20, 15.0, 250, 0, "")
    }

    private fun load() {
        try {
            val o = JSONObject(file.readText())
            val bs = o.optJSONArray("beans") ?: JSONArray()
            beans = (0 until bs.length()).map { i ->
                val b = bs.getJSONObject(i)
                Bean(
                    id = b.getLong("id"),
                    name = b.optString("name"),
                    roaster = b.optString("roaster"),
                    origin = b.optString("origin"),
                    process = b.optString("process"),
                    altitude = b.optString("altitude"),
                    roast = b.optString("roast"),
                    notes = b.optString("notes"),
                    link = b.optString("link")
                )
            }
            val rs = o.optJSONArray("brews") ?: JSONArray()
            brews = (0 until rs.length()).map { i ->
                val r = rs.getJSONObject(i)
                Brew(
                    id = r.getLong("id"),
                    beanId = if (r.isNull("beanId")) null else r.getLong("beanId"),
                    time = r.getLong("time"),
                    method = r.optString("method", "v60"),
                    waterTempC = r.optInt("waterTempC", 93),
                    grind = r.optInt("grind", 20),
                    doseG = r.optDouble("doseG", 15.0),
                    waterMl = r.optInt("waterMl", 250),
                    rating = r.optInt("rating", 0),
                    note = r.optString("note")
                )
            }
        } catch (e: Exception) {
            // No file yet, or unreadable: start empty.
        }
    }

    private fun persist() {
        val bs = JSONArray()
        beans.forEach {
            bs.put(
                JSONObject().put("id", it.id).put("name", it.name).put("roaster", it.roaster)
                    .put("origin", it.origin).put("process", it.process).put("altitude", it.altitude)
                    .put("roast", it.roast).put("notes", it.notes).put("link", it.link)
            )
        }
        val rs = JSONArray()
        brews.forEach {
            rs.put(
                JSONObject().put("id", it.id).put("beanId", it.beanId ?: JSONObject.NULL).put("time", it.time)
                    .put("method", it.method).put("waterTempC", it.waterTempC).put("grind", it.grind)
                    .put("doseG", it.doseG).put("waterMl", it.waterMl).put("rating", it.rating).put("note", it.note)
            )
        }
        file.writeText(JSONObject().put("beans", bs).put("brews", rs).toString())
    }
}
