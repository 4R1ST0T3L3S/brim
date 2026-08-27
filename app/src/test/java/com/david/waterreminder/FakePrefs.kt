package com.david.waterreminder

import android.content.SharedPreferences

/**
 * In-memory [SharedPreferences] so the day's bookkeeping can be tested on the JVM — the project
 * has no Robolectric, and this is a dozen methods against two interfaces.
 *
 * Writes are batched until apply/commit like the real thing, so a test would catch a helper that
 * reads a key back inside its own edit block and expects to see it.
 */
class FakePrefs(initial: Map<String, Any> = emptyMap()) : SharedPreferences {

    val values: MutableMap<String, Any> = initial.toMutableMap()

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any>()
        private val removed = mutableSetOf<String>()
        private var clearAll = false

        private fun put(key: String, value: Any) = apply {
            pending[key] = value
            removed -= key
        }

        override fun putString(key: String, value: String?) =
            if (value == null) remove(key) else put(key, value)

        override fun putStringSet(key: String, values: MutableSet<String>?) =
            if (values == null) remove(key) else put(key, values)

        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String) = apply { removed += key; pending -= key }

        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            if (clearAll) values.clear()
            removed.forEach { values -= it }
            values.putAll(pending)
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
