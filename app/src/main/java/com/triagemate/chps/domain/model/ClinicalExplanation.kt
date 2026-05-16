package com.triagemate.chps.domain.model

/**
 * A short, post-assessment educational explanation generated on-demand
 * for a finished [TriageResult]. Surfaced via the "Learn about this case"
 * card on the result screen.
 *
 * This is purely educational support for the CHO — it does NOT alter the
 * triage decision in any way. The classification, action and referral
 * note are already final by the time this is generated.
 */
data class ClinicalExplanation(
    val whyThisClassification: String,
    val whatToWatchFor: String,
    val clinicalReference: String,
    val generatedAt: Long = System.currentTimeMillis()
)
