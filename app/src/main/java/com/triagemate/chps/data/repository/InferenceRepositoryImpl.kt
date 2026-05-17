package com.triagemate.chps.data.repository

import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.tool
import com.triagemate.chps.data.engine.EngineProvider
import com.triagemate.chps.domain.model.AgenticStatus
import com.triagemate.chps.domain.model.AgenticTriageResult
import com.triagemate.chps.domain.model.ClinicalExplanation
import com.triagemate.chps.domain.model.ConfidenceLevel
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.domain.model.TriageInput
import com.triagemate.chps.domain.model.TriageResult
import com.triagemate.chps.domain.model.VisualFinding
import com.triagemate.chps.domain.model.VoiceInputResult
import com.triagemate.chps.util.Constants
import com.triagemate.chps.domain.repository.InferenceRepository
import com.triagemate.chps.domain.safety.SafetyGuardrail
import com.triagemate.chps.domain.safety.SafetyOverrideResult
import com.triagemate.chps.tools.ClinicalToolSet
import com.triagemate.chps.util.PromptBuilder
import com.triagemate.chps.util.VisualCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InferenceRepositoryImpl @Inject constructor(
    private val engineProvider: EngineProvider
) : InferenceRepository {

    companion object {
        private const val TAG = "InferenceRepo"
        private const val MAX_LOOP_MESSAGES = 8
        private const val REQUEST_VITALS_TOOL = "requestVitalSigns"
        private val DEFAULT_VITALS = listOf(
            "temperature",
            "respiratory_rate",
            "pulse",
            "blood_pressure",
            "oxygen_saturation"
        )
        private val AUTO_RED_CHILD = setOf(
            "Unable to drink or breastfeed",
            "Vomiting everything",
            "Convulsions",
            "Lethargic or unconscious",
            "Stridor",
            "Stridor at rest",
            "Severe chest indrawing"
        )
        private val AUTO_RED_ANTENATAL = setOf(
            "Vaginal bleeding",
            "Heavy vaginal bleeding",
            "Convulsions / fits",
            "Fits or convulsions",
            "Absent fetal movement",
            "Fetal movements stopped",
            "Prolonged labour (>24h)",
            "Cord prolapse"
        )
    }

    private data class PausedSession(
        val conversation: Conversation,
        val input: TriageInput,
        val pendingVitalToolCall: ToolCall
    )

    private val clinicalToolSet = ClinicalToolSet()
    private val mutex = Mutex()
    private var pausedSession: PausedSession? = null

    override suspend fun analyseVisualSign(
        imageUri: Uri,
        visualCue: VisualCue,
        pathway: Pathway,
        selectedSymptoms: List<String>
    ): VisualFinding = withContext(Dispatchers.IO) {
        if (!engineProvider.ensureEngineReady()) {
            return@withContext visualFallback("Model not available.")
        }

        val imagePath = imageUri.path ?: return@withContext visualFallback("Image path unavailable.")
        val visualToolSet = ClinicalToolSet()
        val conversation = engineProvider.engine!!.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "You are a clinical visual assessment assistant for Community Health Officers in Ghana. When given a clinical photo and context, assess the specific visual sign requested and return your finding using the reportVisualFinding tool. Never respond with plain text. Always call the tool."
                ),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.1),
                tools = listOf(tool(visualToolSet)),
                automaticToolCalling = false
            )
        )

        try {
            val message = conversation.sendMessage(
                Contents.of(
                    Content.ImageFile(imagePath),
                    Content.Text(PromptBuilder.buildVisualPrompt(visualCue, selectedSymptoms, pathway))
                )
            )

            val toolCall = message.toolCalls.firstOrNull()
                ?: return@withContext visualFallback("Tool not called.")
            if (canonicalToolName(toolCall.name) != "reportVisualFinding") {
                return@withContext visualFallback("Unexpected visual tool.")
            }

            val args = toolCall.arguments
            val signDetected = argBoolean(args, "signDetected")
            val observation = argString(args, "observation")
            val implication = argString(args, "clinicalImplication")
            val confidence = confidenceLevelFromString(argString(args, "confidence"))
            val referralText = argString(args, "referralNoteAddition")
            val qualitySufficient = argBoolean(args, "imageQualitySufficient")

            return@withContext if (!qualitySufficient && !signDetected) {
                visualFallback("Image quality insufficient.")
            } else {
                VisualFinding(
                    detected = signDetected,
                    findingText = observation.ifBlank { "No additional visual finding detected" },
                    clinicalImplication = implication.ifBlank { "Proceed with symptom-based assessment only" },
                    referralNoteText = referralText.ifBlank {
                        if (signDetected) observation else "Photo reviewed with no confirmed visual finding."
                    },
                    findingType = visualCue.findingType,
                    confidence = confidence,
                    imageQualitySufficient = qualitySufficient
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "analyseVisualSign: ${e.message}", e)
            visualFallback(e.message ?: "Visual analysis failed")
        } finally {
            closeConversationQuietly(conversation)
        }
    }

    override suspend fun runTriage(input: TriageInput): AgenticTriageResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            closePausedSession()
            clinicalToolSet.reset()

            if (!engineProvider.ensureEngineReady()) {
                throw IllegalStateException("Model not available. Please download the model file first.")
            }

            val conversation = engineProvider.engine!!.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(PromptBuilder.buildSystemPrompt()),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.1),
                    tools = listOf(tool(clinicalToolSet)),
                    automaticToolCalling = false
                )
            )

            try {
                Log.d(TAG, "runTriage: START pathway=${input.pathway}")
                val initialMessage = conversation.sendMessage(PromptBuilder.buildUserPrompt(input))
                continueConversation(
                    conversation = conversation,
                    input = input,
                    initialMessage = initialMessage,
                    suppliedVitals = null
                )
            } catch (e: Exception) {
                closeConversationQuietly(conversation)
                Log.e(TAG, "runTriage: EXCEPTION — ${e.message}", e)
                if (isToolCallParseError(e)) {
                    Log.w(TAG, "runTriage: tool-call parse error — applying rule-based fallback")
                    recoverFromParseError(input, suppliedVitals = null)
                } else {
                    errorResult("Error: ${e.message}", input)
                }
            }
        }
    }

    override suspend fun resumeWithVitals(vitalSigns: Map<String, String>): AgenticTriageResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = pausedSession
            if (session == null || !session.conversation.isAlive) {
                closePausedSession()
                return@withLock errorResult("No paused assessment is available to resume.", null)
            }

            try {
                val toolResponsePayload = buildVitalToolResponse(
                    vitalSigns = vitalSigns,
                    requiredVitals = parseRequiredVitals(session.pendingVitalToolCall)
                )
                clinicalToolSet.completePendingVitalRequest(toolResponsePayload)
                clinicalToolSet.supplyVitals(vitalSigns)

                val modelMessage = session.conversation.sendMessage(
                    Message.tool(
                        Contents.of(
                            Content.ToolResponse(
                                session.pendingVitalToolCall.name,
                                toolResponsePayload
                            )
                        )
                    )
                )

                continueConversation(
                    conversation = session.conversation,
                    input = session.input,
                    initialMessage = modelMessage,
                    suppliedVitals = vitalSigns
                )
            } catch (e: Exception) {
                Log.e(TAG, "resumeWithVitals: EXCEPTION — ${e.message}", e)
                closePausedSession()
                if (isToolCallParseError(e)) {
                    Log.w(TAG, "resumeWithVitals: tool-call parse error — applying rule-based fallback")
                    recoverFromParseError(session.input, suppliedVitals = vitalSigns)
                } else {
                    errorResult("Error: ${e.message}", session.input)
                }
            }
        }
    }

    override suspend fun generateClinicalExplanation(
        result: TriageResult,
        input: TriageInput,
        safetyOverride: SafetyOverrideResult?
    ): ClinicalExplanation = withContext(Dispatchers.IO) {
        if (!engineProvider.ensureEngineReady()) {
            return@withContext fallbackExplanation(result, "Model is not available on this device.")
        }

        val explanationToolSet = ClinicalToolSet()
        val conversation = engineProvider.engine!!.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(PromptBuilder.buildExplanationSystemPrompt()),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.4),
                tools = listOf(tool(explanationToolSet)),
                automaticToolCalling = false
            )
        )

        try {
            val message = conversation.sendMessage(
                PromptBuilder.buildExplanationPrompt(result, input, safetyOverride)
            )
            val toolCall = message.toolCalls.firstOrNull()
            if (toolCall == null || canonicalToolName(toolCall.name) != "generateClinicalExplanation") {
                Log.w(TAG, "generateClinicalExplanation: tool was not called — using fallback")
                return@withContext fallbackExplanation(result, "The model did not produce a structured explanation.")
            }

            val args = toolCall.arguments
            val why = argString(args, "whyThisClassification").trim()
            val watch = argString(args, "whatToWatchFor").trim()
            val reference = argString(args, "clinicalReference").trim()

            ClinicalExplanation(
                whyThisClassification = why.ifBlank { defaultWhy(result) },
                whatToWatchFor = watch.ifBlank { defaultWatchFor(result) },
                clinicalReference = reference.ifBlank { defaultReference(input.pathway) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "generateClinicalExplanation: ${e.message}", e)
            fallbackExplanation(result, "The explanation could not be generated (${e.message ?: "unknown error"}).")
        } finally {
            closeConversationQuietly(conversation)
        }
    }

    override suspend fun processVoiceInput(
        audioBytes: ByteArray,
        pathway: Pathway
    ): VoiceInputResult = withContext(Dispatchers.IO) {
        if (audioBytes.isEmpty()) {
            return@withContext VoiceInputResult.Error("No audio was captured. Please try again.")
        }
        if (!engineProvider.ensureEngineReady()) {
            return@withContext VoiceInputResult.Error("Model is not available on this device.")
        }

        val canonicalSymptoms = when (pathway) {
            Pathway.CHILD_U5 -> Constants.CHILD_U5_SYMPTOMS
            Pathway.ANTENATAL -> Constants.ANTENATAL_SYMPTOMS
        }

        val conversation = engineProvider.engine!!.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(PromptBuilder.buildVoiceSystemPrompt()),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2),
                automaticToolCalling = false
            )
        )

        try {
            val message = conversation.sendMessage(
                Contents.of(
                    Content.AudioBytes(audioBytes),
                    Content.Text(PromptBuilder.buildVoicePrompt(pathway, canonicalSymptoms))
                )
            )

            val text = message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(" ") { it.text }
                .trim()

            Log.d(TAG, "processVoiceInput: raw translation='${text.take(200)}'")

            when {
                text.isBlank() -> VoiceInputResult.Error(
                    "The model did not return a translation. Please try again."
                )
                text.equals("UNCLEAR", ignoreCase = true) -> VoiceInputResult.Error(
                    "The audio was unclear. Try again in a quieter spot and speak closer to the phone."
                )
                else -> VoiceInputResult.Success(
                    rawTranslation = text,
                    extractedSymptoms = fuzzyMatchCanonical(text, canonicalSymptoms)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "processVoiceInput: ${e.message}", e)
            VoiceInputResult.Error(e.message ?: "Voice processing failed.")
        } finally {
            closeConversationQuietly(conversation)
        }
    }

    private fun fuzzyMatchCanonical(
        translation: String,
        canonicalSymptoms: List<String>
    ): List<String> {
        val lower = translation.lowercase()
        return canonicalSymptoms.filter { canonical ->
            val canonicalLower = canonical.lowercase()
            if (lower.contains(canonicalLower)) return@filter true
            val tokens = canonicalLower
                .split(" ", "/", "-", "(", ")", ",")
                .map { it.trim() }
                .filter { it.length >= 5 }
            tokens.isNotEmpty() && tokens.any { lower.contains(it) }
        }.distinct()
    }

    private fun fallbackExplanation(result: TriageResult, reason: String): ClinicalExplanation {
        return ClinicalExplanation(
            whyThisClassification = "${defaultWhy(result)} ($reason)",
            whatToWatchFor = defaultWatchFor(result),
            clinicalReference = "WHO IMCI / Ghana Health Service guidelines"
        )
    }

    private fun defaultWhy(result: TriageResult): String {
        val signs = result.dangerSignsDetected.joinToString(", ").ifBlank { "the reported symptoms" }
        return when (result.urgency.uppercase()) {
            "RED" -> "This case was classified as RED because of $signs, which require urgent referral."
            "AMBER" -> "This case was classified as AMBER based on $signs; closer review is needed but it is not an immediate emergency."
            else -> "This case was classified as ${result.urgency} based on $signs."
        }
    }

    private fun defaultWatchFor(result: TriageResult): String {
        return when (result.urgency.uppercase()) {
            "RED" -> "Watch for worsening breathing, fits, drop in consciousness, or any new danger sign during transport."
            "AMBER" -> "Watch for any new danger sign, persistent fever, or worsening of current symptoms over the next 24 hours."
            else -> "Watch for any new danger signs and return to the CHPS compound if symptoms worsen."
        }
    }

    private fun defaultReference(pathway: Pathway): String = when (pathway) {
        Pathway.CHILD_U5 -> "WHO IMCI 2014 — danger signs chapter"
        Pathway.ANTENATAL -> "Ghana Health Service Antenatal Care Guidelines"
    }

    private fun continueConversation(
        conversation: Conversation,
        input: TriageInput,
        initialMessage: Message,
        suppliedVitals: Map<String, String>?
    ): AgenticTriageResult {
        var currentMessage = initialMessage

        repeat(MAX_LOOP_MESSAGES) {
            val toolCalls = currentMessage.toolCalls
            if (toolCalls.isEmpty()) {
                closePausedSession()
                closeConversationQuietly(conversation)
                return buildCompleteResult(suppliedVitals, input)
            }

            val autoRed = hasAutoRedSign(input)
            val toolResponses = mutableListOf<Content.ToolResponse>()

            var referralNoteJustGenerated = false

            for (toolCall in toolCalls) {
                when {
                    isVitalsRequestTool(toolCall.name) -> {
                        if (autoRed) {
                            val skipResponse = mapOf(
                                "action" to "SKIPPED",
                                "reason" to "Auto-RED danger sign present. Classify immediately as RED without vitals."
                            )
                            clinicalToolSet.recordSkippedToolCall(
                                "requestVitalSigns",
                                "Auto-skipped: auto-RED danger sign present",
                                "Overridden by safety guardrail"
                            )
                            toolResponses.add(Content.ToolResponse(toolCall.name, skipResponse))
                        } else {
                            val requiredVitals = parseRequiredVitals(toolCall)
                            clinicalToolSet.recordPendingVitalRequest(requiredVitals)
                            pausedSession = PausedSession(conversation, input, toolCall)
                            return AgenticTriageResult(
                                status = AgenticStatus.AWAITING_VITALS,
                                triageResult = buildCurrentTriageSnapshot(input),
                                referralNote = clinicalToolSet.generatedReferralNote,
                                visualFinding = clinicalToolSet.visualFinding,
                                confirmedVisualFinding = input.confirmedVisualFinding,
                                requiredVitals = requiredVitals,
                                toolCallLog = clinicalToolSet.toolCallLog,
                                currentRound = clinicalToolSet.rounds
                            )
                        }
                    }

                    autoRed && isDrugInteractionTool(toolCall.name) -> {
                        clinicalToolSet.recordSkippedToolCall(
                            "checkDrugInteraction",
                            "Auto-skipped: auto-RED danger sign present",
                            "Overridden by safety guardrail"
                        )
                        toolResponses.add(
                            Content.ToolResponse(
                                toolCall.name,
                                mapOf(
                                    "action" to "SKIPPED",
                                    "reason" to "Auto-RED danger sign present. Proceed directly to RED classification."
                                )
                            )
                        )
                    }

                    else -> {
                        val result = executeToolCall(toolCall)
                        toolResponses.add(Content.ToolResponse(toolCall.name, result))
                        if (canonicalToolName(toolCall.name) == "generateReferralNote") {
                            referralNoteJustGenerated = true
                        }
                    }
                }
            }

            if (referralNoteJustGenerated && clinicalToolSet.classifiedUrgency != null) {
                Log.d(TAG, "continueConversation: generateReferralNote complete — finishing without another model round-trip")
                closePausedSession()
                closeConversationQuietly(conversation)
                return buildCompleteResult(suppliedVitals, input)
            }

            currentMessage = try {
                conversation.sendMessage(Message.tool(Contents.of(toolResponses)))
            } catch (e: Exception) {
                if (isToolCallParseError(e)) {
                    Log.w(TAG, "continueConversation: malformed tool call — recovering (classifiedUrgency=${clinicalToolSet.classifiedUrgency})")
                    closePausedSession()
                    closeConversationQuietly(conversation)
                    return recoverFromParseError(input, suppliedVitals)
                }
                throw e
            }
        }

        closePausedSession()
        closeConversationQuietly(conversation)
        return errorResult("Model exceeded the maximum tool-call rounds.", input)
    }

    private fun executeToolCall(toolCall: ToolCall): Map<String, Any> {
        val args = toolCall.arguments
        return when (canonicalToolName(toolCall.name)) {
            "assessSymptoms" -> clinicalToolSet.assessSymptoms(
                pathway = argString(args, "pathway"),
                symptoms = argString(args, "symptoms"),
                patientAge = argString(args, "patientAge"),
                patientSex = argString(args, "patientSex")
            )

            "checkDrugInteraction" -> clinicalToolSet.checkDrugInteraction(
                currentMedications = argString(args, "currentMedications"),
                proposedTreatment = argString(args, "proposedTreatment"),
                patientAge = argString(args, "patientAge")
            )

            "classifyTriage" -> clinicalToolSet.classifyTriage(
                pathway = argString(args, "pathway"),
                symptoms = argString(args, "symptoms"),
                dangerSigns = argString(args, "dangerSigns"),
                vitalSigns = argString(args, "vitalSigns"),
                drugInteractionStatus = argString(args, "drugInteractionStatus"),
                urgency = argString(args, "urgency"),
                action = argString(args, "action"),
                confidence = argString(args, "confidence").ifBlank { "HIGH" }
            )

            "generateReferralNote" -> clinicalToolSet.generateReferralNote(
                patientSummary = argString(args, "patientSummary"),
                presentingComplaint = argString(args, "presentingComplaint"),
                dangerSigns = argString(args, "dangerSigns"),
                vitalSigns = argString(args, "vitalSigns"),
                urgency = argString(args, "urgency"),
                action = argString(args, "action"),
                referralNote = argString(args, "referralNote")
            )

            else -> {
                Log.w(TAG, "executeToolCall: unknown tool '${toolCall.name}' — returning corrective response")
                clinicalToolSet.recordSkippedToolCall(
                    toolCall.name,
                    "Unknown tool — not available in the triage loop",
                    "Model asked for a tool outside the allowed set"
                )
                mapOf(
                    "action" to "TOOL_NOT_AVAILABLE",
                    "called_tool" to toolCall.name,
                    "available_tools" to listOf(
                        "assessSymptoms",
                        "requestVitalSigns",
                        "checkDrugInteraction",
                        "classifyTriage",
                        "generateReferralNote"
                    ),
                    "next_step" to "Call classifyTriage if not yet called, then generateReferralNote to finish."
                )
            }
        }
    }

    private fun canonicalToolName(name: String): String = when (name) {
        "assessSymptoms", "assess_symptoms" -> "assessSymptoms"
        "reportVisualFinding", "report_visual_finding" -> "reportVisualFinding"
        "requestVitalSigns", "request_vital_signs" -> "requestVitalSigns"
        "checkDrugInteraction", "check_drug_interaction" -> "checkDrugInteraction"
        "classifyTriage", "classify_triage" -> "classifyTriage"
        "generateReferralNote", "generate_referral_note" -> "generateReferralNote"
        "generateClinicalExplanation", "generate_clinical_explanation" -> "generateClinicalExplanation"
        else -> name
    }

    private fun isVitalsRequestTool(name: String): Boolean = canonicalToolName(name) == REQUEST_VITALS_TOOL

    private fun isDrugInteractionTool(name: String): Boolean = canonicalToolName(name) == "checkDrugInteraction"

    private fun isToolCallParseError(e: Throwable): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("Failed to parse tool calls", ignoreCase = true) ||
            msg.contains("Failed to parse FC tool calls", ignoreCase = true)
    }

    /**
     * Recovery path when LiteRT cannot parse the model's tool call (e.g. unquoted values,
     * quoted keys, malformed parameter names). Falls back to a rule-based classification
     * driven by the patient's reported symptoms so the CHO still gets a usable triage
     * result rather than an error screen.
     */
    private fun recoverFromParseError(
        input: TriageInput,
        suppliedVitals: Map<String, String>?
    ): AgenticTriageResult {
        if (clinicalToolSet.classifiedUrgency != null) {
            // classifyTriage already succeeded; only the trailing call (e.g. generateReferralNote) failed.
            return buildCompleteResult(suppliedVitals, input)
        }

        Log.w(TAG, "recoverFromParseError: classifyTriage never ran — synthesising rule-based result")

        val symptomList = input.symptoms.joinToString(",")
        val assessResult = runCatching {
            clinicalToolSet.assessSymptoms(
                pathway = input.pathway.name,
                symptoms = symptomList,
                patientAge = input.patientAge,
                patientSex = input.patientSex
            )
        }.getOrNull()

        @Suppress("UNCHECKED_CAST")
        val detectedDangerSigns = (assessResult?.get("danger_signs") as? List<String>).orEmpty()
        val hasAutoRed = detectedDangerSigns.isNotEmpty() || hasAutoRedSign(input)
        val urgency = if (hasAutoRed) "RED" else "AMBER"
        val action = if (hasAutoRed) {
            "Refer urgently. Danger sign present (auto-classified due to model error)."
        } else {
            "Refer for clinical review. Model could not complete triage — use clinical judgement."
        }

        runCatching {
            clinicalToolSet.classifyTriage(
                pathway = input.pathway.name,
                symptoms = symptomList,
                dangerSigns = detectedDangerSigns.joinToString(","),
                vitalSigns = suppliedVitals?.entries?.joinToString(",") { "${it.key}=${it.value}" }
                    ?: "not_collected",
                drugInteractionStatus = "not_checked",
                urgency = urgency,
                action = action,
                confidence = "LOW"
            )
        }

        return buildCompleteResult(suppliedVitals, input)
    }

    private fun hasAutoRedSign(input: TriageInput): Boolean {
        val autoRedSet = when (input.pathway) {
            Pathway.CHILD_U5 -> AUTO_RED_CHILD
            Pathway.ANTENATAL -> AUTO_RED_ANTENATAL
        }
        return input.symptoms.any { symptom -> autoRedSet.any { autoRed -> autoRed.equals(symptom, ignoreCase = true) } }
    }

    private fun buildVitalToolResponse(
        vitalSigns: Map<String, String>,
        requiredVitals: List<String>
    ): Map<String, Any?> {
        val providedVitals = vitalSigns
            .mapKeys { normalizeVitalKey(it.key) }
            .filterValues { it.isNotBlank() }

        return if (providedVitals.isNotEmpty()) {
            mapOf("action" to "VITALS_PROVIDED", "vital_signs" to providedVitals)
        } else {
            mapOf(
                "action" to "VITALS_NOT_AVAILABLE",
                "required_vitals" to requiredVitals,
                "note" to "Vital signs were not collected. Proceed with available clinical data."
            )
        }
    }

    private fun parseRequiredVitals(toolCall: ToolCall): List<String> {
        val args = toolCall.arguments
        val parsed = when (val raw = argumentValue(args, "requiredVitals", "vitalSigns")) {
            is Collection<*> -> raw.mapNotNull { it?.toString() }
            is Array<*> -> raw.mapNotNull { it?.toString() }
            null -> emptyList()
            else -> raw.toString().split(",")
        }.map(::normalizeVitalKey)
            .filter(String::isNotEmpty)
            .distinct()

        return parsed.ifEmpty { DEFAULT_VITALS }
    }

    private fun buildCurrentTriageSnapshot(input: TriageInput?): TriageResult {
        val urgency = clinicalToolSet.classifiedUrgency
        return if (urgency != null) {
            val referralNote = finalizeReferralNote(
                baseReferralNote = clinicalToolSet.generatedReferralNote ?: "No referral note generated.",
                input = input
            )
            TriageResult(
                urgency = urgency,
                action = clinicalToolSet.classifiedAction ?: "Use clinical judgement",
                reportedSymptoms = input?.symptoms ?: emptyList(),
                dangerSignsDetected = clinicalToolSet.classifiedDangerSigns,
                referralNote = referralNote,
                visualFinding = visualSummary(input),
                confirmedVisualFinding = input?.confirmedVisualFinding,
                photoUri = input?.photoUri
            )
        } else {
            fallbackResult("Assessment paused while waiting for vital signs.", input)
        }
    }

    private fun buildCompleteResult(
        suppliedVitals: Map<String, String>?,
        input: TriageInput
    ): AgenticTriageResult {
        val collectedVitals = suppliedVitals?.filterValues { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
        val triageResult = buildCurrentTriageSnapshot(input).let { snapshot ->
            if (clinicalToolSet.classifiedUrgency == null) fallbackResult("Model did not call classifyTriage.", input) else snapshot
        }
        val finalizedReferralNote = finalizeReferralNote(
            baseReferralNote = clinicalToolSet.generatedReferralNote ?: triageResult.referralNote,
            input = input
        )

        val confidence = confidenceLevelFromString(clinicalToolSet.classifiedConfidence)
        val safetyOverride = SafetyGuardrail.apply(
            gemmaUrgency = triageResult.urgency,
            selectedSymptoms = input.symptoms,
            pathway = input.pathway
        )

        val finalTriageResult = triageResult.copy(
            urgency = safetyOverride.finalUrgency,
            referralNote = finalizedReferralNote,
            visualFinding = visualSummary(input),
            confidence = confidence,
            safetyOverrideApplied = safetyOverride.wasOverridden,
            safetyOverrideReason = safetyOverride.overrideReason,
            originalGemmaUrgency = safetyOverride.originalGemmaUrgency
        )

        return AgenticTriageResult(
            status = AgenticStatus.COMPLETE,
            triageResult = finalTriageResult,
            referralNote = finalizedReferralNote,
            visualFinding = visualSummary(input),
            confirmedVisualFinding = input.confirmedVisualFinding,
            vitalSignsCollected = collectedVitals,
            drugInteraction = clinicalToolSet.drugInteractionResult,
            toolCallLog = clinicalToolSet.toolCallLog,
            currentRound = clinicalToolSet.rounds,
            safetyOverride = safetyOverride
        )
    }

    private fun errorResult(reason: String, input: TriageInput?): AgenticTriageResult {
        return AgenticTriageResult(
            status = AgenticStatus.ERROR,
            triageResult = fallbackResult(reason, input),
            visualFinding = visualSummary(input),
            confirmedVisualFinding = input?.confirmedVisualFinding,
            toolCallLog = clinicalToolSet.toolCallLog,
            currentRound = clinicalToolSet.rounds
        )
    }

    private fun fallbackResult(reason: String, input: TriageInput?) = TriageResult(
        urgency = "AMBER",
        action = "Unable to complete assessment — use clinical judgement",
        reportedSymptoms = input?.symptoms ?: emptyList(),
        dangerSignsDetected = emptyList(),
        referralNote = finalizeReferralNote(reason, input),
        visualFinding = visualSummary(input),
        confirmedVisualFinding = input?.confirmedVisualFinding,
        photoUri = input?.photoUri
    )

    private fun finalizeReferralNote(baseReferralNote: String, input: TriageInput?): String {
        val normalizedBase = baseReferralNote.trim().ifBlank { "Referral note unavailable." }
        val safeInput = input ?: return normalizedBase

        val visualAddition = when {
            safeInput.confirmedVisualFinding != null -> safeInput.confirmedVisualFinding.referralNoteText.trim()
            safeInput.photoUri != null -> "Clinical photo reviewed; no abnormal visual sign was confirmed."
            else -> ""
        }

        if (visualAddition.isBlank() || normalizedBase.contains(visualAddition, ignoreCase = true)) {
            return normalizedBase
        }

        return "$normalizedBase\n\n$visualAddition".trim()
    }

    private fun visualSummary(input: TriageInput?): String? {
        val safeInput = input ?: return null
        return when {
            safeInput.confirmedVisualFinding != null -> safeInput.confirmedVisualFinding.findingText
            safeInput.photoUri != null -> "Clinical photo reviewed; no abnormal visual sign was confirmed."
            else -> null
        }
    }

    private fun argString(arguments: Map<String, Any?>, key: String): String {
        val value = argumentValue(arguments, key) ?: return ""
        return when (value) {
            is Collection<*> -> value.joinToString(",") { it?.toString().orEmpty() }
            is Array<*> -> value.joinToString(",") { it?.toString().orEmpty() }
            else -> value.toString()
        }
    }

    private fun argBoolean(arguments: Map<String, Any?>, key: String): Boolean {
        val value = argumentValue(arguments, key) ?: return false
        return when (value) {
            is Boolean -> value
            else -> value.toString().equals("true", ignoreCase = true)
        }
    }

    private fun argumentValue(arguments: Map<String, Any?>, vararg keys: String): Any? {
        val candidates = buildSet {
            keys.forEach { key ->
                add(key)
                add(toSnakeCase(key))
                add(toCamelCase(key))
            }
        }
        return candidates.asSequence().mapNotNull(arguments::get).firstOrNull()
    }

    private fun toSnakeCase(value: String): String {
        return value.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace("-", "_")
            .lowercase()
    }

    private fun toCamelCase(value: String): String {
        val parts = value.replace("-", "_").split("_").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return value
        return buildString {
            append(parts.first())
            parts.drop(1).forEach { append(it.replaceFirstChar(Char::uppercase)) }
        }
    }

    private fun normalizeVitalKey(raw: String): String {
        return raw.trim()
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(" ", "_")
            .replace("-", "_")
            .lowercase()
    }

    private fun confidenceLevelFromString(value: String): ConfidenceLevel {
        return when (value.trim().lowercase()) {
            "high" -> ConfidenceLevel.HIGH
            "medium", "moderate" -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }

    private fun visualFallback(reason: String): VisualFinding {
        return VisualFinding(
            detected = false,
            findingText = "Unable to analyse photo",
            clinicalImplication = "Proceed with symptom-based assessment only",
            referralNoteText = "Photo provided but could not be analysed.",
            confidence = ConfidenceLevel.LOW,
            imageQualitySufficient = false
        )
    }

    private fun closePausedSession() {
        pausedSession?.let { closeConversationQuietly(it.conversation) }
        pausedSession = null
    }

    private fun closeConversationQuietly(conversation: Conversation) {
        try {
            conversation.close()
        } catch (_: Exception) {
        }
    }
}
