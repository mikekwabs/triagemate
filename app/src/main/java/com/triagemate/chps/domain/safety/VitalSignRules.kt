package com.triagemate.chps.domain.safety

import com.triagemate.chps.domain.model.Pathway
import java.util.Locale

/**
 * Vital-sign parsing, plausibility limits and WHO-based urgency rules.
 *
 * Policy (agreed with the project owner, pending clinical sign-off): follow global
 * WHO standards only. Signs a standard doesn't use (e.g. heart rate in IMCI) are
 * recorded in the referral note but never change urgency. Values outside the
 * plausible ranges are hard-blocked at entry. Rule IDs match the review document
 * "TriageMate Vital-Sign Rules" (sections A, B and C).
 */
object VitalSigns {
    const val TEMPERATURE = "temperature"
    const val RESPIRATORY_RATE = "respiratory_rate"
    const val PULSE = "pulse"
    const val BLOOD_PRESSURE = "blood_pressure"
    const val OXYGEN_SATURATION = "oxygen_saturation"

    private val ALIASES = mapOf(
        "temp" to TEMPERATURE, "body_temperature" to TEMPERATURE,
        "resp_rate" to RESPIRATORY_RATE, "breathing_rate" to RESPIRATORY_RATE, "rr" to RESPIRATORY_RATE,
        "heart_rate" to PULSE, "pulse_rate" to PULSE, "hr" to PULSE,
        "bp" to BLOOD_PRESSURE,
        "spo2" to OXYGEN_SATURATION, "sp_o2" to OXYGEN_SATURATION, "o2_saturation" to OXYGEN_SATURATION
    )

    /** Maps any key the model or UI uses ("heartRate", "SpO2", "blood pressure") to a canonical key. */
    fun canonicalKey(raw: String): String {
        val snake = raw.trim()
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(" ", "_")
            .replace("-", "_")
            .lowercase()
        return ALIASES[snake] ?: snake
    }

    // Strict formats. toDoubleOrNull() alone would accept "NaN", "Infinity" and "1e2"
    // (NaN passes every range check), and a comma decimal is not accepted.
    private val DECIMAL_ONE_PLACE = Regex("""^\d{1,3}(\.\d)?$""")
    private val WHOLE_NUMBER = Regex("""^\d{1,3}$""")

    /** Parses "36" or "36.8" (dot decimal, at most one place); anything else is null. */
    fun parseNumber(raw: String): Double? =
        raw.trim().takeIf { DECIMAL_ONE_PLACE.matches(it) }?.toDouble()

    fun isWholeNumber(raw: String): Boolean = WHOLE_NUMBER.matches(raw.trim())

    private val BP_PATTERN = Regex("""^\s*(\d{1,3})\s*/\s*(\d{1,3})\s*$""")

    /** Parses "120/80" into (systolic, diastolic). */
    fun parseBloodPressure(raw: String): Pair<Int, Int>? =
        BP_PATTERN.matchEntire(raw)?.destructured?.let { (s, d) -> s.toInt() to d.toInt() }
}

/** Section A: values outside these ranges cannot be real readings and are hard-blocked. */
object VitalsValidator {

    /** Returns a correction message, or null when the value is acceptable. Blank is acceptable (optional). */
    fun error(key: String, raw: String): String? {
        if (raw.isBlank()) return null
        return when (VitalSigns.canonicalKey(key)) {
            VitalSigns.TEMPERATURE -> numberInRange(raw, 30.0, 45.0, "a temperature", "°C", "36.8", wholeOnly = false)
            VitalSigns.RESPIRATORY_RATE -> numberInRange(raw, 5.0, 150.0, "a respiratory rate", "breaths/min", "40", wholeOnly = true)
            VitalSigns.PULSE -> numberInRange(raw, 30.0, 250.0, "a pulse", "bpm", "100", wholeOnly = true)
            VitalSigns.OXYGEN_SATURATION -> numberInRange(raw, 40.0, 100.0, "an SpO₂", "%", "98", wholeOnly = true)
            VitalSigns.BLOOD_PRESSURE -> bloodPressureError(raw)
            else -> null
        }
    }

    fun hasErrors(values: Map<String, String>): Boolean = values.any { (k, v) -> error(k, v) != null }

    /** Accepted range shown under the field before any error, or null for vitals without a range. */
    fun rangeHint(key: String): String? = when (VitalSigns.canonicalKey(key)) {
        VitalSigns.TEMPERATURE -> "30–45 °C"
        VitalSigns.RESPIRATORY_RATE -> "5–150 breaths/min"
        VitalSigns.PULSE -> "30–250 bpm"
        VitalSigns.OXYGEN_SATURATION -> "40–100 %"
        VitalSigns.BLOOD_PRESSURE -> "systolic/diastolic, e.g. 120/80 mmHg"
        else -> null
    }

    private fun numberInRange(
        raw: String, min: Double, max: Double, what: String, unit: String, example: String,
        wholeOnly: Boolean
    ): String? {
        if (',' in raw) return if (wholeOnly) "Enter a whole number, e.g. $example." else "Use a dot for decimals, e.g. $example."
        if (wholeOnly && !VitalSigns.isWholeNumber(raw)) return "Enter a whole number, e.g. $example."
        val value = VitalSigns.parseNumber(raw)
            ?: return if (wholeOnly) "Enter a whole number, e.g. $example." else "Enter a number with at most one decimal place, e.g. $example."
        return if (value < min || value > max) {
            "Enter $what between ${fmt(min)} and ${fmt(max)} $unit."
        } else {
            null
        }
    }

    private fun bloodPressureError(raw: String): String? {
        val (systolic, diastolic) = VitalSigns.parseBloodPressure(raw)
            ?: return "Enter blood pressure as systolic/diastolic, e.g. 120/80."
        return when {
            systolic !in 50..250 -> "Systolic must be between 50 and 250 mmHg."
            diastolic !in 20..150 -> "Diastolic must be between 20 and 150 mmHg."
            systolic <= diastolic -> "Systolic (first number) must be higher than diastolic."
            else -> null
        }
    }

    private fun fmt(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}

data class VitalRuleHit(
    /** Rule ID from the review document, e.g. "B1". */
    val ruleId: String,
    /** Minimum urgency the rule sets: "RED" or "AMBER". */
    val urgency: String,
    /** Plain-language finding, e.g. "SpO₂ 69% is below 90%". */
    val finding: String
)

/** Sections B (child under 5, WHO IMCI 2014) and C (antenatal, WHO 2011 / PCPNC 2015). */
object VitalSignRules {

    /**
     * @param patientAgeMonths child age in whole months; null when not recorded. Ignored for ANTENATAL.
     * Values that fail [VitalsValidator] are ignored (they are blocked at entry).
     */
    fun evaluate(
        pathway: Pathway,
        vitalSigns: Map<String, String>,
        patientAgeMonths: Int?
    ): List<VitalRuleHit> {
        val vitals = vitalSigns
            .filter { (k, v) -> v.isNotBlank() && VitalsValidator.error(k, v) == null }
            .mapKeys { VitalSigns.canonicalKey(it.key) }
        return when (pathway) {
            Pathway.CHILD_U5 -> childRules(vitals, patientAgeMonths?.takeIf { it >= 0 })
            Pathway.ANTENATAL -> antenatalRules(vitals)
        }
    }

    private fun childRules(vitals: Map<String, String>, age: Int?): List<VitalRuleHit> = buildList {
        vitals[VitalSigns.OXYGEN_SATURATION]?.let(VitalSigns::parseNumber)?.let { spo2 ->
            if (spo2 < 90) add(VitalRuleHit("B1", "RED", "SpO₂ ${num(spo2)}% is below 90%"))
        }

        vitals[VitalSigns.RESPIRATORY_RATE]?.let(VitalSigns::parseNumber)?.let { rr ->
            val rate = "respiratory rate ${num(rr)}/min"
            when {
                age == 0 && rr >= 60 ->
                    add(VitalRuleHit("B2", "RED", "$rate is fast breathing (60 or more) in an infant under 1 month"))
                age == 1 && rr >= 60 ->
                    add(VitalRuleHit("B3", "AMBER", "$rate is fast breathing (60 or more) at 1 month"))
                age != null && age in 2..11 && rr >= 50 ->
                    add(VitalRuleHit("B4", "AMBER", "$rate is fast breathing (50 or more) at 2–11 months"))
                (age == null || age >= 12) && rr >= 40 -> {
                    val band = if (age == null) "age not recorded" else "12–59 months"
                    add(VitalRuleHit("B5", "AMBER", "$rate is fast breathing (40 or more, $band)"))
                }
            }
        }

        if (age != null && age <= 1) {
            vitals[VitalSigns.TEMPERATURE]?.let(VitalSigns::parseNumber)?.let { temp ->
                if (temp >= 37.5 || temp < 35.5) {
                    add(VitalRuleHit("B6", "RED", "temperature ${num(temp)} °C in a young infant is outside 35.5–37.4 °C"))
                }
            }
        }
    }

    private fun antenatalRules(vitals: Map<String, String>): List<VitalRuleHit> = buildList {
        vitals[VitalSigns.BLOOD_PRESSURE]?.let(VitalSigns::parseBloodPressure)?.let { (s, d) ->
            val bp = "blood pressure $s/$d mmHg"
            when {
                s >= 160 || d >= 110 -> add(VitalRuleHit("C1", "RED", "$bp is severe hypertension (160/110 or more)"))
                s >= 140 || d >= 90 -> add(VitalRuleHit("C2", "AMBER", "$bp is hypertension in pregnancy (140/90 or more)"))
            }
            if (s < 90) add(VitalRuleHit("C3", "RED", "systolic $s mmHg is below 90 (sign of shock)"))
        }

        vitals[VitalSigns.OXYGEN_SATURATION]?.let(VitalSigns::parseNumber)?.let { spo2 ->
            if (spo2 < 90) add(VitalRuleHit("C4", "RED", "SpO₂ ${num(spo2)}% is below 90%"))
        }
    }

    private fun num(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}
