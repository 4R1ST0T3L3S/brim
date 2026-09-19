package com.david.waterreminder

import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
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
        // Every in-app change the widget draws — the total, the goal, the unit, the quick
        // amounts — is a write to this store, so the widget is redrawn from here rather than
        // from each screen that can cause one.
        //
        // Coalesced to one redraw per frame: the listener fires once per *key*, and a single
        // logIntake commit writes three to five of them, each of which would otherwise cost a
        // getAppWidgetIds binder call plus a full RemoteViews rebuild per placed widget — on
        // the UI thread, while the user is tapping.
        binding.root.removeCallbacks(widgetRedraw)
        binding.root.post(widgetRedraw)
    }

    private val widgetRedraw = Runnable { updateWaterWidgets(this) }

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

        // Reconciles the channels with the stored choice, which a restored backup can change
        // without ever going through the picker. Posting creates the chosen channel too — an
        // alarm can fire into a process this Activity never ran in.
        prefs.reminderSound().selectChannel(this)

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
        // Same repair for the widget: an app update drops the host's cached views, and the
        // listener below only fires on a change — so nothing else here puts the numbers back.
        updateWaterWidgets(this)
    }

    override fun onStop() {
        super.onStop()
        waterPrefs()
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        // A redraw already posted would otherwise run after the listener is gone. The widget
        // is not left stale by dropping it: every path that can still change the day from here
        // redraws the widget itself.
        binding.root.removeCallbacks(widgetRedraw)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
