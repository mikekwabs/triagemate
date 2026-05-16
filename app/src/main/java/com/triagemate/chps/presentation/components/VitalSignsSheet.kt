package com.triagemate.chps.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triagemate.chps.presentation.theme.PrimaryNavy
import com.triagemate.chps.presentation.theme.StepperTeal

private data class VitalMeta(val label: String, val unit: String, val placeholder: String)

private fun normalizeVitalKey(key: String): String = key.trim()
    .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
    .replace(" ", "_")
    .replace("-", "_")
    .lowercase()

private fun vitalMeta(key: String): VitalMeta = when (normalizeVitalKey(key)) {
    "temperature" -> VitalMeta("Temperature", "°C", "e.g. 38.5")
    "respiratory_rate" -> VitalMeta("Respiratory Rate", "breaths/min", "e.g. 40")
    "pulse" -> VitalMeta("Pulse Rate", "bpm", "e.g. 100")
    "blood_pressure" -> VitalMeta("Blood Pressure", "mmHg", "e.g. 120/80")
    "oxygen_saturation" -> VitalMeta("SpO₂", "%", "e.g. 98")
    else -> VitalMeta(key.replaceFirstChar { it.uppercase() }, "", "")
}

@Composable
fun VitalSignsSheet(
    requiredVitals: List<String>,
    vitalValues: Map<String, String>,
    onVitalChanged: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            "Vital Signs Needed",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C3E50)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "TriageMate needs these measurements for a more accurate assessment.",
            fontSize = 13.sp,
            color = Color(0xFF7F8C8D),
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(24.dp))

        if (requiredVitals.isEmpty()) {
            AssistChip(
                onClick = {},
                label = { Text("No specific vital fields were named. You can continue without entering any.") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFFF4F6F8),
                    labelColor = Color(0xFF5F6B76)
                )
            )
            Spacer(Modifier.height(16.dp))
        }

        requiredVitals.forEach { vitalKey ->
            val normalizedKey = normalizeVitalKey(vitalKey)
            val meta = vitalMeta(normalizedKey)
            val currentValue = vitalValues[normalizedKey] ?: ""

            Text(
                text = meta.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4A4A4A)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = currentValue,
                onValueChange = { onVitalChanged(normalizedKey, it) },
                placeholder = { Text(meta.placeholder, color = Color(0xFFBDC3C7)) },
                suffix = {
                    if (meta.unit.isNotEmpty()) {
                        Text(meta.unit, color = Color(0xFF95A5A6), fontSize = 13.sp)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (normalizedKey == "blood_pressure") KeyboardType.Text else KeyboardType.Decimal
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedBorderColor = StepperTeal,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                singleLine = true
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
            enabled = vitalValues.values.any { it.isNotBlank() }
        ) {
            Text("Submit Vitals", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Skip and continue without vitals",
                color = Color(0xFF7F8C8D),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}
