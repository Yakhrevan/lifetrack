package com.yakhrevan.lifetrack

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class PortableBackupCryptoTest {
    private val passphrase = "correct-horse-staple-2026"
    private fun legacyJson(): String = JSONObject()
        .put("format", "lifetrack_backup").put("version", 1)
        .put("entries", JSONArray())
        .put("preferences", JSONObject().put("lifetrack_profile", JSONObject()
            .put("profile_name", JSONObject().put("type", "String").put("value", "Private QA Name"))))
        .toString()

    @Test fun encryptedExportRoundTripsAndHidesSensitiveFields() {
        val plain = legacyJson()
        val sealed = PortableBackupCrypto.encrypt(plain, passphrase)
        assertTrue(PortableBackupCrypto.isEncrypted(sealed))
        assertFalse(sealed.contains("Private QA Name"))
        assertEquals(plain, PortableBackupCrypto.decrypt(sealed, passphrase))
        assertEquals("Private QA Name", LifeTrackBackup.decode(PortableBackupCrypto.decrypt(sealed, passphrase))
            .preferences!!.getJSONObject("lifetrack_profile").getJSONObject("profile_name").getString("value"))
    }

    @Test fun repeatedExportsUseDifferentRandomNoncesAndSalts() {
        val first = PortableBackupCrypto.encrypt(legacyJson(), passphrase)
        val second = PortableBackupCrypto.encrypt(legacyJson(), passphrase)
        assertNotEquals(JSONObject(first).getString("nonce"), JSONObject(second).getString("nonce"))
        assertNotEquals(JSONObject(first).getString("ciphertext"), JSONObject(second).getString("ciphertext"))
    }

    @Test fun wrongPassphraseAndModifiedCiphertextCannotRestore() {
        val raw = PortableBackupCrypto.encrypt(legacyJson(), passphrase)
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.decrypt(raw, "wrong-but-long-passphrase")
        }
        val root = JSONObject(raw)
        val ciphertext = Base64.getDecoder().decode(root.getString("ciphertext"))
        ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()
        root.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.decrypt(root.toString(), passphrase)
        }
    }

    @Test fun malformedEncryptionParametersAndShortPassphraseRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.encrypt(legacyJson(), "short")
        }
        val root = JSONObject(PortableBackupCrypto.encrypt(legacyJson(), passphrase))
        root.put("iterations", 1)
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.decrypt(root.toString(), passphrase)
        }
    }

    @Test fun oldPlaintextJsonRemainsImportableWithoutPassphrase() {
        val legacy = legacyJson()
        assertFalse(PortableBackupCrypto.isEncrypted(legacy))
        assertNotNull(LifeTrackBackup.decode(legacy).preferences)
    }
}
