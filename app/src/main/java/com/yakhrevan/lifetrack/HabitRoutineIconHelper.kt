package com.yakhrevan.lifetrack

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object HabitRoutineIconHelper {
    data class NamedIcon(val id: String, val label: String, val icon: ImageVector)

    val availableIcons: List<NamedIcon> = listOf(
        NamedIcon("check", "Task", Icons.Default.CheckCircle),
        NamedIcon("water", "Water", Icons.Default.WaterDrop),
        NamedIcon("fitness", "Exercise", Icons.Default.FitnessCenter),
        NamedIcon("book", "Reading", Icons.Default.MenuBook),
        NamedIcon("spa", "Meditation", Icons.Default.Spa),
        NamedIcon("moon", "Sleep", Icons.Default.Bedtime),
        NamedIcon("sun", "Morning", Icons.Default.WbSunny),
        NamedIcon("run", "Running", Icons.Default.DirectionsRun),
        NamedIcon("school", "Study", Icons.Default.School),
        NamedIcon("timer", "Focus", Icons.Default.Timer),
        NamedIcon("food", "Nutrition", Icons.Default.Restaurant),
        NamedIcon("code", "Coding", Icons.Default.Code),
        NamedIcon("palette", "Creative", Icons.Default.Palette),
        NamedIcon("music", "Music", Icons.Default.MusicNote),
        NamedIcon("favorite", "Health", Icons.Default.Favorite),
        NamedIcon("schedule", "Routine", Icons.Default.Schedule)
    )

    fun getIcon(name: String): ImageVector {
        return availableIcons.find { it.id.equals(name, ignoreCase = true) }?.icon ?: Icons.Default.CheckCircle
    }

    val availableColors: List<Color> = listOf(
        Color(0xFFA855F7), // Purple
        Color(0xFF38BDF8), // Cyan / Sky
        Color(0xFF10B981), // Emerald Green
        Color(0xFFF97316), // Orange
        Color(0xFFEC4899), // Pink
        Color(0xFFF59E0B), // Amber / Yellow
        Color(0xFF6366F1), // Indigo
        Color(0xFFEF4444)  // Rose Red
    )

    fun colorToHex(color: Color): String {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun parseColor(hex: String, fallback: Color = Color(0xFFA855F7)): Color {
        return runCatching {
            val cleanHex = hex.removePrefix("#")
            val colorLong = cleanHex.toLong(16)
            if (cleanHex.length == 6) {
                Color(0xFF000000 or colorLong)
            } else if (cleanHex.length == 8) {
                Color(colorLong)
            } else fallback
        }.getOrDefault(fallback)
    }
}
