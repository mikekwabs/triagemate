package com.triagemate.chps.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triagemate.chps.presentation.screens.assessment.VoiceInputState
import com.triagemate.chps.presentation.theme.LightBlueAccent
import com.triagemate.chps.presentation.theme.PrimaryNavy

/**
 * Replaces [VoiceInputRow] once recording begins. Renders the four
 * states of the voice-input flow:
 *  - Recording (waveform + Stop)
 *  - Processing (spinner)
 *  - Confirmed (translation + matched symptoms + dismiss)
 *  - Error (message + retry / cancel)
 */
@Composable
fun VoiceInputCard(
    state: VoiceInputState,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, LightBlueAccent, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        when (state) {
            is VoiceInputState.Recording -> RecordingBody(onStop)
            is VoiceInputState.Processing -> ProcessingBody()
            is VoiceInputState.Confirmed -> ConfirmedBody(state, onDismiss)
            is VoiceInputState.Error -> ErrorBody(state.message, onRetry, onDismiss)
            VoiceInputState.Idle -> Unit
        }
    }
}

@Composable
private fun RecordingBody(onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RecordingPulse()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Listening in Twi…",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color(0xFF2C3E50)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Tap stop when you're done speaking",
                fontSize = 12.sp,
                color = Color(0xFF7F8C8D)
            )
        }
        IconButton(onClick = onStop) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFC62828)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Stop,
                    contentDescription = "Stop recording",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    WaveformBars()
}

@Composable
private fun ProcessingBody() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = PrimaryNavy,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Translating with Gemma…",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color(0xFF2C3E50)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "On-device, no internet needed",
                fontSize = 12.sp,
                color = Color(0xFF7F8C8D)
            )
        }
    }
}

@Composable
private fun ConfirmedBody(
    state: VoiceInputState.Confirmed,
    onDismiss: () -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Heard you",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color(0xFF2C3E50)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "\u201C${state.translation}\u201D",
                fontSize = 13.sp,
                color = Color(0xFF4A4A4A),
                lineHeight = 18.sp
            )
            if (state.matchedSymptoms.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ticked for you:",
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = PrimaryNavy
                )
                Spacer(Modifier.height(4.dp))
                state.matchedSymptoms.forEach { symptom ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PrimaryNavy)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(symptom, fontSize = 13.sp, color = Color(0xFF34495E))
                    }
                    Spacer(Modifier.height(2.dp))
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Nothing on the checklist matched — please tick symptoms manually.",
                    fontSize = 12.sp,
                    color = Color(0xFFD97706),
                    lineHeight = 16.sp
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Dismiss",
                tint = Color(0xFF7F8C8D),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFC62828),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Voice input failed",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color(0xFFC62828)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                fontSize = 12.sp,
                color = Color(0xFF7F8C8D),
                lineHeight = 16.sp
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Cancel", color = Color(0xFF7F8C8D), fontSize = 13.sp)
        }
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("Try again", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RecordingPulse() {
    val transition = rememberInfiniteTransition(label = "voicePulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voicePulseScale"
    )
    Box(
        modifier = Modifier
            .size((44 * scale).dp.coerceAtLeast(36.dp))
            .clip(CircleShape)
            .background(Color(0xFFFFE5E5)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.Mic,
            contentDescription = null,
            tint = Color(0xFFC62828),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun WaveformBars() {
    val transition = rememberInfiniteTransition(label = "voiceWave")
    val bars = listOf(0, 120, 240, 80, 200, 40, 160, 280)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        bars.forEachIndexed { index, delay ->
            val height by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delay,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((34 * height).dp.coerceAtLeast(6.dp))
                    .clip(RoundedCornerShape(3.dp))
                    .background(PrimaryNavy.copy(alpha = 0.75f))
            )
        }
    }
}
