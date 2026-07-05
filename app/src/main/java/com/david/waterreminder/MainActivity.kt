package com.david.waterreminder

import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
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

    private var fabExpanded = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        BackupManager(this).dataChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            prefs.getInt(PREF_THEME_MODE, DEFAULT_THEME_MODE)
        )
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener {
            if (fabExpanded) collapseFabs() else expandFabs()
        }

        binding.fabBell.setOnClickListener {
            collapseFabs()
            navController.navigate(R.id.RemindersFragment)
        }

        // Show FABs only on the water reminder screen
        navController.addOnDestinationChangedListener { _, destination, _ ->
            collapseFabs()
            val onWaterScreen = destination.id == R.id.FirstFragment
            val fabVisibility = if (onWaterScreen) View.VISIBLE else View.GONE
            binding.fab.visibility = fabVisibility
            binding.fabAnalytics.visibility = fabVisibility
            if (onWaterScreen) {
                binding.fabAnalytics.setImageResource(R.drawable.ic_chart)
                binding.fabAnalytics.setOnClickListener {
                    navController.navigate(R.id.AnalyticsFragment)
                }
            }
        }
    }

    private fun expandFabs() {
        if (fabExpanded) return
        fabExpanded = true
        binding.fabGroup.visibility = View.VISIBLE
        binding.fabGroup.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
        binding.fab.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
    }

    private fun collapseFabs() {
        if (!fabExpanded) return
        fabExpanded = false
        binding.fabGroup.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out))
        binding.fabGroup.visibility = View.GONE
        binding.fab.setImageResource(android.R.drawable.ic_input_add)
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
        getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onStop() {
        super.onStop()
        getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
