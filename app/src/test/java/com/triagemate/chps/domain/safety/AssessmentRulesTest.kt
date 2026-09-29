package com.triagemate.chps.domain.safety

import com.triagemate.chps.domain.model.AssessmentExtras
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.domain.model.RdtResult
import com.triagemate.chps.domain.model.UrineProtein
import com.triagemate.chps.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One test group per row of the "TriageMate Assessment Additions" review document. */
class AssessmentRulesTest {

    private val none = emptyList<Pair<String, String>>()

    private fun child(
        symptoms: List<String>,
        extras: AssessmentExtras,
        age: Int? = 24
    ) = AssessmentRules.evaluate(Pathway.CHILD_U5, symptoms, extras, age, emptyMap())
        .map { it.ruleId to it.urgency }

    private fun antenatal(
        symptoms: List<String>,
        protein: UrineProtein?,
        bp: String?
    ) = AssessmentRules.evaluate(
        Pathway.ANTENATAL, symptoms, AssessmentExtras(urineProtein = protein), null,
        bp?.let { mapOf("blood_pressure" to it) } ?: emptyMap()
    ).map { it.ruleId to it.urgency }

    // ── Section D: child under 5 ────────────────────────────────────────────

    @Test
    fun d1_persistent_diarrhoea_from_14_days() {
        assertEquals(listOf("D1" to "AMBER"), child(listOf("Diarrhoea"), AssessmentExtras(diarrhoeaDays = 14)))
        assertEquals(none, child(listOf("Diarrhoea"), AssessmentExtras(diarrhoeaDays = 13)))
    }

    @Test
    fun d2_cough_more_than_14_days() {
        assertEquals(listOf("D2" to "AMBER"), child(listOf("Fast breathing / cough"), AssessmentExtras(coughDays = 15)))
        assertEquals(none, child(listOf("Fast breathing / cough"), AssessmentExtras(coughDays = 14)))
    }

    @Test
    fun d3_fever_more_than_7_days() {
        assertEquals(listOf("D3" to "AMBER"), child(listOf("Fever"), AssessmentExtras(feverDays = 8)))
        assertEquals(none, child(listOf("Fever"), AssessmentExtras(feverDays = 7)))
    }

    @Test
    fun durations_only_count_when_the_symptom_is_ticked() {
        assertEquals(none, child(listOf("Fever"), AssessmentExtras(diarrhoeaDays = 20, coughDays = 30)))
    }

    @Test
    fun d4_malaria_rdt_informs_action_only() {
        assertEquals(none, child(listOf("Fever"), AssessmentExtras(malariaRdt = RdtResult.POSITIVE)))
    }

    @Test
    fun d5_oedema_of_both_feet_is_red() {
        assertEquals(listOf("D5" to "RED"), child(listOf("Fever"), AssessmentExtras(oedemaBothFeet = true)))
        assertEquals(none, child(listOf("Fever"), AssessmentExtras(oedemaBothFeet = false)))
    }

    @Test
    fun d6_d7_muac_bands() {
        assertEquals(listOf("D6" to "AMBER"), child(listOf("Fever"), AssessmentExtras(muacMm = 114)))
        assertEquals(listOf("D7" to "AMBER"), child(listOf("Fever"), AssessmentExtras(muacMm = 115)))
        assertEquals(listOf("D7" to "AMBER"), child(listOf("Fever"), AssessmentExtras(muacMm = 124)))
        assertEquals(none, child(listOf("Fever"), AssessmentExtras(muacMm = 125)))
    }

    @Test
    fun d8_blood_in_stool_from_2_months_or_unknown_age() {
        assertEquals(listOf("D8" to "AMBER"), child(listOf("Blood in stool"), AssessmentExtras(), age = 2))
        assertEquals(listOf("D8" to "AMBER"), child(listOf("Blood in stool"), AssessmentExtras(), age = null))
        assertEquals(none, child(listOf("Blood in stool"), AssessmentExtras(), age = 1))
    }

    // ── Section E: antenatal ────────────────────────────────────────────────

    @Test
    fun e1_severe_preeclampsia_with_headache_or_vision() {
        assertEquals(listOf("E1" to "RED"), antenatal(listOf("Severe headache"), UrineProtein.PLUS2, "145/95"))
        assertEquals(listOf("E1" to "RED"), antenatal(listOf("Blurred or lost vision"), UrineProtein.PLUS3, "140/90"))
    }

    @Test
    fun e2_diastolic_110_with_plus3_is_red() {
        assertEquals(listOf("E2" to "RED"), antenatal(emptyList(), UrineProtein.PLUS3, "150/110"))
    }

    @Test
    fun e3_preeclampsia_without_severe_feature_is_amber() {
        assertEquals(listOf("E3" to "AMBER"), antenatal(emptyList(), UrineProtein.PLUS2, "140/90"))
        assertEquals(listOf("E3" to "AMBER"), antenatal(emptyList(), UrineProtein.PLUS2, "150/110"))
    }

    @Test
    fun e_rules_need_protein_at_least_plus2_and_a_blood_pressure() {
        assertEquals(none, antenatal(listOf("Severe headache"), UrineProtein.PLUS1, "150/100"))
        assertEquals(none, antenatal(listOf("Severe headache"), UrineProtein.NOT_DONE, "150/100"))
        assertEquals(none, antenatal(listOf("Severe headache"), UrineProtein.PLUS3, null))
        assertEquals(none, antenatal(listOf("Severe headache"), UrineProtein.PLUS3, "130/85"))
        assertEquals(none, antenatal(listOf("Severe headache"), UrineProtein.PLUS3, "300/100"))  // implausible BP ignored
    }

    @Test
    fun e4_severe_abdominal_pain_is_on_the_auto_red_checklist() {
        assertTrue("Severe abdominal pain" in Constants.ANTENATAL_SYMPTOMS)
        val result = SafetyGuardrail.apply("GREEN", listOf("Severe abdominal pain"), Pathway.ANTENATAL)
        assertEquals("RED", result.finalUrgency)
    }

    @Test
    fun e5_leaking_fluid_has_no_rule_until_confirmed() {
        assertTrue("Leaking fluid from the vagina" in Constants.ANTENATAL_SYMPTOMS)
        val result = SafetyGuardrail.apply("GREEN", listOf("Leaking fluid from the vagina"), Pathway.ANTENATAL)
        assertFalse(result.wasOverridden)
    }

    @Test
    fun new_checklist_items_exist() {
        assertTrue("Blood in stool" in Constants.CHILD_U5_SYMPTOMS)
    }

    // ── Guardrail integration ──────────────────────────────────────────────

    @Test
    fun worked_example_from_review_document() {
        val extras = AssessmentExtras(
            feverDays = 3, diarrhoeaDays = 16, malariaRdt = RdtResult.POSITIVE,
            muacMm = 112, oedemaBothFeet = false
        )
        val result = SafetyGuardrail.apply(
            "GREEN", listOf("Fever", "Diarrhoea"), Pathway.CHILD_U5,
            patientAgeMonths = 14, extras = extras
        )
        assertEquals("AMBER", result.finalUrgency)
        assertEquals(listOf("D1", "D6"), result.ruleHits.map { it.ruleId })
        assertEquals(
            "Assessment rule D1: diarrhoea for 16 days is persistent diarrhoea (14 days or more); " +
                "Assessment rule D6: MUAC 112 mm is severe acute malnutrition (below 115 mm)",
            result.overrideReason
        )
    }

    @Test
    fun vital_and_assessment_rules_combine_and_most_severe_wins() {
        val result = SafetyGuardrail.apply(
            "GREEN", listOf("Fever"), Pathway.CHILD_U5,
            vitalSigns = mapOf("respiratory_rate" to "45"), patientAgeMonths = 30,
            extras = AssessmentExtras(oedemaBothFeet = true)
        )
        assertEquals("RED", result.finalUrgency)
        assertEquals(listOf("B5", "D5"), result.ruleHits.map { it.ruleId })
    }

    // ── Entry validation and summary ───────────────────────────────────────

    @Test
    fun validators_hard_block_implausible_entries() {
        assertNull(AssessmentExtrasValidator.daysError(""))
        assertNull(AssessmentExtrasValidator.daysError("14"))
        assertNotNull(AssessmentExtrasValidator.daysError("0"))
        assertNotNull(AssessmentExtrasValidator.daysError("91"))
        assertNotNull(AssessmentExtrasValidator.daysError("2.5"))
        assertNull(AssessmentExtrasValidator.muacError("115"))
        assertNotNull(AssessmentExtrasValidator.muacError("11.5"))   // cm instead of mm
        assertNotNull(AssessmentExtrasValidator.muacError("12"))
    }

    @Test
    fun summary_and_symptom_filtering() {
        val extras = AssessmentExtras(feverDays = 1, diarrhoeaDays = 16, muacMm = 112, urineProtein = UrineProtein.PLUS2)
        assertEquals(
            "fever for 1 day; diarrhoea for 16 days; MUAC 112 mm",
            extras.forSymptoms(Pathway.CHILD_U5, listOf("Fever", "Diarrhoea")).summary()
        )
        assertEquals("urine protein ++", extras.forSymptoms(Pathway.ANTENATAL, emptyList()).summary())
        assertNull(AssessmentExtras().summary())
    }
}
