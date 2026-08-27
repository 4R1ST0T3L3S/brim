package com.david.waterreminder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    /** Every pref type the app actually writes, so a new bucket can't be forgotten silently. */
    private fun populated() = FakePrefs(
        mapOf(
            PREF_UNIT to UNIT_ML,
            "intake_ml" to 1250,
            "personal_goal_ml" to 2400,
            PREF_PROFILE_AGE to 34,
            PREF_PROFILE_WEIGHT_KG to 78.5f,
            PREF_STOP_NOTIFICATIONS_ON_GOAL to true,
            REMINDERS_KEY to mutableSetOf("a", "b"),
        )
    )

    private fun roundTrip(source: FakePrefs, tourDone: Boolean = false): Pair<FakePrefs, Boolean> {
        val json = JSONObject(buildBackupJson(source.all, tourDone).toString())
        val restored = FakePrefs()
        var restoredTour = false
        val editor = restored.edit().clear()
        applyBackupJson(json, editor) { restoredTour = it }
        editor.apply()
        return restored to restoredTour
    }

    @Test
    fun `weight survives the round trip`() {
        val (restored, _) = roundTrip(populated())

        assertEquals(78.5f, restored.getFloat(PREF_PROFILE_WEIGHT_KG, 0f), 0.001f)
    }

    @Test
    fun `age and daily goal survive the round trip`() {
        val (restored, _) = roundTrip(populated())

        assertEquals(34, restored.getInt(PREF_PROFILE_AGE, 0))
        assertEquals(2400, restored.getInt("personal_goal_ml", 0))
    }

    @Test
    fun `every stored value survives the round trip`() {
        val source = populated()

        val (restored, _) = roundTrip(source)

        assertEquals(source.values, restored.values)
    }

    @Test
    fun `tour flag travels separately from the water prefs`() {
        val (restored, restoredTour) = roundTrip(populated(), tourDone = true)

        assertTrue(restoredTour)
        assertFalse(restored.contains(TOUR_DONE_KEY))
    }

    @Test
    fun `a backup written before floats existed still imports`() {
        val legacy = JSONObject(
            """
            {
              "strings":  {"unit": "ml"},
              "ints":     {"personal_goal_ml": 2000, "profile_age": 41},
              "booleans": {"tour_done": true},
              "sets":     {"reminders_v3": ["a"]}
            }
            """.trimIndent()
        )

        val restored = FakePrefs()
        var restoredTour = false
        val editor = restored.edit().clear()
        applyBackupJson(legacy, editor) { restoredTour = it }
        editor.apply()

        assertEquals(2000, restored.getInt("personal_goal_ml", 0))
        assertEquals(41, restored.getInt(PREF_PROFILE_AGE, 0))
        assertEquals(UNIT_ML, restored.getString(PREF_UNIT, null))
        assertEquals(setOf("a"), restored.getStringSet(REMINDERS_KEY, null))
        assertTrue(restoredTour)
        // No weight in the file and none in storage — the default stands, nothing throws.
        assertEquals(0f, restored.getFloat(PREF_PROFILE_WEIGHT_KG, 0f), 0.001f)
    }
}
