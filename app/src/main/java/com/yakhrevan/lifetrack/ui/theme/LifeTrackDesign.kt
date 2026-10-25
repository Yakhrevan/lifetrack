package com.yakhrevan.lifetrack.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Central palette and spacing for a consistent, low-clutter LifeTrack UI. */
object LifeTrackColors {
    // Palette values are observable so the entire app can switch appearance without restarting.
    val darkMode = androidx.compose.runtime.mutableStateOf(false)
    val accentHex = androidx.compose.runtime.mutableStateOf("#A855F7")
    val textSizeMultiplier = androidx.compose.runtime.mutableFloatStateOf(1.0f)
    val dashboardStyle = androidx.compose.runtime.mutableStateOf("Comfortable")
    val enableAnimations = androidx.compose.runtime.mutableStateOf(true)
    val reduceMotion = androidx.compose.runtime.mutableStateOf(false)

    private fun accentColor(): Color = runCatching {
        Color(android.graphics.Color.parseColor(accentHex.value))
    }.getOrDefault(if (darkMode.value) Color(0xFFB8A0FF) else Color(0xFF8B5CF6))

    fun applySettings(settings: com.yakhrevan.lifetrack.AppSettings) {
        darkMode.value = settings.themeMode == "Dark"
        accentHex.value = settings.accentColorHex
        textSizeMultiplier.floatValue = settings.textSizeMultiplier.coerceIn(0.85f, 1.15f)
        dashboardStyle.value = settings.dashboardStyle
        enableAnimations.value = settings.enableAnimations
        reduceMotion.value = settings.reduceMotion
    }

    val isMotionReduced: Boolean get() = reduceMotion.value || !enableAnimations.value
    val CompactGap get() = if (dashboardStyle.value == "Compact") 8.dp else 16.dp
    val CompactPadding get() = if (dashboardStyle.value == "Compact") 10.dp else 16.dp

    val Background: Color get() = if (darkMode.value) Color(0xFF101522) else Color(0xFFF8FAFF)
    val Surface: Color get() = if (darkMode.value) Color(0xFF1A2233) else Color(0xFFFFFFFF)
    val SurfaceRaised: Color get() = if (darkMode.value) Color(0xFF242F45) else Color(0xFFEEF3FF)
    val Primary: Color get() = accentColor()
    val Blue: Color get() = if (darkMode.value) Color(0xFF69BCFF) else Color(0xFF2498F3)
    val Purple: Color get() = accentColor()
    val Pink: Color get() = if (darkMode.value) Color(0xFFFF8EAD) else Color(0xFFF05B87)
    val Orange: Color get() = if (darkMode.value) Color(0xFFFFB66D) else Color(0xFFFF9B43)
    val Green: Color get() = if (darkMode.value) Color(0xFF55D9AC) else Color(0xFF20B982)
    val TextPrimary: Color get() = if (darkMode.value) Color(0xFFF1F5FF) else Color(0xFF17213D)
    val TextSecondary: Color get() = if (darkMode.value) Color(0xFFADB8CE) else Color(0xFF74809A)
    val Border: Color get() = if (darkMode.value) Color(0x1FFFFFFF) else Color(0x1A000000)
}

object LifeTrackSpacing {
    val ScreenHorizontal get() = if (LifeTrackColors.dashboardStyle.value == "Compact") 14.dp else 20.dp
    val SectionGap get() = LifeTrackColors.CompactGap
    val CardPadding get() = LifeTrackColors.CompactPadding
    val CardRadius = 20.dp
}

/** Reusable section heading keeps feature screens visually consistent. */
@Composable
fun LifeTrackSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = LifeTrackColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LifeTrackColors.TextSecondary
            )
        }
    }
}

/** Standard compact card for feature summaries and settings groups. */
@Composable
fun LifeTrackSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LifeTrackSpacing.CardRadius),
        colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface)
    ) {
        Column(
            modifier = Modifier.padding(LifeTrackSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(LifeTrackSpacing.SectionGap)
        ) {
            content()
        }
    }
}
