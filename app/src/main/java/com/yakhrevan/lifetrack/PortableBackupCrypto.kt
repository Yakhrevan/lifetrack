package com.yakhrevan.lifetrack

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned, passphrase-portable exports. No account credentials or keys are embedded in the file. */
internal object PortableBackupCrypto {
    private const val FORMAT = "lifetrack_encrypted_backup"
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val MAX_ENCRYPTED_CHARS = 16 * 1024 * 1024
    private val random = SecureRandom()
    private val aad = "$FORMAT:$VERSION".toByteArray(StandardCharsets.UTF_8)

    fun isEncrypted(raw: String): Boolean = raw.length <= MAX_ENCRYPTED_CHARS &&
        runCatching { JSONObject(raw).optString("format") == FORMAT }.getOrDefault(false)

    fun encrypt(plaintextBackup: String, passphrase: String): String {
        require(passphrase.length >= 12) { "Use a backup passphrase of at least 12 characters." }
        LifeTrackBackup.decode(plaintextBackup)
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(passphrase, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintextBackup.toByteArray(StandardCharsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT).put("version", VERSION)
            .put("cipher", "AES-256-GCM").put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", ITERATIONS)
            .put("salt", encode(salt)).put("nonce", encode(nonce))
            .put("ciphertext", encode(ciphertext)).toString(2)
    }

    fun decrypt(encryptedBackup: String, passphrase: String): String {
        require(encryptedBackup.length <= MAX_ENCRYPTED_CHARS) { "Encrypted backup is too large." }
        require(passphrase.isNotEmpty()) { "Enter your backup passphrase." }
        val root = JSONObject(encryptedBackup)
        require(root.optString("format") == FORMAT && root.optInt("version") == VERSION) {
            "This is not a supported encrypted LifeTrack backup."
        }
        require(root.optString("cipher") == "AES-256-GCM" &&
            root.optString("kdf") == "PBKDF2WithHmacSHA256" &&
            root.optInt("iterations") == ITERATIONS) { "Unsupported backup encryption parameters." }
        val salt = decode(root.getString("salt"))
        val nonce = decode(root.getString("nonce"))
        val ciphertext = decode(root.getString("ciphertext"))
        require(salt.size == 16 && nonce.size == 12 && ciphertext.size >= 16) {
            "Encrypted backup header is invalid."
        }
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derive(passphrase, salt), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            throw IllegalArgumentException("Wrong backup passphrase or damaged encrypted file.")
        }
        LifeTrackBackup.decode(plaintext)
        return plaintext
    }

    private fun derive(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}

/** Device-bound protected copy for unattended Drive-folder exports; not part of exported backup. */
internal object BackupPassphraseVault {
    private const val PREFS = "lifetrack_backup_vault_v1"
    private const val KEY_ALIAS = "lifetrack_portable_backup_key_v1"
    private const val IV = "iv"
    private const val CIPHERTEXT = "ciphertext"

    fun isConfigured(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .contains(CIPHERTEXT)

    fun remember(context: Context, passphrase: String) {
        require(passphrase.length >= 12) { "Use a backup passphrase of at least 12 characters." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.doFinal(passphrase.toByteArray(StandardCharsets.UTF_8))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit().putString(IV, Base64.getEncoder().encodeToString(cipher.iv))
            .putString(CIPHERTEXT, Base64.getEncoder().encodeToString(bytes)).commit()) {
            "Could not save the protected backup key."
        }
    }

    fun unlock(context: Context): String? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nonce = Base64.getDecoder().decode(prefs.getString(IV, null) ?: return null)
        val ciphertext = Base64.getDecoder().decode(prefs.getString(CIPHERTEXT, null) ?: return null)
        require(nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }.getOrNull()

    fun forget(context: Context) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()) {
            "Could not clear the backup passphrase."
        }
    }

    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
}
