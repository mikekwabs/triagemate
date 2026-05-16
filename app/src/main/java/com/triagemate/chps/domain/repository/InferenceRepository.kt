package com.triagemate.chps.domain.repository

import android.net.Uri
import com.triagemate.chps.domain.model.AgenticTriageResult
import com.triagemate.chps.domain.model.ClinicalExplanation
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.domain.model.TriageInput
import com.triagemate.chps.domain.model.TriageResult
import com.triagemate.chps.domain.model.VisualFinding
import com.triagemate.chps.domain.model.VoiceInputResult
import com.triagemate.chps.domain.safety.SafetyOverrideResult
import com.triagemate.chps.util.VisualCue

/**
 * Runs on-device inference via the LiteRT-LM engine.
 *
 * The agentic loop may pause mid-session (AWAITING_VITALS) and be resumed
 * with [resumeWithVitals] once the CHO provides measurements.
 */
interface InferenceRepository {
    suspend fun analyseVisualSign(
        imageUri: Uri,
        visualCue: VisualCue,
        pathway: Pathway,
        selectedSymptoms: List<String>
    ): VisualFinding

    /**
     * Starts a full agentic triage assessment. May return with
     * status=AWAITING_VITALS if Gemma requests vital signs.
     */
    suspend fun runTriage(input: TriageInput): AgenticTriageResult

    /**
     * Resumes a paused triage session after vital signs are collected.
     * The conversation context is maintained from the initial [runTriage] call.
     */
    suspend fun resumeWithVitals(vitalSigns: Map<String, String>): AgenticTriageResult

    /**
     * Generates a short post-assessment educational explanation for a finished
     * case in a separate, short-lived conversation. Does not mutate the triage
     * result. Safe to call multiple times; callers should cache the result.
     */
    suspend fun generateClinicalExplanation(
        result: TriageResult,
        input: TriageInput,
        safetyOverride: SafetyOverrideResult?
    ): ClinicalExplanation

    /**
     * Sends raw PCM 16-bit / 16 kHz / mono audio to Gemma 4 E2B's audio
     * encoder, gets back an English translation of the Twi speech, and
     * maps it onto the canonical symptom checklist for [pathway].
     */
    suspend fun processVoiceInput(
        audioBytes: ByteArray,
        pathway: Pathway
    ): VoiceInputResult
}
