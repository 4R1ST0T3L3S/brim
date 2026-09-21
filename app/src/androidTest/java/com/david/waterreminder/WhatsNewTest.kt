package com.david.waterreminder

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "What's new" popup, end to end on a device — it puts the app in the state a just-updated
 * install is in and launches it, so the dialog can be watched without bumping versionCode,
 * reinstalling, or clearing app data.
 *
 * Run it with the two commands below. Do NOT use `gradlew connectedDebugAndroidTest`: that task
 * uninstalls the app when it finishes, taking the real intake history on the phone with it.
 *
 *   gradlew installDebug installDebugAndroidTest
 *   adb shell am instrument -w -e class com.david.waterreminder.WhatsNewTest \
 *       com.david.waterreminder.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class WhatsNewTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.waterPrefs()
    private val tourPrefs = context.getSharedPreferences("tour_prefs", Context.MODE_PRIVATE)

    private lateinit var saved: Map<String, Any?>
    private var savedTourDone = false

    private val currentEntry: ChangelogEntry?
        get() = context.loadChangelogEntries().firstOrNull { it.versionCode == BuildConfig.VERSION_CODE }

    private fun whatsNewTitle(entry: ChangelogEntry) =
        context.getString(R.string.whats_new_title, entry.versionName)

    /**
     * How long to leave the popup on screen before the activity is torn down, so it can actually
     * be looked at: pass `-e holdMs 10000` to the instrumentation. Zero by default — a test suite
     * should not sit and wait.
     */
    private val holdMs: Long
        get() = InstrumentationRegistry.getArguments().getString("holdMs")?.toLongOrNull() ?: 0L

    /**
     * The test drives the real preference store, which on a dev phone holds real intake — so the
     * three keys it touches are put back afterwards rather than cleared. Nothing else is written.
     */
    @Before
    fun armAnUpdate() {
        saved = mapOf(
            PREF_LAST_SEEN_VERSION_CODE to prefs.all[PREF_LAST_SEEN_VERSION_CODE],
            PREF_PROFILE_SETUP_DONE to prefs.all[PREF_PROFILE_SETUP_DONE]
        )
        savedTourDone = tourPrefs.getBoolean("tour_done", false)

        // The guide tour draws over the whole screen, and onboarding replaces it — neither can be
        // on screen if the popup is going to be asserted against.
        tourPrefs.edit().putBoolean("tour_done", true).commit()
        prefs.edit()
            .putBoolean(PREF_PROFILE_SETUP_DONE, true)
            .putInt(PREF_LAST_SEEN_VERSION_CODE, BuildConfig.VERSION_CODE - 1)
            .commit()
    }

    @After
    fun restore() {
        val edit = prefs.edit()
        saved.forEach { (key, value) ->
            when (value) {
                null -> edit.remove(key)
                is Int -> edit.putInt(key, value)
                is Boolean -> edit.putBoolean(key, value)
            }
        }
        edit.commit()
        tourPrefs.edit().putBoolean("tour_done", savedTourDone).commit()
    }

    /** Guards a release that bumps versionCode and forgets to add the changelog file with it. */
    @Test
    fun thisVersionHasAChangelogEntry() {
        assertNotNull("No changelogs/${BuildConfig.VERSION_CODE}.txt for this versionCode", currentEntry)
    }

    @Test
    fun popup_showsOnTheFirstLaunchAfterAnUpdate() {
        val entry = currentEntry ?: return

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(whatsNewTitle(entry)))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.whats_new_see_all))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))

            if (holdMs > 0) Thread.sleep(holdMs)
        }
    }

    @Test
    fun popup_stampsTheVersionSoItDoesNotShowTwice() {
        val entry = currentEntry ?: return

        ActivityScenario.launch(MainActivity::class.java).use { }
        assertEquals(
            BuildConfig.VERSION_CODE,
            prefs.getInt(PREF_LAST_SEEN_VERSION_CODE, 0)
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(whatsNewTitle(entry))).check(doesNotExist())
        }
    }

    /** A fresh install is not an update, so onboarding stamps the version on its way out. */
    @Test
    fun popup_doesNotShowBeforeOnboardingIsDone() {
        prefs.edit().putBoolean(PREF_PROFILE_SETUP_DONE, false).commit()

        ActivityScenario.launch(MainActivity::class.java).use {
            currentEntry?.let { entry ->
                onView(withText(whatsNewTitle(entry))).check(doesNotExist())
            }
        }
    }

    /**
     * The popup leads with the highlights only. Asserted against the real asset rather than a
     * fixture, so the file shipping in this build is the thing being checked.
     */
    @Test
    fun newestEntry_leadsWithItsHighlights() {
        val newest = context.loadChangelogEntries().first()

        assertEquals(
            "Highlights should be a prefix of the full entry",
            newest.highlights,
            newest.text.lines().take(newest.highlights.lines().size).joinToString("\n")
        )
    }
}
