package com.triagemate.chps.util

import com.triagemate.chps.domain.model.Pathway

/**
 * Single source of truth for danger signs that force a RED referral regardless of
 * the model's output. Every entry must be an exact label from the matching symptom
 * checklist in [Constants] — the guardrail, the agentic loop, the camera cue logic
 * and the UI all read from here. Enforced by AutoRedSignsTest.
 */
val AUTO_RED_CHILD_U5 = setOf(
    "Unable to drink or breastfeed",
    "Vomiting everything",
    "Convulsions",
    "Lethargic or unconscious",
    "Severe chest indrawing",
    "Stridor"
)

val AUTO_RED_ANTENATAL = setOf(
    "Vaginal bleeding",
    "Convulsions / fits",
    "Absent fetal movement",
    "Prolonged labour (>24h)",
    // PCPNC danger sign (assessment additions, rule E4).
    "Severe abdominal pain"
)

fun autoRedSignsFor(pathway: Pathway): Set<String> = when (pathway) {
    Pathway.CHILD_U5  -> AUTO_RED_CHILD_U5
    Pathway.ANTENATAL -> AUTO_RED_ANTENATAL
}

fun isAutoRedSign(symptom: String, pathway: Pathway): Boolean {
    val normalized = symptom.trim()
    return autoRedSignsFor(pathway).any { it.equals(normalized, ignoreCase = true) }
}

object Constants {
    const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
    const val MODEL_DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
    const val DATABASE_NAME = "triagemate_db"

    // ── Child Under 5 symptoms (ordered by clinical severity) ──────
    val CHILD_U5_SYMPTOMS = listOf(
        "Fever",
        "Unable to drink or breastfeed",
        "Vomiting everything",
        "Convulsions",
        "Lethargic or unconscious",
        "Fast breathing / cough",
        "Diarrhoea",
        "Severe chest indrawing",
        "Stridor",
        "Blood in stool"
    )

    // ── Antenatal symptoms (matching design order) ─────────────────
    val ANTENATAL_SYMPTOMS = listOf(
        "Vaginal bleeding",
        "Convulsions / fits",
        "Absent fetal movement",
        "Blurred or lost vision",
        "Severe headache",
        "Difficulty breathing",
        "Swollen face, hands or feet",
        "High fever",
        "Prolonged labour (>24h)",
        "Severe abdominal pain",
        "Leaking fluid from the vagina"
    )
}
