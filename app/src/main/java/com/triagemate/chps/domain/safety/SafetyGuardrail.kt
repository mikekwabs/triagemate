package com.triagemate.chps.domain.safety

import com.triagemate.chps.domain.model.AssessmentExtras
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.util.isAutoRedSign

data class SafetyOverrideResult(
    val finalUrgency: String,
    val wasOverridden: Boolean,
    val originalGemmaUrgency: String,
    val overrideReason: String?,
    val overriddenSigns: List<String>,
    /** Vital-sign and assessment rules that raised urgency above the model's classification. */
    val ruleHits: List<SafetyRuleHit> = emptyList()
)

/**
 * Deterministic safety layer applied after the model. It can only raise urgency:
 * checklist danger signs force RED, and [VitalSignRules] set a WHO-based minimum.
 * [AssessmentRules] add the WHO rules for the optional additional findings.
 * The most severe applicable level wins.
 */
object SafetyGuardrail {

    fun apply(
        gemmaUrgency: String,
        selectedSymptoms: List<String>,
        pathway: Pathway,
        vitalSigns: Map<String, String> = emptyMap(),
        patientAgeMonths: Int? = null,
        extras: AssessmentExtras = AssessmentExtras()
    ): SafetyOverrideResult {
        val originalRank = rank(gemmaUrgency)

        val triggeredSigns = selectedSymptoms.filter { isAutoRedSign(it, pathway) }
            .takeIf { originalRank < rank("RED") }
            .orEmpty()
        val ruleHits = (
            VitalSignRules.evaluate(pathway, vitalSigns, patientAgeMonths) +
                AssessmentRules.evaluate(pathway, selectedSymptoms, extras, patientAgeMonths, vitalSigns)
            ).filter { rank(it.urgency) > originalRank }

        val floors = buildList {
            if (triggeredSigns.isNotEmpty()) add("RED")
            ruleHits.forEach { add(it.urgency) }
        }
        val finalUrgency = floors.maxByOrNull(::rank)

        if (finalUrgency == null) {
            return SafetyOverrideResult(
                finalUrgency = gemmaUrgency,
                wasOverridden = false,
                originalGemmaUrgency = gemmaUrgency,
                overrideReason = null,
                overriddenSigns = emptyList()
            )
        }

        val reasons = buildList {
            if (triggeredSigns.isNotEmpty()) add("WHO danger sign detected: ${triggeredSigns.joinToString(", ")}")
            ruleHits.forEach { add("${it.category} rule ${it.ruleId}: ${it.finding}") }
        }
        return SafetyOverrideResult(
            finalUrgency = finalUrgency,
            wasOverridden = true,
            originalGemmaUrgency = gemmaUrgency,
            overrideReason = reasons.joinToString("; "),
            overriddenSigns = triggeredSigns,
            ruleHits = ruleHits
        )
    }

    /** GREEN < AMBER < RED; anything unrecognised ranks below GREEN so any rule raises it. */
    private fun rank(urgency: String): Int = when (urgency.trim().uppercase()) {
        "RED" -> 2
        "AMBER" -> 1
        "GREEN" -> 0
        else -> -1
    }
}
