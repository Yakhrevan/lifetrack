package com.yakhrevan.lifetrack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBackground = Color(0xFF090D1A)
private val CardInner = Color(0xFF1B2438)
private val PurpleBrand = Color(0xFFA855F7)
private val TextWhite = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun HabitsRoutinesScreen(
    modifier: Modifier = Modifier,
    initialTab: String = "Habits",
    onHabitCompleted: (name: String, completed: Boolean) -> Unit = { _, _ -> }
) {
    var selectedTab by remember { mutableStateOf(initialTab) } // "Habits" or "Routines"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Tab Switcher: Habits | Routines
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Habits", "Routines").forEach { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) PurpleBrand else CardInner)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab,
                        color = if (isSelected) Color.White else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Active Screen
        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == "Habits") {
                HabitsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onHabitCompletedCallback = onHabitCompleted
                )
            } else {
                RoutinesScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}