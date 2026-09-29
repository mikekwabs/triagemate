package com.triagemate.chps.domain.safety

import com.triagemate.chps.domain.model.Pathway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One test group per row of the "TriageMate Vital-Sign Rules" review document. */
class VitalSignRulesTest {

    private fun child(vitals: Map<String, String>, age: Int?) =
        VitalSignRules.evaluate(Pathway.CHILD_U5, vitals, age).map { it.ruleId to it.urgency }

    private fun antenatal(vitals: Map<String, String>) =
        VitalSignRules.evaluate(Pathway.ANTENATAL, vitals, null).map { it.ruleId to it.urgency }

    // ── Section A: plausible ranges (hard block) ─────────────────────────────

    @Test
    fun a_temperature_range() {
        assertNull(VitalsValidator.error("temperature", "30"))
        assertNull(VitalsValidator.error("temperature", "45"))
        assertNotNull(VitalsValidator.error("temperature", "29.9"))
        assertNotNull(VitalsValidator.error("temperature", "70"))    // the device-test typo
        assertNotNull(VitalsValidator.error("temperature", "98.6"))  // Fahrenheit
    }

    @Test
    fun a_respiratory_rate_pulse_and_spo2_ranges() {
        assertNull(VitalsValidator.error("respiratory_rate", "5"))
        assertNull(VitalsValidator.error("respiratory_rate", "150"))
        assertNotNull(VitalsValidator.error("respiratory_rate", "151"))
        assertNull(VitalsValidator.error("pulse", "30"))
        assertNotNull(VitalsValidator.error("pulse", "251"))
        assertNull(VitalsValidator.error("oxygen_saturation", "40"))
        assertNotNull(VitalsValidator.error("oxygen_saturation", "101"))
        assertNotNull(VitalsValidator.error("oxygen_saturation", "39"))
    }

    @Test
    fun a_blood_pressure_format_and_ranges() {
        assertNull(VitalsValidator.error("blood_pressure", "120/80"))
        assertNull(VitalsValidator.error("blood_pressure", " 120 / 80 "))
        assertNotNull(VitalsValidator.error("blood_pressure", "120"))
        assertNotNull(VitalsValidator.error("blood_pressure", "260/80"))
        assertNotNull(VitalsValidator.error("blood_pressure", "120/160"))
        assertNotNull(VitalsValidator.error("blood_pressure", "80/80"))   // systolic must exceed diastolic
    }

    @Test
    fun a_non_numbers_are_blocked_blanks_are_allowed() {
        assertNotNull(VitalsValidator.error("pulse", "fast"))
        assertNull(VitalsValidator.error("pulse", ""))
        assertTrue(VitalsValidator.hasErrors(mapOf("pulse" to "90", "temperature" to "70")))
        assertFalse(VitalsValidator.hasErrors(mapOf("pulse" to "90", "temperature" to "")))
    }

    @Test
    fun a_temperature_needs_dot_decimal_with_at_most_one_place() {
        assertNull(VitalsValidator.error("temperature", "36.8"))
        assertNull(VitalsValidator.error("temperature", "37"))
        assertEquals("Use a dot for decimals, e.g. 36.8.", VitalsValidator.error("temperature", "36,8"))
        assertNotNull(VitalsValidator.error("temperature", "36.85"))
        assertNotNull(VitalsValidator.error("temperature", "36.8.1"))
        assertNotNull(VitalsValidator.error("temperature", "36."))
        assertNotNull(VitalsValidator.error("temperature", ".8"))
        assertNotNull(VitalsValidator.error("temperature", "+38"))
        assertNotNull(VitalsValidator.error("temperature", "3 8"))
    }

    @Test
    fun a_special_number_strings_are_rejected() {
        // toDoubleOrNull() accepts these; NaN would pass every range comparison.
        listOf("NaN", "Infinity", "1e2", "-38").forEach { value ->
            assertNotNull(value, VitalsValidator.error("temperature", value))
            assertNotNull(value, VitalsValidator.error("oxygen_saturation", value))
        }
    }

    @Test
    fun a_counts_and_spo2_must_be_whole_numbers() {
        assertNull(VitalsValidator.error("respiratory_rate", "40"))
        assertEquals("Enter a whole number, e.g. 40.", VitalsValidator.error("respiratory_rate", "40.5"))
        assertEquals("Enter a whole number, e.g. 100.", VitalsValidator.error("pulse", "100,0"))
        assertEquals("Enter a whole number, e.g. 98.", VitalsValidator.error("oxygen_saturation", "97.5"))
    }

    @Test
    fun a_aliases_are_validated_too() {
        assertNotNull(VitalsValidator.error("heartRate", "300"))
        assertNotNull(VitalsValidator.error("SpO2", "120"))
        assertNull(VitalsValidator.error("weight", "9999"))               // unknown vitals are not ranged
    }

    // ── Section B: child under 5 (WHO IMCI 2014) ─────────────────────────────

    @Test
    fun b1_spo2_below_90_is_red_at_any_age() {
        assertEquals(listOf("B1" to "RED"), child(mapOf("oxygen_saturation" to "89"), 30))
        assertEquals(listOf("B1" to "RED"), child(mapOf("oxygen_saturation" to "89"), null))
        assertEquals(emptyList<Pair<String, String>>(), child(mapOf("oxygen_saturation" to "90"), 30))
    }

    @Test
    fun b2_fast_breathing_under_1_month_is_red() {
        assertEquals(listOf("B2" to "RED"), child(mapOf("respiratory_rate" to "60"), 0))
        assertEquals(emptyList<Pair<String, String>>(), child(mapOf("respiratory_rate" to "59"), 0))
    }

    @Test
    fun b3_fast_breathing_at_1_month_is_amber() {
        assertEquals(listOf("B3" to "AMBER"), child(mapOf("respiratory_rate" to "60"), 1))
        assertEquals(emptyList<Pair<String, String>>(), child(mapOf("respiratory_rate" to "59"), 1))
    }

    @Test
    fun b4_fast_breathing_2_to_11_months_is_amber() {
        assertEquals(listOf("B4" to "AMBER"), child(mapOf("respiratory_rate" to "50"), 2))
        assertEquals(listOf("B4" to "AMBER"), child(mapOf("respiratory_rate" to "50"), 11))
        assertEquals(emptyList<Pair<String, String>>(), child(mapOf("respiratory_rate" to "49"), 6))
    }

    @Test
    fun b5_fast_breathing_12_to_59_months_or_unknown_age_is_amber() {
        assertEquals(listOf("B5" to "AMBER"), child(mapOf("respiratory_rate" to "40"), 12))
        assertEquals(listOf("B5" to "AMBER"), child(mapOf("respiratory_rate" to "40"), 59))
        assertEquals(listOf("B5" to "AMBER"), child(mapOf("respiratory_rate" to "40"), null))
        assertEquals(emptyList<Pair<String, String>>(), child(mapOf("respiratory_rate" to "39"), 30))
    }

    @Test
    fun b6_young_infant_temperature_outside_range_is_red() {
        assertEquals(listOf("B6" to "RED"), child(mapOf("temperature" to "37.5"), 0))
        assertEquals(listOf("B6" to "RED"), child(mapOf("temperature" to "35.4"), 1))
        assertEquals(emptyList<Pair<String, String>>(), child(mapOf("temperature" to "37.4"), 1))
        assertEquals(emptyList<Pair<String, String>>(), child(mapOf("temperature" to "37.5"), null))
    }

    @Test
    fun b_record_only_pulse_bp_and_fever_from_2_months() {
        val vitals = mapOf("pulse" to "50", "blood_pressure" to "145/57", "temperature" to "39.5")
        assertEquals(emptyList<Pair<String, String>>(), child(vitals, 25))
    }

    // ── Section C: antenatal (WHO 2011, PCPNC 2015) ──────────────────────────

    @Test
    fun c1_severe_hypertension_is_red() {
        assertEquals(listOf("C1" to "RED"), antenatal(mapOf("blood_pressure" to "160/100")))
        assertEquals(listOf("C1" to "RED"), antenatal(mapOf("blood_pressure" to "150/110")))
    }

    @Test
    fun c2_hypertension_is_amber_only_below_severe() {
        assertEquals(listOf("C2" to "AMBER"), antenatal(mapOf("blood_pressure" to "140/85")))
        assertEquals(listOf("C2" to "AMBER"), antenatal(mapOf("blood_pressure" to "130/90")))
        assertEquals(emptyList<Pair<String, String>>(), antenatal(mapOf("blood_pressure" to "139/89")))
    }

    @Test
    fun c3_systolic_below_90_is_red() {
        assertEquals(listOf("C3" to "RED"), antenatal(mapOf("blood_pressure" to "85/50")))
        assertEquals(emptyList<Pair<String, String>>(), antenatal(mapOf("blood_pressure" to "90/60")))
    }

    @Test
    fun c4_spo2_below_90_is_red() {
        assertEquals(listOf("C4" to "RED"), antenatal(mapOf("oxygen_saturation" to "89")))
        assertEquals(emptyList<Pair<String, String>>(), antenatal(mapOf("oxygen_saturation" to "90")))
    }

    @Test
    fun c_record_only_pulse_respiratory_rate_temperature() {
        val vitals = mapOf("pulse" to "130", "respiratory_rate" to "34", "temperature" to "39")
        assertEquals(emptyList<Pair<String, String>>(), antenatal(vitals))
    }

    // ── Guardrail integration ───────────────────────────────────────────────

    @Test
    fun worked_example_from_device_test_becomes_red() {
        // Temperature 70 is blocked at entry, so it is never submitted.
        val vitals = mapOf(
            "respiratory_rate" to "69", "pulse" to "50",
            "blood_pressure" to "145/57", "oxygen_saturation" to "69"
        )
        val result = SafetyGuardrail.apply("GREEN", listOf("Fever", "Diarrhoea"), Pathway.CHILD_U5, vitals, 25)
        assertEquals("RED", result.finalUrgency)
        assertTrue(result.wasOverridden)
        assertEquals("GREEN", result.originalGemmaUrgency)
        assertEquals(listOf("B1", "B5"), result.vitalRuleHits.map { it.ruleId })
        assertEquals(
            "Vital-sign rule B1: SpO₂ 69% is below 90%; " +
                "Vital-sign rule B5: respiratory rate 69/min is fast breathing (40 or more, 12–59 months)",
            result.overrideReason
        )
    }

    @Test
    fun amber_rule_raises_green_but_never_lowers_red() {
        val raised = SafetyGuardrail.apply("GREEN", listOf("Fever"), Pathway.CHILD_U5, mapOf("respiratory_rate" to "45"), 30)
        assertEquals("AMBER", raised.finalUrgency)
        assertTrue(raised.wasOverridden)

        val kept = SafetyGuardrail.apply("RED", listOf("Fever"), Pathway.CHILD_U5, mapOf("respiratory_rate" to "45"), 30)
        assertEquals("RED", kept.finalUrgency)
        assertFalse(kept.wasOverridden)
    }

    @Test
    fun rule_at_or_below_model_urgency_is_not_reported() {
        val result = SafetyGuardrail.apply("AMBER", listOf("Fever"), Pathway.CHILD_U5, mapOf("respiratory_rate" to "45"), 30)
        assertFalse(result.wasOverridden)
        assertTrue(result.vitalRuleHits.isEmpty())
    }

    @Test
    fun danger_sign_and_vital_rule_are_both_explained() {
        val result = SafetyGuardrail.apply(
            "AMBER", listOf("Stridor"), Pathway.CHILD_U5, mapOf("oxygen_saturation" to "85"), 20
        )
        assertEquals("RED", result.finalUrgency)
        assertEquals(listOf("Stridor"), result.overriddenSigns)
        assertEquals(
            "WHO danger sign detected: Stridor; Vital-sign rule B1: SpO₂ 85% is below 90%",
            result.overrideReason
        )
    }

    @Test
    fun implausible_values_are_ignored_by_rules() {
        val result = SafetyGuardrail.apply("GREEN", emptyList(), Pathway.CHILD_U5, mapOf("oxygen_saturation" to "20"), 30)
        assertFalse(result.wasOverridden)
    }

    @Test
    fun no_vitals_means_no_vital_override() {
        val result = SafetyGuardrail.apply("GREEN", listOf("Fever"), Pathway.CHILD_U5)
        assertFalse(result.wasOverridden)
        assertEquals("GREEN", result.finalUrgency)
    }
}
