package com.triagemate.chps.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.triagemate.chps.presentation.theme.UrgencyAmber
import com.triagemate.chps.presentation.theme.UrgencyGreen
import com.triagemate.chps.presentation.theme.UrgencyRed

@Composable
fun UrgencyBadge(urgency: String, modifier: Modifier = Modifier) {
    val containerColor = when (urgency.uppercase()) {
        "RED" -> UrgencyRed
        "AMBER" -> UrgencyAmber
        "GREEN" -> UrgencyGreen
        else -> Color.Gray
    }

    AssistChip(
        onClick = { },
        label = { Text(urgency, color = Color.White) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = Color.White
        ),
        border = null,
        modifier = modifier
    )
}
