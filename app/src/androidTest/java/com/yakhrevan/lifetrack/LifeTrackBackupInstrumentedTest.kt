package com.yakhrevan.lifetrack

import android.content.Context
import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the isolated CI emulator, never a user's actual phone. */
@RunWith(AndroidJUnit4::class)
class LifeTrackBackupInstrumentedTest {
    @Test fun interruptedRestoreRollsBackEveryModuleAndClearsItsJournal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LifeTrackRepository(context)
        val originalEntries = repository.loadEntries()
        val profile = context.getSharedPreferences("lifetrack_profile", Context.MODE_PRIVATE)
        val key = "phase4c_restore_crash_probe"
        val originalValue = profile.getString(key, null)
        assertFalse(RestoreSafetyJournal.recoverIfNeeded(context))
        try {
            assertTrue(profile.edit().putString(key, "before-crash").commit())
            val rollback = LifeTrackBackup.encode(context, originalEntries)
            RestoreSafetyJournal.stage(context, rollback)
            repository.replaceEntriesDurably(listOf(LogEntry("Study", "incomplete restore", "30",
                "2026-11-26", "interrupted", "phase4c-incomplete")))
            assertTrue(profile.edit().putString(key, "partially restored").commit())

            assertTrue("An interrupted restore must be rolled back on next app start",
                RestoreSafetyJournal.recoverIfNeeded(context))
            assertEquals(originalEntries, repository.loadEntries())
            assertEquals("before-crash", profile.getString(key, null))
            assertFalse("Recovery must be idempotent", RestoreSafetyJournal.recoverIfNeeded(context))
        } finally {
            // The original on-device state must be preserved even if an assertion fails.
            runCatching { RestoreSafetyJournal.recoverIfNeeded(context) }
            repository.replaceEntriesDurably(originalEntries)
            val editor = profile.edit()
            if (originalValue == null) editor.remove(key) else editor.putString(key, originalValue)
            assertTrue(editor.commit())
        }
    }

    @Test fun restoringBackupRecoversChangedSettingsOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("lifetrack_profile", Context.MODE_PRIVATE)
        val key = "phase4a_restore_probe"
        val previous = prefs.getString(key, null)
        try {
            assertTrue(prefs.edit().putString(key, "before-backup").commit())
            val json = LifeTrackBackup.encode(context, LifeTrackRepository(context).loadEntries())
            val decoded = LifeTrackBackup.decode(json)
            assertTrue(prefs.edit().putString(key, "changed-after-backup").commit())

            LifeTrackBackup.restoreSafely(context, decoded)

            assertEquals("before-backup", prefs.getString(key, null))
            assertFalse("A completed restore cannot be rolled back on a later launch",
                RestoreSafetyJournal.recoverIfNeeded(context))
        } finally {
            val editor = prefs.edit()
            if (previous == null) editor.remove(key) else editor.putString(key, previous)
            assertTrue(editor.commit())
        }
    }

    @Test fun allThreeHomeWidgetProvidersAreInstalled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val providers = AppWidgetManager.getInstance(context).installedProviders
            .map { it.provider.className }.toSet()
        assertTrue(providers.any { it.endsWith(".TaskCountWidget") })
        assertTrue(providers.any { it.endsWith(".WaterProgressWidget") })
        assertTrue(providers.any { it.endsWith(".HabitStreakWidget") })
    }

    @Test fun fullBackupIncludesNewCalendarWaterStudyAndAlertModules() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val names = listOf("lifetrack_calendar", "lifetrack_water_v2", "lifetrack_study_v2",
            "lifetrack_notification_history_v1")
        try {
            names.forEach { name ->
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                    .putString("ui_test_probe", name).commit()
            }
            val content = LifeTrackBackup.encode(context, emptyList())
            val backup = LifeTrackBackup.decode(content)
            names.forEach { name ->
                val value = backup.preferences!!.getJSONObject(name).getJSONObject("ui_test_probe")
                assertEquals(name, value.getString("value"))
            }
            assertTrue(content.contains("lifetrack_calendar"))
        } finally {
            names.forEach { name ->
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().remove("ui_test_probe").commit()
            }
        }
    }
}
