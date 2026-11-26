package com.yakhrevan.lifetrack

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * User-selected Storage Access Framework folder; works with providers such as
 * Google Drive without collecting account credentials or broad storage access.
 * A locally stored backup does NOT protect against uninstall/device loss.
 */
object OffDeviceBackupStore {
    private const val PREFS = "lifetrack_external_backup_v1"
    private const val FOLDER = "selected_tree_uri"

    fun isConfigured(context: Context): Boolean = folderUri(context) != null

    private fun folderUri(context: Context): Uri? = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(FOLDER, null)?.let(Uri::parse)

    fun selectFolder(context: Context, uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val folder = requireNotNull(DocumentFile.fromTreeUri(context, uri)) { "Folder unavailable" }
        require(folder.isDirectory && folder.canWrite()) { "Choose a writable folder" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(FOLDER, uri.toString()).apply()
    }

    /** Creates and verifies an encrypted, passphrase-portable document at the selected location. */
    fun writeVerified(context: Context, payload: String, atMillis: Long): Result<String> = runCatching {
        LifeTrackBackup.decode(payload) // Never export an invalid backup.
        val passphrase = requireNotNull(BackupPassphraseVault.unlock(context)) {
            "Set a backup passphrase in Backup & Sync before exporting to Drive/Files."
        }
        val encrypted = PortableBackupCrypto.encrypt(payload, passphrase)
        val uri = requireNotNull(folderUri(context)) { "Select a backup folder first" }
        val folder = requireNotNull(DocumentFile.fromTreeUri(context, uri)) { "Backup folder inaccessible" }
        require(folder.isDirectory && folder.canWrite()) { "Backup folder is no longer writable" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(atMillis))
        val document = requireNotNull(folder.createFile("application/json", "LifeTrack-$stamp.ltb.json")) {
            "Cannot create backup document"
        }
        try {
            context.contentResolver.openOutputStream(document.uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(encrypted)
            } ?: error("Cannot write backup document")
            val readBack = context.contentResolver.openInputStream(document.uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("Cannot verify backup document")
            require(sha256(readBack) == sha256(encrypted)) { "Backup verification failed" }
            require(PortableBackupCrypto.decrypt(readBack, passphrase) == payload) {
                "Encrypted backup verification failed"
            }
            document.uri.toString()
        } catch (e: Exception) {
            document.delete() // Only the incomplete document created in this call.
            throw e
        }
    }

    private fun sha256(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
