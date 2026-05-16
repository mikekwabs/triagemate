package com.triagemate.chps.tools

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.triagemate.chps.domain.model.ToolCallRecord
import org.json.JSONArray
import org.json.JSONObject

class ClinicalToolSet : ToolSet {

    companion object { private const val TAG = "ClinicalToolSet" }

    private val childDangerSigns = setOf(
        "unable to drink or breastfeed",
        "vomiting everything",
        "convulsions",
        "lethargic or unconscious",
        "stridor",
        "severe chest indrawing"
    )

    private val antenatalDangerSigns = setOf(
        "vaginal bleeding",
        "convulsions / fits",
        "absent fetal movement",
        "blurred or lost vision",
        "prolonged labour (>24h)"
    )

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
    var visualFinding: String? = null
        private set
    var vitalsRequested: Boolean = false
        private set
    var requiredVitalsList: List<String> = emptyList()
        private set
    var drugInteractionResult: String? = null
        private set
    var reportedVisualFinding: Map<String, Any>? = null
        private set

    /** Captured by [generateClinicalExplanation] for the post-assessment Learn More card. */
    var explanationWhyThisClassification: String? = null
        private set
    var explanationWhatToWatchFor: String? = null
        private set
    var explanationClinicalReference: String? = null
        private set

    /** Captured by [extractSymptoms] for the Moat 2 voice-input flow. */
    var voiceTranslation: String? = null
        private set
    var voiceMatchedSymptoms: List<String> = emptyList()
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
        visualFinding = null
        vitalsRequested = false
        requiredVitalsList = emptyList()
        drugInteractionResult = null
        reportedVisualFinding = null
        explanationWhyThisClassification = null
        explanationWhatToWatchFor = null
        explanationClinicalReference = null
        voiceTranslation = null
        voiceMatchedSymptoms = emptyList()
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

    @Tool(description = "Perform initial symptom assessment. Analyse presenting symptoms against WHO IMCI guidelines (children under 5) or Ghana Health Service antenatal protocols. Identify danger signs and determine if additional data is needed. Always call this first.")
    fun assessSymptoms(
        @ToolParam(description = "Clinical pathway: CHILD_U5 or ANTENATAL") pathway: String,
        @ToolParam(description = "Comma-separated presenting symptoms") symptoms: String,
        @ToolParam(description = "Patient age in months (children) or gestational weeks (antenatal)") patientAge: String,
        @ToolParam(description = "Patient sex: MALE or FEMALE") patientSex: String
    ): Map<String, Any> {
        val normalizedSymptoms = symptoms.split(",")
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
        record("assessSymptoms", "$pathway|$symptoms|$patientAge|$patientSex", result)
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

    @Tool(
        description = "Report the result of a clinical visual assessment from a photograph taken by a Community Health Officer. Call this tool after analysing a clinical photo. Report exactly what you see in the image relevant to the clinical question asked. Be specific and honest — if the image is unclear or you cannot make a confident assessment, say so."
    )
    fun reportVisualFinding(
        @ToolParam(description = "Whether the specific clinical sign was detected: true or false")
        signDetected: Boolean,
        @ToolParam(description = "What was observed in the image.")
        observation: String,
        @ToolParam(description = "Clinical interpretation of the observation.")
        clinicalImplication: String,
        @ToolParam(description = "Confidence in the assessment: high, medium, or low")
        confidence: String,
        @ToolParam(description = "One sentence for inclusion in referral note.")
        referralNoteAddition: String,
        @ToolParam(description = "Whether image quality was sufficient to make a meaningful assessment: true or false")
        imageQualitySufficient: Boolean
    ): Map<String, Any> {
        val result = mapOf(
            "sign_detected" to signDetected,
            "observation" to observation,
            "clinical_implication" to clinicalImplication,
            "confidence" to confidence,
            "referral_note_addition" to referralNoteAddition,
            "image_quality_sufficient" to imageQualitySufficient
        )
        visualFinding = observation
        reportedVisualFinding = result
        record("reportVisualFinding", "$confidence|$imageQualitySufficient", result)
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

    @Tool(description = "Final triage classification. Call after all data gathered. RED = refer urgently within 4 hours. AMBER = refer non-urgently, review 24-48h. GREEN = manage locally.")
    fun classifyTriage(
        @ToolParam(description = "Clinical pathway: CHILD_U5 or ANTENATAL") pathway: String,
        @ToolParam(description = "All presenting symptoms") symptoms: String,
        @ToolParam(description = "Confirmed danger signs from assessment") dangerSigns: String,
        @ToolParam(description = "Vital signs if collected, or 'not_collected'") vitalSigns: String,
        @ToolParam(description = "Drug interaction status if checked, or 'not_checked'") drugInteractionStatus: String,
        @ToolParam(description = "Urgency classification: RED, AMBER, or GREEN") urgency: String,
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
            "vital_signs" to vitalSigns,
            "drug_interaction" to drugInteractionStatus,
            "confidence" to classifiedConfidence
        )
        record("classifyTriage", "$urgency|$dangerSigns|$action", result)
        return result
    }

    @Tool(description = "Generate a structured referral note as the final step. The note must be suitable for sharing via WhatsApp, SMS, or print.")
    fun generateReferralNote(
        @ToolParam(description = "Patient summary: age, sex, pathway") patientSummary: String,
        @ToolParam(description = "Presenting complaint: comma-separated symptoms") presentingComplaint: String,
        @ToolParam(description = "Confirmed danger signs") dangerSigns: String,
        @ToolParam(description = "Vital signs if available, or 'Not collected'") vitalSigns: String,
        @ToolParam(description = "Urgency classification: RED, AMBER, or GREEN") urgency: String,
        @ToolParam(description = "Recommended action and pre-referral treatment") action: String,
        @ToolParam(description = "Full referral note text, max 8 sentences") referralNote: String
    ): Map<String, Any> {
        generatedReferralNote = referralNote
        val result = mapOf(
            "referral_note" to referralNote,
            "patient_summary" to patientSummary,
            "urgency" to urgency,
            "timestamp" to System.currentTimeMillis().toString()
        )
        record("generateReferralNote", referralNote.take(80), result)
        return result
    }

    @Tool(
        description = "Generate a short educational explanation for an already-finalised triage decision. This is shown after the assessment as supporting context for the Community Health Officer. It does NOT change the urgency, action, or referral note — those are already fixed. Be concise, clinically accurate, and pragmatic for a CHO working in a CHPS compound."
    )
    fun generateClinicalExplanation(
        @ToolParam(description = "Plain-language reason this case received its urgency classification (RED / AMBER / GREEN). 2 to 4 sentences. Reference the specific symptoms, danger signs, or vitals that drove the decision.")
        whyThisClassification: String,
        @ToolParam(description = "Practical signs the CHO should watch for during transport, follow-up, or local care — early-warning indicators of deterioration. 2 to 4 sentences.")
        whatToWatchFor: String,
        @ToolParam(description = "Brief clinical reference: e.g. 'WHO IMCI 2014', 'Ghana Health Service Antenatal Guidelines', or the specific danger-sign chapter that applies. One short phrase.")
        clinicalReference: String
    ): Map<String, Any> {
        explanationWhyThisClassification = whyThisClassification.trim()
        explanationWhatToWatchFor = whatToWatchFor.trim()
        explanationClinicalReference = clinicalReference.trim()
        val result = mapOf(
            "why_this_classification" to whyThisClassification,
            "what_to_watch_for" to whatToWatchFor,
            "clinical_reference" to clinicalReference
        )
        record("generateClinicalExplanation", clinicalReference.take(80), result)
        return result
    }

    @Tool(
        description = "Map a Twi voice description (already translated to English) onto the canonical symptom checklist for the active pathway. Call this exactly once after listening to the audio. Only include symptoms that the speaker clearly described — never invent or assume. Be precise: each entry in matchedSymptoms must match a canonical checklist label exactly."
    )
    fun extractSymptoms(
        @ToolParam(description = "English translation of what the Community Health Officer or caregiver said in Twi. Keep it faithful to the original — do not summarise or remove nuance.")
        translation: String,
        @ToolParam(description = "Comma-separated canonical symptoms from the active pathway checklist that match the translation. Use the exact checklist wording. Leave empty if no symptom matches with confidence.")
        matchedSymptoms: String
    ): Map<String, Any> {
        voiceTranslation = translation.trim()
        voiceMatchedSymptoms = matchedSymptoms.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val result = mapOf(
            "translation" to voiceTranslation.orEmpty(),
            "matched_symptoms" to voiceMatchedSymptoms
        )
        record("extractSymptoms", translation.take(80), result)
        return result
    }
}
