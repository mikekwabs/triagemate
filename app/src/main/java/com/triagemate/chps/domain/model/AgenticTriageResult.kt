package com.triagemate.chps.domain.model

/**
 * The result of an agentic multi-tool triage session.
 *
 * The agentic loop may pause mid-session (e.g. to collect vital signs from the CHO),
 * so this class carries both the final result and any intermediate state needed to
 * resume the loop.
 */
data class AgenticTriageResult(
    val status: AgenticStatus,
    val triageResult: TriageResult? = null,
    val referralNote: String? = null,
    val visualFinding: String? = null,
    val confirmedVisualFinding: VisualFinding? = null,
    val requiredVitals: List<String>? = null,
    val vitalSignsCollected: Map<String, String>? = null,
    val drugInteraction: String? = null,
    val toolCallLog: List<ToolCallRecord> = emptyList(),
    val currentRound: Int = 0
)

/**
 * Represents the current state of the agentic triage loop.
 */
enum class AgenticStatus {
    /** Full triage complete — result and referral note are available */
    COMPLETE,
    /** Paused — waiting for the CHO to enter vital sign measurements */
    AWAITING_VITALS,
    /** Something went wrong — fallback AMBER classification is in triageResult */
    ERROR
}
