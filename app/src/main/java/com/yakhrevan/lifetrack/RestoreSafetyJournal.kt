package com.yakhrevan.lifetrack

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Crash-safe rollback for a full restore spanning several SharedPreferences files.
 * The original, validated backup is committed before the first mutation.
 * A journal left by process death is replayed before MainActivity reads any data.
 */
internal object RestoreSafetyJournal {
    private const val FILE_NAME = "lifetrack_pending_restore_rollback_v1.json"

    private fun file(context: Context): AtomicFile =
        AtomicFile(File(context.applicationContext.noBackupFilesDir, FILE_NAME))

    fun stage(context: Context, originalBackup: String) {
        LifeTrackBackup.decode(originalBackup)
        val destination = file(context)
        check(!destination.baseFile.exists()) {
            "An interrupted restore must be recovered before another restore is started."
        }
        val stream = destination.startWrite()
        try {
            stream.write(originalBackup.toByteArray(StandardCharsets.UTF_8))
            destination.finishWrite(stream) // fsync + atomic rename
        } catch (failure: Exception) {
            destination.failWrite(stream)
            throw failure
        }
    }

    /** Called only after every module has been durably restored and reminders rescheduled. */
    fun complete(context: Context) {
        val destination = file(context)
        destination.delete()
        check(!destination.baseFile.exists()) { "Restore recovery file could not be removed." }
    }

    /** Returns true when an interrupted restore was rolled back successfully. */
    fun recoverIfNeeded(context: Context): Boolean {
        val destination = file(context)
        if (!destination.baseFile.exists()) return false
        val originalBackup = destination.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val chars = CharArray(8192)
            while (true) {
                val count = reader.read(chars)
                if (count < 0) break
                require(result.length + count <= 10 * 1024 * 1024) { "Recovery file is too large." }
                result.append(chars, 0, count)
            }
            result.toString()
        }
        val backup = LifeTrackBackup.decode(originalBackup)
        LifeTrackRepository(context).replaceEntriesDurably(backup.entries)
        LifeTrackBackup.restorePreferences(context, backup.preferences)
        ReminderReconciliationScheduler.requestNow(context)
        complete(context)
        return true
    }
}
