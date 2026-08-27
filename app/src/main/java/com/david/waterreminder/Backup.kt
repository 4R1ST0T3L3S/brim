package com.david.waterreminder

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup file format for export/import, kept free of Android plumbing so the round trip can be
 * exercised on the JVM.
 *
 * Preferences are bucketed by type because SharedPreferences is typed on the way back in: reading
 * an Int out of a key that was written as a Float throws at runtime. Every bucket is optional on
 * import — files written before [BUCKET_LONGS] and [BUCKET_FLOATS] existed simply lack them.
 */

private const val BUCKET_STRINGS = "strings"
private const val BUCKET_INTS = "ints"
private const val BUCKET_LONGS = "longs"
private const val BUCKET_FLOATS = "floats"
private const val BUCKET_BOOLEANS = "booleans"
private const val BUCKET_SETS = "sets"

const val TOUR_DONE_KEY = "tour_done"

/**
 * Serialises [all] — normally `prefs.all` — plus the tour flag, which lives in its own prefs file.
 */
fun buildBackupJson(all: Map<String, *>, tourDone: Boolean): JSONObject {
    val strings = JSONObject()
    val ints = JSONObject()
    val longs = JSONObject()
    val floats = JSONObject()
    val booleans = JSONObject()
    val sets = JSONObject()

    all.forEach { (key, value) ->
        when (value) {
            is String  -> strings.put(key, value)
            is Int     -> ints.put(key, value)
            is Long    -> longs.put(key, value)
            is Float   -> floats.put(key, value.toDouble())
            is Boolean -> booleans.put(key, value)
            is Set<*>  -> {
                val arr = JSONArray()
                value.forEach { arr.put(it) }
                sets.put(key, arr)
            }
        }
    }

    booleans.put(TOUR_DONE_KEY, tourDone)

    return JSONObject()
        .put(BUCKET_STRINGS, strings)
        .put(BUCKET_INTS, ints)
        .put(BUCKET_LONGS, longs)
        .put(BUCKET_FLOATS, floats)
        .put(BUCKET_BOOLEANS, booleans)
        .put(BUCKET_SETS, sets)
}

/**
 * Writes [json] into [editor]. The caller owns clearing and applying the edit; [onTourDone] is
 * handed the tour flag because it belongs to a different prefs file.
 */
fun applyBackupJson(
    json: JSONObject,
    editor: SharedPreferences.Editor,
    onTourDone: (Boolean) -> Unit,
) {
    json.optJSONObject(BUCKET_STRINGS)?.let { obj ->
        obj.keys().forEach { key -> editor.putString(key, obj.getString(key)) }
    }
    json.optJSONObject(BUCKET_INTS)?.let { obj ->
        obj.keys().forEach { key -> editor.putInt(key, obj.getInt(key)) }
    }
    json.optJSONObject(BUCKET_LONGS)?.let { obj ->
        obj.keys().forEach { key -> editor.putLong(key, obj.getLong(key)) }
    }
    json.optJSONObject(BUCKET_FLOATS)?.let { obj ->
        obj.keys().forEach { key -> editor.putFloat(key, obj.getDouble(key).toFloat()) }
    }
    json.optJSONObject(BUCKET_BOOLEANS)?.let { obj ->
        obj.keys().forEach { key ->
            if (key == TOUR_DONE_KEY) onTourDone(obj.getBoolean(key))
            else editor.putBoolean(key, obj.getBoolean(key))
        }
    }
    json.optJSONObject(BUCKET_SETS)?.let { obj ->
        obj.keys().forEach { key ->
            val arr = obj.getJSONArray(key)
            val set = (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
            editor.putStringSet(key, set)
        }
    }
}
