package com.triagemate.chps.util

import com.triagemate.chps.domain.model.Pathway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferralNoteBuilderTest {

    private fun childNote(
        dangerSigns: List<String> = emptyList(),
        vitals: Map<String, String>? = null,
        urgency: String = "AMBER",
        overrideReason: String? = null,
        originalUrgency: String? = null
    ) = ReferralNoteBuilder.build(
        pathway = Pathway.CHILD_U5,
        patientAge = "8",
        patientSex = "MALE",
        symptoms = listOf("Fever", "Diarrhoea"),
        dangerSigns = dangerSigns,
        vitalSigns = vitals,
        medications = "",
        urgency = urgency,
        action = "Refer to the health centre for review within 24 hours.",
        safetyOverrideReason = overrideReason,
        originalModelUrgency = originalUrgency
    )

    @Test
    fun childNote_containsAllSections() {
        val note = childNote(vitals = mapOf("temperature" to "38.9", "respiratory_rate" to "48"))
        val expected = listOf(
            "TriageMate referral — AMBER (non-urgent referral, review within 24–48 hours)",
            "Patient: Male, 8 months, Child Under 5",
            "Presenting symptoms: Fever, Diarrhoea",
            "Danger signs: None identified",
            "Vital signs: temperature 38.9 °C, respiratory rate 48/min",
            "Current medications: None reported",
            "Recommended action: Refer to the health centre for review within 24 hours."
        ).joinToString("\n")
        assertEquals(expected, note)
    }

    @Test
    fun antenatalNote_isAlwaysFemaleWithGestation() {
        val note = ReferralNoteBuilder.build(
            pathway = Pathway.ANTENATAL,
            patientAge = "32",
            patientSex = "",
            symptoms = listOf("Vaginal bleeding"),
            dangerSigns = listOf("Vaginal bleeding"),
            vitalSigns = null,
            medications = "Iron supplements",
            urgency = "red",
            action = "Refer urgently."
        )
        assertTrue(note.startsWith("TriageMate referral — RED (urgent referral within 4 hours)"))
        assertTrue(note.contains("Patient: Female, 32 weeks gestation, Antenatal Care"))
        assertTrue(note.contains("Current medications: Iron supplements"))
    }

    @Test
    fun safetyOverride_isStatedWithFinalUrgency() {
        val note = childNote(
            dangerSigns = listOf("Stridor"),
            urgency = "RED",
            overrideReason = "WHO danger sign detected: Stridor",
            originalUrgency = "AMBER"
        )
        assertTrue(note.startsWith("TriageMate referral — RED"))
        assertTrue(note.contains("Safety override: WHO danger sign detected: Stridor — escalated to RED (AI assessment was AMBER)."))
    }

    @Test
    fun additionalFindings_appearBeforeMedications() {
        val note = ReferralNoteBuilder.build(
            pathway = Pathway.CHILD_U5, patientAge = "14", patientSex = "FEMALE",
            symptoms = listOf("Diarrhoea"), dangerSigns = emptyList(), vitalSigns = null,
            medications = "", urgency = "AMBER", action = "Refer.",
            additionalFindings = "diarrhoea for 16 days; MUAC 112 mm"
        )
        assertTrue(note.contains("Additional findings: diarrhoea for 16 days; MUAC 112 mm\nCurrent medications: None reported"))
    }

    @Test
    fun placeholderAndDuplicateDangerSigns_areCleaned() {
        val note = childNote(dangerSigns = listOf("none", "Stridor", "stridor", " "))
        assertTrue(note.contains("Danger signs: Stridor\n"))
    }

    @Test
    fun blankVitalsAndMissingDemographics_areReportedExplicitly() {
        val note = ReferralNoteBuilder.build(
            pathway = Pathway.CHILD_U5,
            patientAge = "",
            patientSex = "",
            symptoms = emptyList(),
            dangerSigns = emptyList(),
            vitalSigns = mapOf("temperature" to " "),
            medications = "",
            urgency = "GREEN",
            action = ""
        )
        assertTrue(note.contains("Patient: Sex not recorded, age not recorded, Child Under 5"))
        assertTrue(note.contains("Vital signs: Not collected"))
        assertTrue(note.contains("Recommended action: Use clinical judgement."))
        assertFalse(note.contains("Safety override"))
    }
}
