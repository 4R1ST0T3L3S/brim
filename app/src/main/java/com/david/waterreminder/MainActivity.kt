package com.david.waterreminder

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
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

        binding.fabWater.setOnClickListener {
            collapseFabs()
            navController.popBackStack(R.id.FirstFragment, false)
        }

        // Collapse expanded panel and swap icon/action based on which screen is active
        navController.addOnDestinationChangedListener { _, destination, _ ->
            collapseFabs()
            when (destination.id) {
                R.id.AnalyticsFragment -> {
                    binding.fabAnalytics.setImageResource(R.drawable.ic_water_drop)
                    binding.fabAnalytics.setOnClickListener {
                        navController.popBackStack(R.id.FirstFragment, false)
                    }
                }
                else -> {
                    binding.fabAnalytics.setImageResource(R.drawable.ic_chart)
                    binding.fabAnalytics.setOnClickListener {
                        navController.navigate(R.id.AnalyticsFragment)
                    }
                }
            }
        }
    }

    private fun expandFabs() {
        fabExpanded = true
        binding.fabGroup.visibility = View.VISIBLE
        binding.fabGroup.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
        binding.fab.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
    }

    private fun collapseFabs() {
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
        return when (item.itemId) {
            R.id.action_settings -> {
                findNavController(R.id.nav_host_fragment_content_main)
                    .navigate(R.id.SettingsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
