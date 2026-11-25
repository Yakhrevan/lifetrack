package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test

class NotificationHistoryTest {
    @Test fun encodedRecordsRoundtripWithSnoozeState() {
        val records = listOf(ReminderRecord("Tasks", "id-1", "Pay rent", "SNOOZED", 123L, 345L),
            ReminderRecord("Calendar", "event-2", "Meet", "DELIVERED", 456L))
        assertEquals(records, ReminderRecordCodec.decode(ReminderRecordCodec.encode(records)))
    }

    @Test fun malformedOrEmptyHistoryIsSafe() {
        assertTrue(ReminderRecordCodec.decode(null).isEmpty())
        assertTrue(ReminderRecordCodec.decode("not json").isEmpty())
        assertTrue(ReminderRecordCodec.decode("[{}]").isEmpty())
    }
}
