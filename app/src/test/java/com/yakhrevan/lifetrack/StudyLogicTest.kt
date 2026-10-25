package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test

class StudyLogicTest {
    @Test fun addsTrimmedProjectAndRejectsInvalid() {
        val l = ProjectLogic.add(emptyList(), "  Maths  ")!!
        assertEquals("Maths", l.single().name)
        assertNull(ProjectLogic.add(l, "maths"))        // duplicate, case-insensitive
        assertNull(ProjectLogic.add(l, "   "))          // blank
        assertNull(ProjectLogic.add(l, "x".repeat(41))) // too long
    }

    @Test fun renameArchiveDelete() {
        var l = ProjectLogic.add(emptyList(), "A")!!
        l = ProjectLogic.add(l, "B")!!
        val a = l[0].id; val b = l[1].id
        assertNull(ProjectLogic.rename(l, a, "b"))
        assertEquals("C", ProjectLogic.rename(l, a, "C")!![0].name)
        assertNotNull(ProjectLogic.rename(l, a, "A")) // same name for itself is fine
        assertTrue(ProjectLogic.setArchived(l, b, true)[1].archived)
        assertEquals(1, ProjectLogic.delete(l, a).size)
    }

    @Test fun projectCodecRoundTripAndSkipsBadLines() {
        val l = listOf(Project("1", "Robotics, ROS 2"), Project("2", "Art→Sketch", true))
        assertEquals(l, ProjectLogic.decode(ProjectLogic.encode(l)))
        assertTrue(ProjectLogic.decode("garbage\n\t\t").isEmpty())
        assertTrue(ProjectLogic.decode(null).isEmpty())
    }

    @Test fun customDurationsAreFree() {
        assertEquals(35, DurationLogic.parse("35"))
        assertEquals(37, DurationLogic.parse(" 37 "))
        assertEquals(49, DurationLogic.parse("49"))
        assertNull(DurationLogic.parse("0"))
        assertNull(DurationLogic.parse("721"))
        assertNull(DurationLogic.parse("abc"))
    }

    @Test fun presetsSortedDistinctAndBounded() {
        var c = emptyList<Int>()
        c = DurationLogic.addPreset(c, 49); c = DurationLogic.addPreset(c, 35); c = DurationLogic.addPreset(c, 37)
        c = DurationLogic.addPreset(c, 35); c = DurationLogic.addPreset(c, 25); c = DurationLogic.addPreset(c, 0)
        assertEquals(listOf(35, 37, 49), c)
        assertEquals(c, DurationLogic.decodePresets(DurationLogic.encodePresets(c)))
        assertEquals("1h 30m", DurationLogic.format(90))
    }

    @Test fun stopwatchSurvivesPauseResume() {
        var s = TimerState(TimerMode.STOPWATCH).start(1_000)
        assertEquals(60_000, s.elapsedMs(61_000))
        s = s.pause(61_000)
        assertEquals(60_000, s.elapsedMs(999_999)) // frozen while paused
        s = s.resume(100_000)
        assertEquals(90_000, s.elapsedMs(130_000))
        assertFalse(s.isDone(10_000_000))
    }

    @Test fun countdownFinishesAndCapsCountedTime() {
        val s = TimerState(TimerMode.COUNTDOWN, targetMin = 35).start(0)
        assertEquals(1_000L, s.remainingMs(35 * 60_000L - 1_000))
        assertFalse(s.isDone(35 * 60_000L - 1_000))
        assertTrue(s.isDone(35 * 60_000L))
        assertEquals(35 * 60_000L, s.countedMs(5 * 3_600_000L)) // away for hours, still 35 min
        assertEquals(35, TimerState.minutesToLog(s.countedMs(5 * 3_600_000L)))
    }

    @Test fun minutesToLogRoundsToNearest() {
        assertEquals(0, TimerState.minutesToLog(29_000))
        assertEquals(1, TimerState.minutesToLog(30_000))
        assertEquals(37, TimerState.minutesToLog(37 * 60_000L + 10_000))
    }

    @Test fun resetClearsEverything() {
        val s = TimerState(TimerMode.POMODORO, 25).start(0).reset()
        assertFalse(s.active); assertFalse(s.running); assertEquals(0, s.elapsedMs(5_000))
    }

    @Test fun timerDoesNotGainTimeWhenWallClockMovesBackwards() {
        val running = TimerState(TimerMode.STOPWATCH).start(10_000)
        assertEquals(0L, running.elapsedMs(9_000))
        val paused = running.pause(15_000)
        assertEquals(5_000L, paused.elapsedMs(1_000))
        assertEquals(paused, paused.resume(20_000).pause(20_000))
    }

    @Test fun invalidAndDuplicateStoredPresetsAreSanitized() {
        assertEquals(listOf(35, 49), DurationLogic.decodePresets("35,35,0,721,49,abc,-2"))
        assertEquals(12, DurationLogic.decodePresets((1..20).joinToString(",") { (70 + it).toString() }).size)
    }

    @Test fun clockFormats() {
        assertEquals("00:05", TimerState.clock(5_000))
        assertEquals("37:00", TimerState.clock(37 * 60_000L))
        assertEquals("1:05:09", TimerState.clock(3_909_000))
    }
}