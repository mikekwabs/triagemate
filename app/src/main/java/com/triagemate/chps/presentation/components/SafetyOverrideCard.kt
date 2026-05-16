package com.triagemate.chps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triagemate.chps.domain.safety.SafetyOverrideResult

@Composable
fun SafetyOverrideCard(override: SafetyOverrideResult) {
    val borderColor = Color(0xFFD97706)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFFBEB))
            .drawBehind {
                drawRect(color = borderColor, size = size.copy(width = 4.dp.toPx()))
            }
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Safety override applied",
                    color = Color(0xFF92400E),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(
                color = Color(0xFFFDE68A),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Gemma initially classified this case as ${override.originalGemmaUrgency}. " +
                    "A WHO danger sign was detected (${override.overriddenSigns.joinToString(", ")}) — " +
                    "TriageMate overrode to RED to protect patient safety.",
                color = Color(0xFF92400E),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFFF0FDFA), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Override recorded in audit trail",
                    color = Color(0xFF0D9488),
                    fontSize = 10.sp
                )
            }
        }
    }
}
