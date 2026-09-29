package com.triagemate.chps.domain.safety

import com.triagemate.chps.domain.model.AssessmentExtras
import com.triagemate.chps.domain.model.ChecklistLabels
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.domain.model.UrineProtein

/**
 * Sections D (child under 5, WHO IMCI 2014) and E (antenatal, WHO PCPNC 2015) of the review
 * document "TriageMate Assessment Additions" (pending clinical sign-off). IMCI pink maps to
 * RED and yellow to AMBER. Items without an explicit urgency rule (malaria RDT, leaking
 * fluid) are passed to the model only. E4 (severe abdominal pain) is on the auto-RED
 * checklist list and handled by the danger-sign guardrail.
 */
object AssessmentRules {

    fun evaluate(
        pathway: Pathway,
        symptoms: List<String>,
        extras: AssessmentExtras,
        patientAgeMonths: Int?,
        vitalSigns: Map<String, String>
    ): List<SafetyRuleHit> {
        val relevant = extras.forSymptoms(pathway, symptoms)
        return when (pathway) {
            Pathway.CHILD_U5 -> childRules(symptoms, relevant, patientAgeMonths?.takeIf { it >= 0 })
            Pathway.ANTENATAL -> antenatalRules(symptoms, relevant, vitalSigns)
        }
    }

    private fun childRules(symptoms: List<String>, extras: AssessmentExtras, age: Int?) = buildList {
        extras.diarrhoeaDays?.let { days ->
            if (days >= 14) add(hit("D1", "AMBER", "diarrhoea for $days days is persistent diarrhoea (14 days or more)"))
        }
        extras.coughDays?.let { days ->
            if (days > 14) add(hit("D2", "AMBER", "cough for $days days needs assessment (more than 14 days)"))
        }
        extras.feverDays?.let { days ->
            if (days > 7) add(hit("D3", "AMBER", "fever every day for $days days needs assessment (more than 7 days)"))
        }
        if (extras.oedemaBothFeet == true) {
            add(hit("D5", "RED", "oedema of both feet is complicated severe acute malnutrition"))
        }
        extras.muacMm?.let { muac ->
            when {
                muac < 115 -> add(hit("D6", "AMBER", "MUAC $muac mm is severe acute malnutrition (below 115 mm)"))
                muac < 125 -> add(hit("D7", "AMBER", "MUAC $muac mm is moderate acute malnutrition (115–124 mm)"))
            }
        }
        // Young-infant classification of blood in stool is still to be confirmed (review question 3).
        if (ChecklistLabels.BLOOD_IN_STOOL in symptoms && (age == null || age >= 2)) {
            add(hit("D8", "AMBER", "blood in stool is dysentery"))
        }
    }

    private fun antenatalRules(
        symptoms: List<String>,
        extras: AssessmentExtras,
        vitalSigns: Map<String, String>
    ) = buildList {
        val protein = extras.urineProtein ?: return@buildList
        val diastolic = vitalSigns
            .filterKeys { VitalSigns.canonicalKey(it) == VitalSigns.BLOOD_PRESSURE }
            .values.firstOrNull { VitalsValidator.error(VitalSigns.BLOOD_PRESSURE, it) == null }
            ?.let(VitalSigns::parseBloodPressure)?.second
            ?: return@buildList

        val severeFeature = ChecklistLabels.SEVERE_HEADACHE in symptoms || ChecklistLabels.BLURRED_VISION in symptoms
        val bp = "diastolic $diastolic mmHg with urine protein ${protein.label}"
        when {
            diastolic >= 110 && protein == UrineProtein.PLUS3 ->
                add(hit("E2", "RED", "$bp is severe pre-eclampsia"))
            diastolic >= 90 && protein >= UrineProtein.PLUS2 && severeFeature ->
                add(hit("E1", "RED", "$bp and headache or visual disturbance is severe pre-eclampsia"))
            diastolic >= 90 && protein >= UrineProtein.PLUS2 ->
                add(hit("E3", "AMBER", "$bp is pre-eclampsia"))
        }
    }

    private fun hit(id: String, urgency: String, finding: String) =
        SafetyRuleHit(id, urgency, finding, category = "Assessment")
}

/** Entry checks for the additional findings; same hard-block approach as [VitalsValidator]. */
object AssessmentExtrasValidator {
    private val WHOLE_NUMBER = Regex("""^\d{1,3}$""")

    fun daysError(raw: String): String? = wholeInRange(raw, 1, 90, "a number of days", "e.g. 3")

    fun muacError(raw: String): String? = wholeInRange(raw, 60, 250, "a MUAC", "in mm, e.g. 125")

    private fun wholeInRange(raw: String, min: Int, max: Int, what: String, example: String): String? {
        if (raw.isBlank()) return null
        val value = raw.trim().takeIf { WHOLE_NUMBER.matches(it) }?.toInt()
            ?: return "Enter a whole number, $example."
        return if (value !in min..max) "Enter $what between $min and $max." else null
    }
}
