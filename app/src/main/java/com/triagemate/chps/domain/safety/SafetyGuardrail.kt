package com.triagemate.chps.domain.safety

import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.util.isAutoRedSign

data class SafetyOverrideResult(
    val finalUrgency: String,
    val wasOverridden: Boolean,
    val originalGemmaUrgency: String,
    val overrideReason: String?,
    val overriddenSigns: List<String>,
    /** Vital-sign rules that raised urgency above the model's classification. */
    val vitalRuleHits: List<VitalRuleHit> = emptyList()
)

/**
 * Deterministic safety layer applied after the model. It can only raise urgency:
 * checklist danger signs force RED, and [VitalSignRules] set a WHO-based minimum.
 * The most severe applicable level wins.
 */
object SafetyGuardrail {

    fun apply(
        gemmaUrgency: String,
        selectedSymptoms: List<String>,
        pathway: Pathway,
        vitalSigns: Map<String, String> = emptyMap(),
        patientAgeMonths: Int? = null
    ): SafetyOverrideResult {
        val originalRank = rank(gemmaUrgency)

        val triggeredSigns = selectedSymptoms.filter { isAutoRedSign(it, pathway) }
            .takeIf { originalRank < rank("RED") }
            .orEmpty()
        val vitalHits = VitalSignRules.evaluate(pathway, vitalSigns, patientAgeMonths)
            .filter { rank(it.urgency) > originalRank }

        val floors = buildList {
            if (triggeredSigns.isNotEmpty()) add("RED")
            vitalHits.forEach { add(it.urgency) }
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
            vitalHits.forEach { add("Vital-sign rule ${it.ruleId}: ${it.finding}") }
        }
        return SafetyOverrideResult(
            finalUrgency = finalUrgency,
            wasOverridden = true,
            originalGemmaUrgency = gemmaUrgency,
            overrideReason = reasons.joinToString("; "),
            overriddenSigns = triggeredSigns,
            vitalRuleHits = vitalHits
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
