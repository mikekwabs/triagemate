package com.triagemate.chps.util

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
