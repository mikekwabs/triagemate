package com.triagemate.chps.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triagemate.chps.domain.model.ChecklistLabels
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.domain.model.RdtResult
import com.triagemate.chps.domain.model.UrineProtein
import com.triagemate.chps.domain.safety.AssessmentExtrasValidator
import com.triagemate.chps.presentation.screens.assessment.ExtrasForm
import com.triagemate.chps.presentation.theme.StepperTeal
import kotlinx.coroutines.delay

private const val ERROR_DISPLAY_DELAY_MS = 600L

/**
 * Optional structured findings ("TriageMate Assessment Additions", sections D and E).
 * Each item only appears when it applies: durations and RDT for a ticked symptom,
 * MUAC for 6–59 months (or age not yet entered), urine protein for antenatal.
 */
@Composable
fun AssessmentExtrasSection(
    pathway: Pathway,
    selectedSymptoms: Set<String>,
    patientAgeMonths: Int?,
    form: ExtrasForm,
    onChange: ((ExtrasForm) -> ExtrasForm) -> Unit,
    modifier: Modifier = Modifier
) {
    val isChild = pathway == Pathway.CHILD_U5
    val hasFever = ChecklistLabels.fever(pathway) in selectedSymptoms
    val showCough = isChild && ChecklistLabels.COUGH in selectedSymptoms
    val showDiarrhoea = isChild && ChecklistLabels.DIARRHOEA in selectedSymptoms
    val showMuac = isChild && (patientAgeMonths == null || patientAgeMonths in 6..59)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Additional findings (optional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C3E50)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Record what you have checked. Leave anything you have not measured blank.",
            fontSize = 13.sp,
            color = Color(0xFF7F8C8D)
        )
        Spacer(Modifier.height(12.dp))

        if (hasFever) {
            NumberField("Days of fever", form.feverDays, "1–90 days", AssessmentExtrasValidator::daysError) { v ->
                onChange { it.copy(feverDays = v) }
            }
            ChoiceRow(
                label = "Malaria RDT",
                options = RdtResult.entries.map { it to it.label.replaceFirstChar(Char::uppercase) },
                selected = form.malariaRdt
            ) { v -> onChange { it.copy(malariaRdt = v) } }
        }
        if (showCough) {
            NumberField("Days of cough", form.coughDays, "1–90 days", AssessmentExtrasValidator::daysError) { v ->
                onChange { it.copy(coughDays = v) }
            }
        }
        if (showDiarrhoea) {
            NumberField("Days of diarrhoea", form.diarrhoeaDays, "1–90 days", AssessmentExtrasValidator::daysError) { v ->
                onChange { it.copy(diarrhoeaDays = v) }
            }
        }
        if (isChild) {
            ChoiceRow(
                label = "Oedema of both feet",
                options = listOf(true to "Yes", false to "No"),
                selected = form.oedemaBothFeet
            ) { v -> onChange { it.copy(oedemaBothFeet = v) } }
        }
        if (showMuac) {
            NumberField("MUAC", form.muacMm, "60–250 mm, age 6–59 months", AssessmentExtrasValidator::muacError, unit = "mm") { v ->
                onChange { it.copy(muacMm = v) }
            }
        }
        if (!isChild) {
            ChoiceRow(
                label = "Urine protein (dipstick)",
                options = UrineProtein.entries.map { it to it.label.replaceFirstChar(Char::uppercase) },
                selected = form.urineProtein
            ) { v -> onChange { it.copy(urineProtein = v) } }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    hint: String,
    validate: (String) -> String?,
    unit: String = "",
    onValueChange: (String) -> Unit
) {
    val error = validate(value)
    // Same approach as the vitals sheet: a constant supporting line (no height change),
    // and errors only after typing pauses.
    var shownError by remember(label) { mutableStateOf<String?>(null) }
    LaunchedEffect(label, error) {
        if (error == null) {
            shownError = null
        } else {
            delay(ERROR_DISPLAY_DELAY_MS)
            shownError = error
        }
    }
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4A4A4A))
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = shownError != null,
        supportingText = { Text(shownError ?: hint) },
        suffix = { if (unit.isNotEmpty()) Text(unit, color = Color(0xFF95A5A6), fontSize = 13.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFE0E0E0),
            focusedBorderColor = StepperTeal,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit
) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4A4A4A))
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        options.forEach { (value, text) ->
            val isSelected = selected == value
            FilterChip(
                selected = isSelected,
                // Tapping the selected chip again clears the answer.
                onClick = { onSelect(if (isSelected) null else value) },
                label = { Text(text) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFE0F2F1),
                    selectedLabelColor = Color(0xFF00695C)
                )
            )
        }
    }
}
