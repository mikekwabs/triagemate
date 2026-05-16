package com.triagemate.chps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triagemate.chps.domain.model.ConfidenceLevel
import com.triagemate.chps.presentation.theme.UrgencyAmber
import com.triagemate.chps.presentation.theme.UrgencyGreen
import com.triagemate.chps.presentation.theme.UrgencyRed

@Composable
fun UrgencyBadge(
    urgency: String,
    modifier: Modifier = Modifier,
    confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
    wasOverridden: Boolean = false
) {
    val containerColor = when (urgency.uppercase()) {
        "RED"   -> UrgencyRed
        "AMBER" -> UrgencyAmber
        "GREEN" -> UrgencyGreen
        else    -> Color.Gray
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

/**
 * Small pill chip rendered on top of the coloured urgency banner.
 * Communicates either the model's self-reported confidence or — with
 * higher priority — that the safety guardrail overrode the model.
 *
 * Visual design:
 *  - Frosted-glass pill (white border + translucent white fill) so it reads
 *    cleanly against any banner colour (red / amber / green)
 *  - Leading icon gives instant recognition without reading
 *  - Override state uses a solid white fill to stand out as authoritative
 */
@Composable
fun ConfidenceChip(
    confidence: ConfidenceLevel,
    wasOverridden: Boolean,
    modifier: Modifier = Modifier
) {
    data class ChipStyle(
        val text: String,
        val icon: ImageVector,
        val bgColor: Color,
        val borderColor: Color,
        val contentColor: Color
    )

    val style = when {
        wasOverridden -> ChipStyle(
            text = "Override applied",
            icon = Icons.Filled.Shield,
            bgColor = Color.White,
            borderColor = Color.White,
            contentColor = Color(0xFFC62828)
        )
        confidence == ConfidenceLevel.HIGH -> ChipStyle(
            text = "High confidence",
            icon = Icons.Outlined.CheckCircle,
            bgColor = Color(0x29FFFFFF),
            borderColor = Color(0x66FFFFFF),
            contentColor = Color.White
        )
        confidence == ConfidenceLevel.MEDIUM -> ChipStyle(
            text = "Medium confidence",
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            bgColor = Color(0x29FFFFFF),
            borderColor = Color(0x66FFFFFF),
            contentColor = Color.White
        )
        else -> ChipStyle(
            text = "Low — verify",
            icon = Icons.Outlined.WarningAmber,
            bgColor = Color.White,
            borderColor = Color.White,
            contentColor = Color(0xFFD97706)
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(style.bgColor)
            .border(1.dp, style.borderColor, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.contentColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = style.text,
            color = style.contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
