package com.david.waterreminder

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper

class WaterBackupAgent : BackupAgentHelper() {
    override fun onCreate() {
        // Back up water_prefs only. tour_prefs is intentionally excluded
        // so the guide tour always replays after a reinstall.
        addHelper("water", SharedPreferencesBackupHelper(this, "water_prefs"))
    }
}
