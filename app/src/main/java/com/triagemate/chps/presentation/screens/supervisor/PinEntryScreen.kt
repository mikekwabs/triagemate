package com.triagemate.chps.presentation.screens.supervisor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinEntryScreen(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    viewModel: PinEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.verified) {
        if (uiState.verified) onSuccess()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supervisor Access", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1F2937)
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = Color(0xFFF8F9FA)
        ) {
            val shakeOffset = if (uiState.shakeAnimation) {
                val transition = rememberInfiniteTransition(label = "pin_shake")
                transition.animateFloat(
                    initialValue = -8f,
                    targetValue = 8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(70, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "shake_anim"
                ).value.roundToInt()
            } else {
                0
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Enter supervisor PIN",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Use the 4-digit PIN created during compound setup.",
                    color = Color(0xFF6B7280),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.offset(x = shakeOffset.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(
                                    if (index < uiState.enteredDigits.size) Color(0xFF0D9488) else Color(0xFFD1D5DB),
                                    CircleShape
                                )
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                val helperText = when {
                    uiState.lockoutSecondsRemaining > 0 ->
                        "Too many attempts. Wait ${uiState.lockoutSecondsRemaining}s."
                    uiState.errorMessage != null -> uiState.errorMessage!!
                    else -> "PIN verifies automatically after 4 digits"
                }
                Text(
                    text = helperText,
                    color = if (uiState.errorMessage != null) Color(0xFFDC2626) else Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(32.dp))

                PinPad(
                    onDigit = { viewModel.enterDigit(it.toInt()) },
                    onDelete = viewModel::deleteLastDigit
                )
            }
        }
    }
}

@Composable
private fun PinPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                row.forEach { digit ->
                    KeyButton(label = digit, modifier = Modifier.weight(1f)) { onDigit(digit) }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.weight(1f))
            KeyButton(label = "0", modifier = Modifier.weight(1f)) { onDigit("0") }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Delete",
                    tint = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFF111827),
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
