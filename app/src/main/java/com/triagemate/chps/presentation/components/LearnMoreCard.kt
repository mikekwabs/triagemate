package com.triagemate.chps.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triagemate.chps.domain.model.ClinicalExplanation
import com.triagemate.chps.presentation.screens.result.LearnMoreState
import com.triagemate.chps.presentation.theme.LightBlueAccent
import com.triagemate.chps.presentation.theme.PrimaryNavy

/**
 * "Learn about this case" — collapsible educational card shown after a
 * triage assessment. The Gemma call is fired ONLY when the user taps to
 * expand it; result is cached by the ViewModel and re-expansion is free.
 */
@Composable
fun LearnMoreCard(
    state: LearnMoreState,
    onExpand: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded && state is LearnMoreState.Idle) {
            onExpand()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, LightBlueAccent, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LightBlueAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = PrimaryNavy,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Learn about this case",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "Why this classification & what to watch for",
                        fontSize = 12.sp,
                        color = Color(0xFF7F8C8D)
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color(0xFF7F8C8D)
                )
            }

            if (expanded) {
                Spacer(Modifier.height(14.dp))
                when (state) {
                    is LearnMoreState.Idle -> LearnMoreLoadingRow("Preparing explanation…")
                    is LearnMoreState.Loading -> LearnMoreLoadingRow("Generating explanation…")
                    is LearnMoreState.Ready -> LearnMoreReady(state.explanation)
                    is LearnMoreState.Error -> LearnMoreError(state.message, onRetry)
                }
            }
        }
    }
}

@Composable
private fun LearnMoreLoadingRow(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = PrimaryNavy,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFF7F8C8D))
    }
}

@Composable
private fun LearnMoreReady(explanation: ClinicalExplanation) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ExplanationSection(
            icon = Icons.Outlined.Lightbulb,
            label = "WHY THIS CLASSIFICATION",
            body = explanation.whyThisClassification
        )
        ExplanationSection(
            icon = Icons.Outlined.Visibility,
            label = "WHAT TO WATCH FOR",
            body = explanation.whatToWatchFor
        )
        ClinicalReferenceChip(explanation.clinicalReference)
    }
}

@Composable
private fun ExplanationSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    body: String
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PrimaryNavy, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryNavy
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF34495E),
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun ClinicalReferenceChip(reference: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(LightBlueAccent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.MenuBook,
            null,
            tint = PrimaryNavy,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            reference,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryNavy
        )
    }
}

@Composable
private fun LearnMoreError(message: String, onRetry: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.ErrorOutline,
                null,
                tint = Color(0xFFC62828),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Couldn't generate explanation",
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFC62828),
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = Color(0xFF7F8C8D), lineHeight = 18.sp)
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp), tint = PrimaryNavy)
            Spacer(Modifier.width(6.dp))
            Text("Try again", color = PrimaryNavy, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
