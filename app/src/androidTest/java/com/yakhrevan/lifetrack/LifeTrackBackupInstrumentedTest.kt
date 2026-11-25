package com.yakhrevan.lifetrack

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the isolated CI emulator, never a user's actual phone. */
@RunWith(AndroidJUnit4::class)
class LifeTrackBackupInstrumentedTest {
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
