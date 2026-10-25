package com.yakhrevan.lifetrack

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VoiceIntentParserTest {

    @Test
    fun testExpenseVoiceParsingPetrol() = runBlocking {
        val plan = LocalVoiceIntentParser.parse("Spent 200 for petrol")
        assertEquals(1, plan.actions.size)
        val action = plan.actions[0]
        assertEquals(VoiceModule.EXPENSE, action.module)
        assertEquals(VoiceActionType.CREATE, action.actionType)
        assertEquals(200.0, action.amount ?: 0.0, 0.001)
        assertEquals("Fuel", action.category)
        assertTrue(action.title.contains("Petrol", ignoreCase = true))
        assertFalse("Should auto-execute with quick undo", action.requiresConfirmation)
    }

    @Test
    fun testReminderTaskVoiceParsing() = runBlocking {
        val plan = LocalVoiceIntentParser.parse("Remind me to study at 7 PM")
        assertEquals(1, plan.actions.size)
        val action = plan.actions[0]
        assertEquals(VoiceModule.TASK, action.module)
        assertEquals(VoiceActionType.CREATE, action.actionType)
        assertEquals("Study", action.title)
        assertEquals("19:00", action.time)
        assertFalse("Should auto-execute with quick undo", action.requiresConfirmation)
    }

    @Test
    fun testWaterVoiceParsingTamil() = runBlocking {
        val plan = LocalVoiceIntentParser.parse("500ml water குடிச்சேன்")
        assertEquals(1, plan.actions.size)
        val action = plan.actions[0]
        assertEquals(VoiceModule.WATER, action.module)
        assertEquals(VoiceActionType.CREATE, action.actionType)
        assertEquals(500.0, action.amount ?: 0.0, 0.001)
        assertEquals("ml", action.unit)
    }

    @Test
    fun testWaterVoiceParsingTanglish() = runBlocking {
        val plan = LocalVoiceIntentParser.parse("500ml thanni kudichen")
        assertEquals(1, plan.actions.size)
        val action = plan.actions[0]
        assertEquals(VoiceModule.WATER, action.module)
        assertEquals(500.0, action.amount ?: 0.0, 0.001)
    }
}
