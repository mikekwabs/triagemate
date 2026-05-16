package com.triagemate.chps.tools

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

class TriageToolSet : ToolSet {
    @Tool(
        description = "Classify the triage urgency level for a patient assessment. Analyse the symptoms against WHO IMCI guidelines for children under 5 and Ghana Health Service antenatal danger signs protocol. Return the urgency level, recommended action, detected danger signs, and a structured referral note."
    )
    fun classifyTriage(
        @ToolParam(description = "The clinical pathway: CHILD_U5 or ANTENATAL")
        pathway: String,
        @ToolParam(description = "Comma-separated list of presenting symptoms")
        symptoms: String,
        @ToolParam(description = "Patient age: months for children, gestational weeks for antenatal")
        patientAge: String,
        @ToolParam(description = "Assessed urgency: RED, AMBER, or GREEN")
        urgency: String,
        @ToolParam(description = "Recommended clinical action for the CHO, max 2 sentences")
        action: String,
        @ToolParam(description = "Comma-separated danger signs detected from the symptom list")
        dangerSignsDetected: String,
        @ToolParam(description = "Structured referral note: patient summary, danger signs, recommended action, urgency. Max 5 sentences.")
        referralNote: String
    ): Map<String, Any> {
        return mapOf(
            "urgency" to urgency,
            "action" to action,
            "dangerSignsDetected" to dangerSignsDetected.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            "referralNote" to referralNote,
            "pathway" to pathway,
            "patientAge" to patientAge,
            "symptoms" to symptoms
        )
    }
}
