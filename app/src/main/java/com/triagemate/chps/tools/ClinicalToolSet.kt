package com.triagemate.chps.tools

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.triagemate.chps.domain.model.ToolCallRecord
import com.triagemate.chps.util.AUTO_RED_ANTENATAL
import com.triagemate.chps.util.AUTO_RED_CHILD_U5
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tools exposed to the agentic triage conversation, plus the case state they collect.
 * Every @Tool here has its schema prefilled at the start of each triage, so only the
 * tools the model actually needs belong here: photo and explanation tools live in
 * [VisualToolSet] and [ExplanationToolSet]. Deterministic steps are run by the app
 * instead of the model: [assessSymptoms] before the first turn, [recordReferralNote]
 * after classification.
 */
class ClinicalToolSet : ToolSet {

    companion object { private const val TAG = "ClinicalToolSet" }

    private val childDangerSigns = AUTO_RED_CHILD_U5.map { it.lowercase() }.toSet()

    // Reported to the model as a danger sign but deliberately NOT auto-RED:
    // the guardrail does not force RED on visual disturbance alone.
    private val antenatalDangerSigns =
        (AUTO_RED_ANTENATAL + "Blurred or lost vision").map { it.lowercase() }.toSet()

    private val toolCallLogInternal = mutableListOf<ToolCallRecord>()
    private var roundInternal = 0

    var classifiedUrgency: String? = null
        private set
    var classifiedAction: String? = null
        private set
    var classifiedDangerSigns: List<String> = emptyList()
        private set
    var classifiedConfidence: String = "HIGH"
        private set
    var generatedReferralNote: String? = null
        private set
    var vitalsRequested: Boolean = false
        private set
    var requiredVitalsList: List<String> = emptyList()
        private set
    var drugInteractionResult: String? = null
        private set
    /** Result of the pre-run [assessSymptoms] for the current case. */
    var lastAssessment: Map<String, Any>? = null
        private set

    private var suppliedVitals: Map<String, String>? = null

    val toolCallLog: List<ToolCallRecord> get() = toolCallLogInternal.toList()
    val rounds: Int get() = roundInternal

    fun reset() {
        toolCallLogInternal.clear()
        roundInternal = 0
        classifiedUrgency = null
        classifiedAction = null
        classifiedDangerSigns = emptyList()
        classifiedConfidence = "HIGH"
        generatedReferralNote = null
        vitalsRequested = false
        requiredVitalsList = emptyList()
        drugInteractionResult = null
        lastAssessment = null
        suppliedVitals = null
    }

    fun supplyVitals(vitals: Map<String, String>) {
        suppliedVitals = vitals
    }

    private fun record(name: String, args: String, result: Any? = null) {
        roundInternal++
        toolCallLogInternal.add(
            ToolCallRecord(
                round = roundInternal,
                toolName = name,
                arguments = args,
                timestamp = System.currentTimeMillis(),
                result = serializeResult(result)
            )
        )
        Log.d(TAG, "round=$roundInternal tool=$name")
    }

    fun recordPendingVitalRequest(requiredVitals: List<String>) {
        vitalsRequested = true
        requiredVitalsList = requiredVitals
        record("requestVitalSigns", requiredVitals.joinToString(","))
    }

    fun completePendingVitalRequest(response: Map<String, Any?>) {
        val lastIndex = toolCallLogInternal.indexOfLast { it.toolName == "requestVitalSigns" }
        if (lastIndex == -1) return
        toolCallLogInternal[lastIndex] = toolCallLogInternal[lastIndex].copy(
            result = serializeResult(response)
        )
    }

    fun recordSkippedToolCall(toolName: String, arguments: String, result: String) {
        record("${toolName}_SKIPPED", arguments, result)
    }

    private fun serializeResult(result: Any?): String? = when (result) {
        null -> null
        is String -> result
        is Map<*, *> -> JSONObject(result).toString()
        is Collection<*> -> JSONArray(result).toString()
        else -> result.toString()
    }

    /**
     * Rule-based symptom assessment. Not a @Tool: the app runs it before the first model
     * turn and puts the result in the patient prompt, which saves the model a full round
     * (generating the call, then reading the response). Takes a list because checklist
     * labels can contain commas ("Swollen face, hands or feet").
     */
    fun assessSymptoms(
        pathway: String,
        symptoms: List<String>,
        patientAge: String,
        patientSex: String
    ): Map<String, Any> {
        val normalizedSymptoms = symptoms
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val dangerSigns = detectDangerSigns(pathway, normalizedSymptoms)
        val preliminarySeverity = when {
            dangerSigns.isNotEmpty() -> "HIGH"
            shouldSuggestVitals(pathway, normalizedSymptoms) -> "MODERATE"
            else -> "LOW"
        }

        val result = mapOf(
            "preliminary_severity" to preliminarySeverity,
            "danger_signs" to dangerSigns,
            "symptom_count" to normalizedSymptoms.size,
            "clinical_summary" to buildClinicalSummary(pathway, normalizedSymptoms, dangerSigns, preliminarySeverity),
            "pathway" to pathway,
            "patient_age" to patientAge,
            "patient_sex" to patientSex
        )
        lastAssessment = result
        record("assessSymptoms", "$pathway|${normalizedSymptoms.joinToString(",")}|$patientAge|$patientSex", result)
        return result
    }

    private fun detectDangerSigns(pathway: String, symptoms: List<String>): List<String> {
        val referenceSet = when (pathway.uppercase()) {
            "ANTENATAL" -> antenatalDangerSigns
            else -> childDangerSigns
        }
        return symptoms.filter { symptom ->
            val normalized = symptom.lowercase()
            referenceSet.any { normalized.contains(it) }
        }
    }

    private fun shouldSuggestVitals(pathway: String, symptoms: List<String>): Boolean {
        val normalized = symptoms.map { it.lowercase() }
        return when (pathway.uppercase()) {
            "ANTENATAL" -> normalized.any {
                it.contains("high fever") ||
                    it.contains("difficulty breathing") ||
                    it.contains("swollen face") ||
                    it.contains("swollen hands") ||
                    it.contains("swollen feet") ||
                    it.contains("severe headache")
            }

            else -> normalized.any {
                it.contains("fever") ||
                    it.contains("fast breathing") ||
                    it.contains("cough") ||
                    it.contains("diarrhoea")
            }
        }
    }

    private fun buildClinicalSummary(
        pathway: String,
        symptoms: List<String>,
        dangerSigns: List<String>,
        preliminarySeverity: String
    ): String {
        val symptomSummary = symptoms.joinToString(", ").ifEmpty { "no symptoms recorded" }
        val dangerSummary = if (dangerSigns.isNotEmpty()) {
            "Danger signs identified: ${dangerSigns.joinToString(", ")}."
        } else {
            "No automatic danger signs identified from the checklist."
        }
        val vitalsGuidance = if (shouldSuggestVitals(pathway, symptoms)) {
            "Vital signs may help refine triage if clinically available."
        } else {
            "Vital signs are optional and should only be requested if they would change triage confidence."
        }
        return "Symptoms: $symptomSummary. $dangerSummary Preliminary severity: $preliminarySeverity. $vitalsGuidance"
    }

    @Tool(description = "Request vital sign measurements from the CHO. Call when vital signs would improve triage accuracy. Returns available vitals if previously supplied, otherwise notes they are not collected.")
    fun requestVitalSigns(
        @ToolParam(description = "Comma-separated vital signs needed: temperature, respiratory_rate, pulse, blood_pressure, oxygen_saturation") requiredVitals: String
    ): Map<String, Any> {
        vitalsRequested = true
        requiredVitalsList = requiredVitals.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val result = suppliedVitals?.let { vitals ->
            mapOf("action" to "VITALS_PROVIDED", "vital_signs" to vitals)
        } ?: mapOf(
            "action" to "VITALS_NOT_AVAILABLE",
            "required_vitals" to requiredVitalsList,
            "note" to "Vital signs were not collected. Proceed with available clinical data."
        )

        record("requestVitalSigns", requiredVitals, result)
        return result
    }

    @Tool(description = "Check drug interactions between current medications and proposed pre-referral treatment. Call when the patient is on medication or the treatment has known contraindications.")
    fun checkDrugInteraction(
        @ToolParam(description = "Comma-separated current medications") currentMedications: String,
        @ToolParam(description = "Proposed treatment or pre-referral medication") proposedTreatment: String,
        @ToolParam(description = "Patient age in months or gestational weeks") patientAge: String
    ): Map<String, Any> {
        drugInteractionResult = "none"
        val result = mapOf(
            "interaction_risk" to "none",
            "alternative" to "",
            "rationale" to "No known interactions",
            "current_medications" to currentMedications,
            "proposed_treatment" to proposedTreatment
        )
        record("checkDrugInteraction", "$currentMedications|$proposedTreatment|$patientAge", result)
        return result
    }

    @Tool(description = "Final triage classification and the LAST tool call. Call after all data gathered; the app writes the referral note from these fields. RED = refer urgently within 4 hours. AMBER = refer non-urgently, review 24-48h. GREEN = manage locally.")
    // Only the decisions the model makes. Pathway, symptoms, vitals and drug-check status
    // are already known to the app; asking the model to restate them cost decode tokens.
    fun classifyTriage(
        @ToolParam(description = "Urgency classification: RED, AMBER, or GREEN") urgency: String,
        @ToolParam(description = "Comma-separated danger signs confirmed for this patient, or 'none'") dangerSigns: String,
        @ToolParam(description = "Recommended action for the CHO, max 2 sentences") action: String,
        @ToolParam(description = "Confidence in this classification: HIGH, MEDIUM, or LOW") confidence: String = "HIGH"
    ): Map<String, Any> {
        classifiedUrgency = urgency
        classifiedAction = action
        classifiedDangerSigns = dangerSigns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        classifiedConfidence = confidence.uppercase().ifBlank { "HIGH" }
        val result = mapOf(
            "urgency" to urgency,
            "action" to action,
            "danger_signs" to classifiedDangerSigns,
            "vital_signs" to (suppliedVitals?.filterValues(String::isNotBlank)?.takeIf { it.isNotEmpty() } ?: "not_collected"),
            "drug_interaction" to (drugInteractionResult ?: "not_checked"),
            "confidence" to classifiedConfidence
        )
        record("classifyTriage", "$urgency|$dangerSigns|$action", result)
        return result
    }

    /**
     * Stores the app-built referral note. Not a @Tool: the model no longer writes the
     * note (it was the most expensive decode in the loop). Recorded under the old tool
     * name so the activity log and audit trail are unchanged.
     */
    fun recordReferralNote(referralNote: String) {
        generatedReferralNote = referralNote
        val result = mapOf(
            "referral_note" to referralNote,
            "source" to "template",
            "timestamp" to System.currentTimeMillis().toString()
        )
        record("generateReferralNote", referralNote.take(80), result)
    }
}
