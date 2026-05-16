package com.triagemate.chps.domain.model

import com.triagemate.chps.domain.safety.SafetyOverrideResult

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
    val currentRound: Int = 0,
    val safetyOverride: SafetyOverrideResult? = null
)

enum class AgenticStatus {
    COMPLETE,
    AWAITING_VITALS,
    ERROR
}
