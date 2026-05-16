package com.triagemate.chps.domain.safety

import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.util.isAutoRedSign

data class SafetyOverrideResult(
    val finalUrgency: String,
    val wasOverridden: Boolean,
    val originalGemmaUrgency: String,
    val overrideReason: String?,
    val overriddenSigns: List<String>
)

object SafetyGuardrail {

    fun apply(
        gemmaUrgency: String,
        selectedSymptoms: List<String>,
        pathway: Pathway
    ): SafetyOverrideResult {
        val triggeredSigns = selectedSymptoms.filter { isAutoRedSign(it, pathway) }
        val shouldBeRed = triggeredSigns.isNotEmpty()
        val gemmaWasWrong = shouldBeRed && gemmaUrgency != "RED"

        return if (gemmaWasWrong) {
            SafetyOverrideResult(
                finalUrgency = "RED",
                wasOverridden = true,
                originalGemmaUrgency = gemmaUrgency,
                overrideReason = "WHO danger sign detected: ${triggeredSigns.joinToString(", ")}",
                overriddenSigns = triggeredSigns
            )
        } else {
            SafetyOverrideResult(
                finalUrgency = gemmaUrgency,
                wasOverridden = false,
                originalGemmaUrgency = gemmaUrgency,
                overrideReason = null,
                overriddenSigns = emptyList()
            )
        }
    }
}
