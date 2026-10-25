package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test

class EntryCodecTest {
    @Test fun roundTripKeepsAllFields() {
        val e = LogEntry("Expense", "Tea", "20", "2026-10-19", "note", "id1", "Food & groceries")
        val back = EntryCodec.decode(EntryCodec.encode(listOf(e))).entries
        assertEquals(listOf(e), back)
    }

    @Test fun oldEntriesWithoutIdOrMoneyCategoryAreBackfilled() {
        val r = EntryCodec.decode("""[{"c":"Water","t":"Glass","a":"250","d":"2026-10-01","n":""}]""")
        assertTrue(r.needsRewrite)
        assertTrue(r.entries[0].id.isNotBlank())
        assertEquals("Other", r.entries[0].moneyCategory)
        assertEquals("250", r.entries[0].amount)
    }

    @Test fun badItemIsSkippedNotFatalAndKept() {
        val r = EntryCodec.decode("""[{"c":"Water","a":"250","d":"2026-10-01","id":"x"}, 42]""")
        assertEquals(1, r.entries.size)
        assertEquals(listOf("42"), r.skippedRaw)
    }

    @Test fun blankOrNullStoreDecodesToEmpty() {
        assertTrue(EntryCodec.decode(null).entries.isEmpty())
        assertTrue(EntryCodec.decode("  ").entries.isEmpty())
    }

    @Test(expected = Exception::class) fun corruptJsonThrowsSoRepositoryCanBackItUp() {
        EntryCodec.decode("{not json")
    }

    @Test fun orderStaysNewestFirstAfterSaveLoadCycles() {
        val a = LogEntry("Water", "a", "1", "2026-10-19", "", "1")
        val b = LogEntry("Water", "b", "1", "2026-10-18", "", "2")
        var list = listOf(a, b)
        repeat(3) { list = EntryCodec.newestFirst(EntryCodec.decode(EntryCodec.encode(list)).entries) }
        assertEquals(listOf("1", "2"), list.map { it.id })
    }

    @Test fun sameDayEntriesKeepRelativeOrder() {
        val n = LogEntry("Water", "new", "1", "2026-10-19", "", "n")
        val o = LogEntry("Water", "old", "1", "2026-10-19", "", "o")
        assertEquals(listOf("n", "o"), EntryCodec.newestFirst(listOf(n, o)).map { it.id })
    }
}

class EntryCodecDuplicateIdTest {
    @org.junit.Test
    fun duplicateIdsKeepFirstButDifferentSessionsRemain() {
        val first = LogEntry("Study", "ROS 2", "25", "2026-10-19", "", "same-id")
        val duplicate = first.copy(amount = "50")
        val distinct = first.copy(id = "different-id", amount = "25")
        val result = EntryCodec.uniqueById(listOf(first, duplicate, distinct))
        org.junit.Assert.assertEquals(listOf(first, distinct), result)
    }
}
