package com.yakhrevan.lifetrack

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** System Intent and persisted-settings checks on an isolated Android emulator. */
@RunWith(AndroidJUnit4::class)
class LifeTrackPhase4AInstrumentedTest {
    @Test fun appLaunchPreservesPersonalRecordsContainingLegacyDemoText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LifeTrackRepository(context)
        val beforeEntries = repository.loadEntries()
        val cleanupPrefs = context.getSharedPreferences("lifetrack_demo_seed_v2", android.content.Context.MODE_PRIVATE)
        val cleanupBefore = cleanupPrefs.getBoolean("release_cleanup_v2", false)
        val tracker = LogEntry("Study", "Personal research", "50", "2026-11-26",
            "These are my DEMO DATA experiment results", id = "phase4c-personal-tracker")
        val task = TaskItem(id = "phase4c-personal-task", title = "My own task",
            description = "DEMO DATA is a name used in this task")
        val note = NoteItem(id = "phase4c-personal-note", title = "My notes",
            content = "I wrote DEMO DATA here myself")
        try {
            assertTrue(cleanupPrefs.edit().putBoolean("release_cleanup_v2", false).commit())
            repository.replaceEntriesDurably(beforeEntries + tracker)
            TaskRepository.saveTask(context, task)
            NoteRepository.saveNote(context, note)
            ActivityScenario.launch(MainActivity::class.java).use {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                // Wait for lifecycle coroutines which used to delete these records.
                Thread.sleep(1000)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            assertTrue(repository.loadEntries().any { it.id == tracker.id })
            assertEquals(task.description, TaskRepository.getTaskById(context, task.id)?.description)
            assertEquals(note.content, NoteRepository.getNoteById(context, note.id)?.content)
        } finally {
            repository.replaceEntriesDurably(beforeEntries)
            TaskRepository.deleteTask(context, task.id)
            NoteRepository.deleteNote(context, note.id)
            assertTrue(cleanupPrefs.edit().putBoolean("release_cleanup_v2", cleanupBefore).commit())
        }
    }

    @Test fun backupPassphraseIsProtectedByAndroidKeystoreAndRestorableOnSameDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = BackupPassphraseVault.unlock(context)
        val secret = "phase4b-device-secret-2026"
        try {
            BackupPassphraseVault.remember(context, secret)
            assertTrue(BackupPassphraseVault.isConfigured(context))
            assertEquals(secret, BackupPassphraseVault.unlock(context))
            val prefs = context.getSharedPreferences("lifetrack_backup_vault_v1", android.content.Context.MODE_PRIVATE)
            assertFalse(prefs.all.values.any { it.toString().contains(secret) })
            val backup = LifeTrackBackup.encode(context, LifeTrackRepository(context).loadEntries())
            assertEquals(backup, PortableBackupCrypto.decrypt(PortableBackupCrypto.encrypt(backup, secret), secret))
        } finally {
            if (previous != null) BackupPassphraseVault.remember(context, previous)
            else BackupPassphraseVault.forget(context)
        }
    }

    @Test fun packageAndVersionSupportInPlaceUpgrade() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.yakhrevan.lifetrack", context.packageName)
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertTrue(androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info) > 4L)
    }

    @Test fun timezoneBroadcastReplacesStaleDelayedReconciliation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val delayed = OneTimeWorkRequestBuilder<ReminderReconciliationWorker>()
            .setInitialDelay(1, TimeUnit.HOURS).build()
        manager.enqueueUniqueWork("lifetrack_reminder_reconciliation",
            ExistingWorkPolicy.REPLACE, delayed).result.get(20, TimeUnit.SECONDS)
        try {
            ReminderReconciliationReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000L
            var rescheduled = false
            while (!rescheduled && android.os.SystemClock.elapsedRealtime() < deadline) {
                val scheduled = manager.getWorkInfosForUniqueWork("lifetrack_reminder_reconciliation")
                    .get(5, TimeUnit.SECONDS)
                rescheduled = scheduled.any {
                    it.id != delayed.id && it.state != androidx.work.WorkInfo.State.CANCELLED
                }
                if (!rescheduled) Thread.sleep(100)
            }
            assertTrue("Clock changes must replace the previous delayed work",
                rescheduled)
        } finally {
            manager.cancelUniqueWork("lifetrack_reminder_reconciliation")
                .result.get(20, TimeUnit.SECONDS)
        }
    }

    @Test fun notificationDeepLinksTargetTheirOwnSavedItem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val taskIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "Tasks")
            putExtra("task_id", "qa-task-42")
        }
        val eventIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "Calendar")
            putExtra("calendar_event_id", "qa-event-13")
        }
        val widgetIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "Water")
        }

        assertEquals("Tasks", parseNotificationTarget(taskIntent)?.page)
        assertEquals("qa-task-42", parseNotificationTarget(taskIntent)?.itemId)
        assertEquals("Calendar", parseNotificationTarget(eventIntent)?.page)
        assertEquals("qa-event-13", parseNotificationTarget(eventIntent)?.itemId)
        assertEquals("Water", parseNotificationTarget(widgetIntent)?.page)
        assertNull(parseNotificationTarget(widgetIntent)?.itemId)
        assertNull(parseNotificationTarget(Intent().putExtra("navigate_to", "Unknown")))
        assertNull(parseNotificationTarget(null))
    }

    @Test fun persistedMasterAndTaskSwitchesActuallyBlockNotifications() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = UserProfileRepository.getSettings(context)
        try {
            UserProfileRepository.saveSettings(context, previous.copy(notificationsEnabled = false,
                notifyTasks = true, notifyCalendar = true))
            assertFalse(NotificationPolicy.canNotify(context, NotificationPolicy.Module.TASKS))
            assertFalse(NotificationPolicy.canNotify(context, NotificationPolicy.Module.CALENDAR))

            UserProfileRepository.saveSettings(context, previous.copy(notificationsEnabled = true,
                notifyTasks = false, notifyCalendar = true, confirmBeforeDelete = false))
            val readBack = UserProfileRepository.getSettings(context)
            assertTrue(readBack.notificationsEnabled)
            assertFalse(readBack.notifyTasks)
            assertTrue(readBack.notifyCalendar)
            assertFalse(readBack.confirmBeforeDelete)
            assertFalse(NotificationPolicy.canNotify(context, NotificationPolicy.Module.TASKS))
        } finally {
            UserProfileRepository.saveSettings(context, previous)
        }
    }
}
