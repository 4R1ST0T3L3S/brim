package com.david.waterreminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.david.waterreminder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        BackupManager(this).dataChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = waterPrefs()
        val themeMode = prefs.getInt(PREF_THEME_MODE, DEFAULT_THEME_MODE)
        setTheme(themeStyleRes(themeMode))
        AppCompatDelegate.setDefaultNightMode(nightModeFor(themeMode))
        super.onCreate(savedInstanceState)

        Reminder.migrateLegacyHourly(prefs)
        // Briefly written to the main store before it moved to transientPrefs, so it would
        // otherwise ride along in exported backups. Removable once no install has it.
        if (prefs.contains(PREF_LAST_REMINDER_POST_AT)) {
            prefs.edit().remove(PREF_LAST_REMINDER_POST_AT).apply()
        }

        createNotificationChannel()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        if (!prefs.getBoolean(PREF_PROFILE_SETUP_DONE, false)) {
            navController.navigate(R.id.ProfileSetupFragment)
        }

        binding.fabReminders.setOnClickListener {
            navController.navigate(R.id.RemindersFragment)
        }

        // Show FABs only on the water reminder screen
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Deferred to the next frame so this settles together with the fragment
            // transaction's own layout pass, instead of a frame ahead of it — otherwise
            // the toolbar/FABs can flash into view while the outgoing screen is still visible,
            // visibly pushing its content down before the new screen actually swaps in.
            binding.root.post {
                val onWaterScreen = destination.id == R.id.FirstFragment
                val fabVisibility = if (onWaterScreen) View.VISIBLE else View.GONE
                binding.fabReminders.visibility = fabVisibility
                binding.fabAnalytics.visibility = fabVisibility
                // Full-screen onboarding: no toolbar/menu until setup is finished.
                val onboarding = destination.id == R.id.ProfileSetupFragment || destination.id == R.id.ReminderSetupFragment
                binding.toolbar.visibility = if (onboarding) View.GONE else View.VISIBLE
                if (onWaterScreen) {
                    binding.fabAnalytics.setImageResource(R.drawable.ic_chart)
                    binding.fabAnalytics.setOnClickListener {
                        navController.navigate(R.id.AnalyticsFragment)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val navOptions = androidx.navigation.navOptions {
            launchSingleTop = true
            popUpTo(R.id.FirstFragment) { inclusive = false }
        }
        return when (item.itemId) {
R.id.action_help -> {
                findNavController(R.id.nav_host_fragment_content_main)
                    .navigate(R.id.HelpFragment, null, navOptions)
                true
            }
            R.id.action_settings -> {
                findNavController(R.id.nav_host_fragment_content_main)
                    .navigate(R.id.SettingsFragment, null, navOptions)
                true
            }
            R.id.action_about -> {
                findNavController(R.id.nav_host_fragment_content_main)
                    .navigate(R.id.AboutFragment, null, navOptions)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()
        waterPrefs()
            .registerOnSharedPreferenceChangeListener(prefsListener)
        // Repair the chain: a reminder the system dropped used to stay silent for good, and the
        // idle alarm has no chain of its own at all. Both are idempotent — see nextIdleFireTime
        // — which is what makes onStart safe to hang them on.
        rescheduleAllAlarms(this)
    }

    override fun onStop() {
        super.onStop()
        waterPrefs()
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = getString(R.string.notification_channel_description) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
