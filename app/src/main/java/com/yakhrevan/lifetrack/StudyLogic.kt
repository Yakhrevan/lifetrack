package com.yakhrevan.lifetrack

import java.util.UUID

/** User-defined study subject / creative project. Never predefined; the user owns the list. */
internal data class Project(val id: String, val name: String, val archived: Boolean = false)

/** Pure, Android-free logic for projects and timers so it can be unit tested. */
internal object ProjectLogic {
    const val MAX_NAME = 40

    /** Returns the cleaned name, or null if empty / too long. */
    fun cleanName(raw: String): String? =
        raw.replace(Regex("[\\t\\r\\n]+"), " ").trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME }

    fun isDuplicate(list: List<Project>, name: String, exceptId: String? = null): Boolean =
        list.any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    /** Returns null when the name is invalid or already used. */
    fun add(list: List<Project>, rawName: String): List<Project>? {
        val name = cleanName(rawName) ?: return null
        if (isDuplicate(list, name)) return null
        return list + Project(UUID.randomUUID().toString(), name)
    }

    fun rename(list: List<Project>, id: String, rawName: String): List<Project>? {
        val name = cleanName(rawName) ?: return null
        if (isDuplicate(list, name, id)) return null
        return list.map { if (it.id == id) it.copy(name = name) else it }
    }

    fun setArchived(list: List<Project>, id: String, archived: Boolean): List<Project> =
        list.map { if (it.id == id) it.copy(archived = archived) else it }

    /** Removes only the list item; saved sessions keep their title text, so history is preserved. */
    fun delete(list: List<Project>, id: String): List<Project> = list.filterNot { it.id == id }

    // Line format: id \t archived(0/1) \t name
    fun encode(list: List<Project>): String =
        list.joinToString("\n") { "${it.id}\t${if (it.archived) 1 else 0}\t${it.name}" }

    fun decode(raw: String?): List<Project> =
        raw.orEmpty().lines().mapNotNull { line ->
            val p = line.split("\t", limit = 3)
            if (p.size == 3 && p[0].isNotBlank() && p[2].isNotBlank()) Project(p[0], p[2], p[1] == "1") else null
        }
}

/** Custom timer lengths, e.g. 35, 37 or 49 minutes. */
internal object DurationLogic {
    const val MIN = 1
    const val MAX = 720
    const val MAX_CUSTOM_PRESETS = 12
    val BUILT_IN = listOf(15, 25, 30, 45, 60)

    fun parse(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it in MIN..MAX }

    fun encodePresets(list: List<Int>): String = list.joinToString(",")

    fun decodePresets(raw: String?): List<Int> =
        raw.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in MIN..MAX }.distinct().take(MAX_CUSTOM_PRESETS)

    /** Adds a custom preset; ignores invalid, built-in duplicates and overflow. */
    fun addPreset(custom: List<Int>, minutes: Int): List<Int> =
        if (minutes !in MIN..MAX || minutes in BUILT_IN || minutes in custom || custom.size >= MAX_CUSTOM_PRESETS) custom
        else (custom + minutes).sorted()

    fun format(min: Int): String = when {
        min < 60 -> "${min}m"
        min % 60 == 0 -> "${min / 60}h"
        else -> "${min / 60}h ${min % 60}m"
    }
}

internal enum class TimerMode { STOPWATCH, COUNTDOWN, POMODORO }

/**
 * Timer state based on wall-clock timestamps (not a ticking counter), so it stays
 * correct across navigation, screen-off and process restarts when persisted.
 */
internal data class TimerState(
    val mode: TimerMode = TimerMode.STOPWATCH,
    val targetMin: Int = 25,
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val accMs: Long = 0L,
    val onBreak: Boolean = false,
    val active: Boolean = false
) {
    fun elapsedMs(now: Long): Long =
        (accMs + if (running) (now - startedAt).coerceAtLeast(0) else 0L).coerceAtLeast(0)

    private val targetMs get() = targetMin * 60_000L
    private val hasTarget get() = mode != TimerMode.STOPWATCH || onBreak

    fun remainingMs(now: Long): Long = if (!hasTarget) 0 else (targetMs - elapsedMs(now)).coerceAtLeast(0)
    fun isDone(now: Long): Boolean = active && hasTarget && elapsedMs(now) >= targetMs

    /** Counted time never exceeds the target for countdown/pomodoro. */
    fun countedMs(now: Long): Long = if (hasTarget) minOf(elapsedMs(now), targetMs) else elapsedMs(now)

    fun start(now: Long) = copy(active = true, running = true, startedAt = now, accMs = 0)
    fun pause(now: Long) = if (running) copy(running = false, accMs = elapsedMs(now), startedAt = 0) else this
    fun resume(now: Long) = if (active && !running) copy(running = true, startedAt = now) else this
    fun reset() = copy(active = false, running = false, startedAt = 0, accMs = 0, onBreak = false)

    companion object {
        /** Rounds to the nearest minute; under 30 s logs nothing. */
        fun minutesToLog(ms: Long): Int = ((ms + 30_000L) / 60_000L).toInt()

        fun clock(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
            return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
            else String.format(java.util.Locale.US, "%02d:%02d", m, sec)
        }
    }
}