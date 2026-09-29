package com.triagemate.chps.util

import com.triagemate.chps.domain.model.Pathway

/**
 * Builds the referral note from the finished triage in Kotlin instead of asking the
 * model to write it. On CPU the model-written note was the most expensive decode in the
 * loop (~240 tokens, ~40 s on a Galaxy A10), and a template also guarantees the note
 * states the FINAL urgency, including any safety-guardrail override.
 *
 * Output is plain text suitable for WhatsApp, SMS, or print.
 */
object ReferralNoteBuilder {

    fun build(
        pathway: Pathway,
        patientAge: String,
        patientSex: String,
        symptoms: List<String>,
        dangerSigns: List<String>,
        vitalSigns: Map<String, String>?,
        medications: String,
        urgency: String,
        action: String,
        safetyOverrideReason: String? = null,
        originalModelUrgency: String? = null
    ): String {
        val normalizedUrgency = urgency.trim().uppercase()
        val lines = buildList {
            add("TriageMate referral — $normalizedUrgency (${urgencyMeaning(normalizedUrgency)})")
            add("Patient: ${patientSummary(pathway, patientAge, patientSex)}")
            add("Presenting symptoms: ${symptoms.joinToString(", ").ifBlank { "None recorded" }}")
            add("Danger signs: ${cleanDangerSigns(dangerSigns).joinToString(", ").ifBlank { "None identified" }}")
            add("Vital signs: ${formatVitals(vitalSigns)}")
            add("Current medications: ${medications.trim().ifBlank { "None reported" }}")
            add("Recommended action: ${action.trim().ifBlank { "Use clinical judgement." }}")
            if (!safetyOverrideReason.isNullOrBlank()) {
                val original = originalModelUrgency?.let { " (AI assessment was ${it.uppercase()})" }.orEmpty()
                add("Safety override: $safetyOverrideReason — escalated to $normalizedUrgency$original.")
            }
        }
        return lines.joinToString("\n")
    }

    private fun urgencyMeaning(urgency: String): String = when (urgency) {
        "RED" -> "urgent referral within 4 hours"
        "AMBER" -> "non-urgent referral, review within 24–48 hours"
        "GREEN" -> "manage locally"
        else -> "urgency unclear — use clinical judgement"
    }

    private fun patientSummary(pathway: Pathway, patientAge: String, patientSex: String): String {
        val age = patientAge.trim()
        return when (pathway) {
            Pathway.ANTENATAL -> {
                val gestation = if (age.isNotEmpty()) "$age weeks gestation" else "gestation not recorded"
                "Female, $gestation, ${pathway.displayName}"
            }
            Pathway.CHILD_U5 -> {
                val sex = patientSex.trim().lowercase().replaceFirstChar(Char::uppercase).ifEmpty { "Sex not recorded" }
                val ageText = if (age.isNotEmpty()) "$age months" else "age not recorded"
                "$sex, $ageText, ${pathway.displayName}"
            }
        }
    }

    private val NO_DANGER_SIGN_VALUES = setOf("none", "nil", "n/a", "na", "no danger signs", "not_collected")

    private fun cleanDangerSigns(dangerSigns: List<String>): List<String> =
        dangerSigns.map(String::trim)
            .filter { it.isNotEmpty() && it.lowercase() !in NO_DANGER_SIGN_VALUES }
            .distinctBy(String::lowercase)

    private fun formatVitals(vitalSigns: Map<String, String>?): String {
        val provided = vitalSigns.orEmpty().filterValues(String::isNotBlank)
        if (provided.isEmpty()) return "Not collected"
        return provided.entries.joinToString(", ") { (key, value) ->
            "${key.replace('_', ' ')} ${value.trim()}"
        }
    }
}
