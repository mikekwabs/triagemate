package com.triagemate.chps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triagemate.chps.presentation.screens.assessment.AssessmentUiState
import com.triagemate.chps.presentation.theme.ProgressGreen
import com.triagemate.chps.presentation.theme.StepperTeal

private enum class ProgressState { PENDING, ACTIVE, DONE }

private data class ProgressItem(
    val label: String,
    val state: ProgressState,
    val detail: String? = null
)

private fun buildProgressItems(uiState: AssessmentUiState, hasPhoto: Boolean): List<ProgressItem> {
    val assessLog = uiState.toolCallLog.firstOrNull { it.toolName == "assessSymptoms" }
    val vitalRequestLog = uiState.toolCallLog.firstOrNull { it.toolName == "requestVitalSigns" }
    val vitalsDone = uiState.vitalSignValues.isNotEmpty()
    val classifyLog = uiState.toolCallLog.firstOrNull { it.toolName == "classifyTriage" }
    val referralLog = uiState.toolCallLog.firstOrNull { it.toolName == "generateReferralNote" }
    val items = mutableListOf<ProgressItem>()

    if (assessLog != null) {
        val count = uiState.dangerSignCount
        val label = if (count > 0) {
            "Symptoms assessed — $count danger signs detected"
        } else {
            "Symptoms assessed"
        }
        val timing = if (uiState.assessmentStartMs > 0) {
            val secs = (assessLog.timestamp - uiState.assessmentStartMs) / 1000f
            if (secs > 0f) "%.1fs".format(secs) else null
        } else {
            null
        }
        items.add(ProgressItem(label, ProgressState.DONE, timing))
    } else {
        items.add(ProgressItem("Assessing symptoms...", ProgressState.ACTIVE))
    }

    if (vitalRequestLog != null || uiState.awaitingVitals || vitalsDone) {
        when {
            vitalsDone -> {
                val summary = buildVitalsSummary(uiState.vitalSignValues)
                items.add(ProgressItem("Vital signs recorded — $summary", ProgressState.DONE))
            }
            uiState.awaitingVitals -> items.add(ProgressItem("Requesting vital signs...", ProgressState.ACTIVE))
            else -> items.add(ProgressItem("Vital signs requested", ProgressState.DONE))
        }
    }

    val classifyActive = assessLog != null && classifyLog == null && uiState.isLoading && !uiState.awaitingVitals
    when {
        classifyLog != null -> items.add(ProgressItem("Urgency classified", ProgressState.DONE))
        classifyActive -> items.add(
            ProgressItem(
                if (hasPhoto) "Classifying urgency with visual input..." else "Classifying urgency...",
                ProgressState.ACTIVE
            )
        )
        assessLog != null && referralLog == null -> items.add(
            ProgressItem(
                if (hasPhoto) "Classifying urgency with visual input..." else "Classifying urgency...",
                ProgressState.PENDING
            )
        )
    }

    when {
        referralLog != null -> items.add(ProgressItem("Referral note generated", ProgressState.DONE))
        classifyLog != null && uiState.isLoading -> items.add(ProgressItem("Generating referral note...", ProgressState.ACTIVE))
        classifyLog != null -> items.add(ProgressItem("Generating referral note...", ProgressState.PENDING))
    }

    return items
}

private fun buildVitalsSummary(vitals: Map<String, String>): String {
    val parts = mutableListOf<String>()
    vitals["temperature"]?.takeIf { it.isNotBlank() }?.let { parts.add("Temp ${it}°C") }
    vitals["respiratory_rate"]?.takeIf { it.isNotBlank() }?.let { parts.add("RR $it/min") }
    vitals["pulse"]?.takeIf { it.isNotBlank() }?.let { parts.add("HR $it bpm") }
    return parts.ifEmpty { listOf("recorded") }.joinToString(", ")
}

@Composable
fun ActivityLogCard(
    uiState: AssessmentUiState,
    hasPhoto: Boolean,
    modifier: Modifier = Modifier
) {
    val items = remember(
        uiState.toolCallLog,
        uiState.awaitingVitals,
        uiState.isLoading,
        uiState.vitalSignValues,
        uiState.dangerSignCount,
        uiState.assessmentStartMs,
        hasPhoto
    ) {
        buildProgressItems(uiState, hasPhoto)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (hasPhoto) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEAF9F7))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = StepperTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "Clinical photo attached — Gemma will analyse alongside symptoms",
                        color = Color(0xFF111111),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                if (items.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
                }
            }

            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (item.state) {
                        ProgressState.DONE -> {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ProgressGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ProgressGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        ProgressState.ACTIVE -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = StepperTeal,
                                strokeWidth = 2.5.dp
                            )
                        }

                        ProgressState.PENDING -> {
                            Box(
                                modifier = Modifier.size(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFBDBDBD))
                                )
                            }
                        }
                    }

                    Spacer(Modifier.size(12.dp))

                    Text(
                        text = item.label,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        fontWeight = if (item.state == ProgressState.DONE) FontWeight.Medium else FontWeight.Normal,
                        color = when (item.state) {
                            ProgressState.DONE -> Color(0xFF2C3E50)
                            ProgressState.ACTIVE -> StepperTeal
                            ProgressState.PENDING -> Color(0xFF9E9E9E)
                        }
                    )

                    if (item.detail != null) {
                        Text(
                            text = item.detail,
                            fontSize = 12.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }

                if (index < items.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = Color(0xFFF0F0F0)
                    )
                }
            }
        }
    }
}
