package com.yakhrevan.lifetrack

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Pure (Android-free) encoder/decoder for the stored entries JSON so it can be
 * unit tested. The stored field names ("c","t","a","d","n","id","mc") are
 * unchanged, so existing user data stays readable.
 */
internal object EntryCodec {
    data class DecodeResult(
        val entries: List<LogEntry>,
        /** Raw JSON of items that could not be read; kept so nothing is silently lost. */
        val skippedRaw: List<String>,
        val needsRewrite: Boolean
    )

    fun decode(raw: String?): DecodeResult {
        val array = JSONArray(if (raw.isNullOrBlank()) "[]" else raw)
        val entries = mutableListOf<LogEntry>()
        val skipped = mutableListOf<String>()
        var needsRewrite = false
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            if (item == null) {
                skipped += array.opt(i).toString()
                continue
            }
            val id = item.optString("id")
            if (id.isBlank()) needsRewrite = true
            entries += LogEntry(
                category = item.optString("c"),
                title = item.optString("t"),
                amount = item.optString("a"),
                date = item.optString("d"),
                note = item.optString("n"),
                id = id.ifBlank { UUID.randomUUID().toString() },
                moneyCategory = item.optString("mc").ifBlank { "Other" }
            )
        }
        return DecodeResult(entries, skipped, needsRewrite)
    }

    fun encode(entries: List<LogEntry>): String {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id).put("mc", e.moneyCategory).put("c", e.category)
                    .put("t", e.title).put("a", e.amount).put("d", e.date).put("n", e.note)
            )
        }
        return array.toString()
    }

    /**
     * App keeps entries newest-first in memory and saves them in that order.
     * Sorting by ISO date (stable) keeps that order across restarts and also
     * repairs lists that were flipped by the old reverse-on-load behaviour.
     */
    /** Keep the first occurrence of each stable ID; distinct sessions with different IDs remain untouched. */
    fun uniqueById(entries: List<LogEntry>): List<LogEntry> =
        entries.distinctBy { it.id }

    fun newestFirst(entries: List<LogEntry>): List<LogEntry> =
        uniqueById(entries).sortedByDescending { it.date }
}