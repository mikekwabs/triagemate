package com.triagemate.chps.util

import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.domain.model.TriageInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private val input = TriageInput(
        pathway = Pathway.CHILD_U5,
        symptoms = listOf("Fever", "Diarrhoea"),
        patientAge = "8",
        patientSex = "MALE"
    )

    @Test
    fun userPrompt_includesPreRunAssessmentSummary() {
        val assessment = mapOf<String, Any>(
            "clinical_summary" to "Symptoms: Fever, Diarrhoea. No automatic danger signs identified from the checklist. Preliminary severity: MODERATE."
        )
        val prompt = PromptBuilder.buildUserPrompt(input, assessment)
        assertTrue(prompt.contains("Symptom assessment (already run by the app): Symptoms: Fever, Diarrhoea."))
        assertTrue(prompt.contains("Preliminary severity: MODERATE."))
    }

    @Test
    fun userPrompt_withoutAssessment_hasNoAssessmentLine() {
        assertFalse(PromptBuilder.buildUserPrompt(input).contains("Symptom assessment"))
    }

    @Test
    fun systemPrompt_neverAsksModelToCallRemovedTools() {
        val prompt = PromptBuilder.buildSystemPrompt()
        assertFalse(prompt.contains("calling assessSymptoms"))
        assertFalse(prompt.contains("Round 1: assessSymptoms"))
        assertFalse(prompt.contains("generateReferralNote"))
        assertTrue(prompt.contains("Round 1: classifyTriage (urgency=RED)"))
    }
}
