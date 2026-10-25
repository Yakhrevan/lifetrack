package com.yakhrevan.lifetrack

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LifeTrackBackupTest {
    @Test
    fun restoresLegacyEntriesOnlyBackup() {
        val entry = LogEntry(
            category = "Study",
            title = "ROS 2",
            amount = "45",
            date = "2026-10-19",
            note = "Navigation notes",
            id = "entry-1",
            moneyCategory = "Other"
        )

        val decoded = LifeTrackBackup.decode(EntryCodec.encode(listOf(entry)))

        assertEquals(listOf(entry), decoded.entries)
        assertNull(decoded.preferences)
    }

    @Test
    fun decodesVersionedBackupWithPreferences() {
        val entry = LogEntry(
            category = "Water",
            title = "Water",
            amount = "500",
            date = "2026-10-19",
            note = "Quick add",
            id = "entry-2",
            moneyCategory = "Other"
        )
        val backup = JSONObject()
            .put("format", "lifetrack_backup")
            .put("version", 1)
            .put("entries", JSONArray(EntryCodec.encode(listOf(entry))))
            .put("preferences", JSONObject().put("lifetrack_profile", JSONObject()))

        val decoded = LifeTrackBackup.decode(backup.toString())

        assertEquals(listOf(entry), decoded.entries)
        assertEquals(true, decoded.preferences?.has("lifetrack_profile"))
    }

    @Test
    fun rejectsUnsupportedBackupVersion() {
        val backup = JSONObject()
            .put("format", "lifetrack_backup")
            .put("version", 99)
            .put("entries", JSONArray())
            .put("preferences", JSONObject())
        assertThrows(IllegalArgumentException::class.java) {
            LifeTrackBackup.decode(backup.toString())
        }
    }

    @Test
    fun rejectsMissingEntriesAndPreferences() {
        val missingEntries = JSONObject()
            .put("format", "lifetrack_backup")
            .put("version", 1)
            .put("preferences", JSONObject())
        assertThrows(IllegalStateException::class.java) {
            LifeTrackBackup.decode(missingEntries.toString())
        }
        val missingPreferences = JSONObject()
            .put("format", "lifetrack_backup")
            .put("version", 1)
            .put("entries", JSONArray())
        assertThrows(IllegalStateException::class.java) {
            LifeTrackBackup.decode(missingPreferences.toString())
        }
    }

    @Test
    fun rejectsEmptyAndOversizedBackupFiles() {
        assertThrows(IllegalArgumentException::class.java) { LifeTrackBackup.decode("  ") }
        assertThrows(IllegalArgumentException::class.java) {
            LifeTrackBackup.decode("x".repeat(10 * 1024 * 1024 + 1))
        }
    }

    @Test
    fun rejectsWrongTypesForRequiredFields() {
        val wrongEntries = JSONObject()
            .put("format", "lifetrack_backup")
            .put("version", 1)
            .put("entries", JSONObject())
            .put("preferences", JSONObject())
        assertThrows(IllegalStateException::class.java) { LifeTrackBackup.decode(wrongEntries.toString()) }

        val wrongPreferences = JSONObject()
            .put("format", "lifetrack_backup")
            .put("version", 1)
            .put("entries", JSONArray())
            .put("preferences", JSONArray())
        assertThrows(IllegalStateException::class.java) { LifeTrackBackup.decode(wrongPreferences.toString()) }
    }

    @Test
    fun rejectsMalformedPreferenceValuesBeforeRestore() {
        val malformed = JSONObject()
            .put("format", "lifetrack_backup")
            .put("version", 1)
            .put("entries", JSONArray())
            .put("preferences", JSONObject().put(
                "lifetrack_profile",
                JSONObject().put("profile_name", JSONObject().put("type", "Unknown").put("value", "Test"))
            ))
        assertThrows(IllegalArgumentException::class.java) { LifeTrackBackup.decode(malformed.toString()) }
    }

    @Test
    fun rejectsMalformedPreferenceSections() {
        val malformed = JSONObject()
            .put("format", "lifetrack_backup")
            .put("version", 1)
            .put("entries", JSONArray())
            .put("preferences", JSONObject().put("lifetrack_profile", "not-an-object"))
        assertThrows(IllegalArgumentException::class.java) { LifeTrackBackup.decode(malformed.toString()) }
    }

    @Test
    fun rejectsPreferenceValueWhoseRuntimeTypeDoesNotMatchItsDeclaration() {
        val malformed = JSONObject()
            .put("format", "lifetrack_backup")
            .put("version", 1)
            .put("entries", JSONArray())
            .put("preferences", JSONObject().put(
                "lifetrack_profile",
                JSONObject().put("water_target", JSONObject().put("type", "Int").put("value", "2500"))
            ))
        assertThrows(IllegalArgumentException::class.java) { LifeTrackBackup.decode(malformed.toString()) }
    }

    @Test
    fun rejectsUnrecognizedBackupFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            LifeTrackBackup.decode("""{"format":"other","version":1,"entries":[]}""")
        }
    }
}
