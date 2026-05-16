package com.triagemate.chps.util

import com.triagemate.chps.domain.model.FindingType
import com.triagemate.chps.domain.model.Pathway

data class VisualCue(
    val title: String,
    val reason: String,
    val gemmaInstruction: String,
    val findingType: FindingType,
    val triggerSymptoms: List<String>
) {
    companion object {
        fun generic(): VisualCue = VisualCue(
            title = "Photograph the visible sign",
            reason = "Clinical photo may help refine the assessment",
            gemmaInstruction = "The CHO has taken a clinical photograph. Assess whether there is any clinically relevant visible finding in the image. Be honest, specific, and concise. Report your finding, confidence level, and clinical implication.",
            findingType = FindingType.NEW_OBSERVATION,
            triggerSymptoms = emptyList()
        )
    }
}

sealed class CameraTier {
    data object SuppressCamera : CameraTier()
    data class StrongCue(val cue: VisualCue) : CameraTier()
    data object WeakCue : CameraTier()
}

object VisualCueMapper {

    private val autoRedChild = setOf(
        "Unable to drink or breastfeed",
        "Vomiting everything",
        "Convulsions",
        "Lethargic or unconscious",
        "Stridor",
        "Stridor at rest",
        "Severe chest indrawing"
    )

    private val autoRedAntenatal = setOf(
        "Vaginal bleeding",
        "Heavy vaginal bleeding",
        "Convulsions / fits",
        "Fits or convulsions",
        "Absent fetal movement",
        "Fetal movements stopped",
        "Prolonged labour (>24h)",
        "Cord prolapse"
    )

    fun computeTier(
        selectedSymptoms: List<String>,
        pathway: Pathway,
        patientAgeMonths: Int?
    ): CameraTier {
        val symptoms = selectedSymptoms.toSet()

        if (hasAutoRedSign(symptoms, pathway)) {
            return CameraTier.SuppressCamera
        }

        val strongCue = when (pathway) {
            Pathway.CHILD_U5 -> childStrongCue(symptoms, patientAgeMonths)
            Pathway.ANTENATAL -> antenatalStrongCue(symptoms)
        }
        return strongCue?.let(CameraTier::StrongCue) ?: CameraTier.WeakCue
    }

    fun getSuggestion(
        selectedSymptoms: List<String>,
        pathway: Pathway,
        patientAgeMonths: Int?
    ): VisualCue? = when (val tier = computeTier(selectedSymptoms, pathway, patientAgeMonths)) {
        is CameraTier.StrongCue -> tier.cue
        else -> null
    }

    private fun hasAutoRedSign(symptoms: Set<String>, pathway: Pathway): Boolean {
        val autoRedSet = when (pathway) {
            Pathway.CHILD_U5 -> autoRedChild
            Pathway.ANTENATAL -> autoRedAntenatal
        }
        return symptoms.any { it in autoRedSet }
    }

    private fun childStrongCue(symptoms: Set<String>, patientAgeMonths: Int?): VisualCue? {
        if (symptoms.contains("Fever") && symptoms.contains("Lethargic or unconscious")) {
            return VisualCue(
                title = "Photograph the inner lower eyelid",
                reason = "Fever + Lethargy - WHO IMCI: check for conjunctival pallor (anaemia sign)",
                gemmaInstruction = "The CHO has gently pulled down the patient's lower eyelid to expose the inner conjunctival surface and photographed it. Using WHO IMCI visual assessment criteria, assess whether the inner eyelid surface appears pale (white or very light pink) rather than a healthy red-pink colour. Pale conjunctiva is a WHO IMCI sign of severe anaemia in children. Report your finding, confidence level, and clinical implication.",
                findingType = FindingType.ESCALATING,
                triggerSymptoms = listOf("Fever", "Lethargic or unconscious")
            )
        }

        if (symptoms.contains("Diarrhoea") && (symptoms.contains("Vomiting everything") || symptoms.contains("Lethargic or unconscious"))) {
            val triggers = buildList {
                add("Diarrhoea")
                if (symptoms.contains("Vomiting everything")) add("Vomiting everything")
                if (symptoms.contains("Lethargic or unconscious")) add("Lethargic or unconscious")
            }
            return VisualCue(
                title = "Photograph a skin pinch on the abdomen",
                reason = "Diarrhoea + Vomiting/Lethargy - WHO IMCI: check skin turgor for dehydration",
                gemmaInstruction = "The CHO has pinched a small fold of skin on the child's abdominal wall and photographed the moment of release. Using WHO IMCI dehydration criteria, assess whether the skin pinch appears to return slowly, taking more than 2 seconds, or very slowly. Slow skin pinch return indicates some dehydration; very slow indicates severe dehydration. Report your finding.",
                findingType = FindingType.ESCALATING,
                triggerSymptoms = triggers
            )
        }

        if (symptoms.contains("Fast breathing / cough") && symptoms.contains("Severe chest indrawing")) {
            return VisualCue(
                title = "Photograph the bare chest during breathing",
                reason = "Respiratory symptoms - WHO IMCI: confirm chest indrawing visually",
                gemmaInstruction = "The CHO has photographed the child's bare chest during a breath cycle. Using WHO IMCI criteria for severe pneumonia, assess whether the lower chest wall visibly draws inward during inhalation. This is distinct from normal chest rise - look for the lower ribs and sternum pulling in rather than expanding out. Report presence, severity, and confidence.",
                findingType = FindingType.CONFIRMING,
                triggerSymptoms = listOf("Fast breathing / cough", "Severe chest indrawing")
            )
        }

        if (symptoms.contains("Fever") && patientAgeMonths != null && patientAgeMonths < 3) {
            return VisualCue(
                title = "Photograph the fontanelle (top of head)",
                reason = "Young infant with fever - check for sunken fontanelle (dehydration/meningitis sign)",
                gemmaInstruction = "The CHO has photographed the top of an infant's head, specifically the anterior fontanelle (the soft spot). Assess whether the fontanelle appears sunken or depressed relative to the surrounding skull surface. A sunken fontanelle in a febrile infant is a WHO IMCI sign of severe dehydration. A bulging fontanelle may indicate meningitis. Report what you observe.",
                findingType = FindingType.ESCALATING,
                triggerSymptoms = listOf("Fever")
            )
        }

        if (symptoms.contains("Fever")) {
            return VisualCue(
                title = "Photograph the child's palm and inner eyelid",
                reason = "Fever - checking for pallor or jaundice",
                gemmaInstruction = "The CHO has photographed the child's palm or inner eyelid. Assess for palmar pallor - the palm looks pale or white rather than pink, suggesting anaemia - and for yellow discolouration of the skin or whites of the eyes, suggesting jaundice or severe illness.",
                findingType = FindingType.NEW_OBSERVATION,
                triggerSymptoms = listOf("Fever")
            )
        }

        return null
    }

    private fun antenatalStrongCue(symptoms: Set<String>): VisualCue? {
        if (symptoms.contains("Severe headache") && symptoms.contains("Blurred or lost vision")) {
            return VisualCue(
                title = "Photograph both lower legs and ankles",
                reason = "Headache + visual symptoms - WHO ANC: check for oedema (pre-eclampsia sign)",
                gemmaInstruction = "The CHO has photographed the pregnant patient's lower legs and ankles from the front. Assess whether there is visible pitting oedema - bilateral leg swelling appearing puffy, with skin that looks stretched or shiny. Bilateral leg oedema combined with severe headache and visual disturbance is a warning sign of pre-eclampsia. Report presence, extent, and whether it appears bilateral.",
                findingType = FindingType.ESCALATING,
                triggerSymptoms = listOf("Severe headache", "Blurred or lost vision")
            )
        }

        if (symptoms.contains("High fever") && symptoms.size > 1) {
            return VisualCue(
                title = "Photograph the inner eyelid",
                reason = "Fever in pregnancy - check for pallor (anaemia increases obstetric risk)",
                gemmaInstruction = "The CHO has photographed the inner lower eyelid of the pregnant patient. Assess whether the conjunctiva appears pale, white, or very light pink. Anaemia in pregnancy significantly increases risk during labour and delivery. Report your finding and confidence level.",
                findingType = FindingType.ESCALATING,
                triggerSymptoms = listOf("High fever")
            )
        }

        return null
    }
}
