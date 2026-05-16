package com.triagemate.chps.util

import com.triagemate.chps.domain.model.Pathway

val AUTO_RED_CHILD_U5 = setOf(
    "Unable to drink or breastfeed",
    "Vomiting everything",
    "Convulsions",
    "Lethargic or unconscious",
    "Stridor at rest",
    "Severe chest indrawing"
)

val AUTO_RED_ANTENATAL = setOf(
    "Heavy vaginal bleeding",
    "Fits or convulsions",
    "Fetal movements stopped",
    "Cord prolapse"
)

fun isAutoRedSign(symptom: String, pathway: Pathway): Boolean = when (pathway) {
    Pathway.CHILD_U5  -> symptom in AUTO_RED_CHILD_U5
    Pathway.ANTENATAL -> symptom in AUTO_RED_ANTENATAL
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
        "Stridor"
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
        "Prolonged labour (>24h)"
    )
}
