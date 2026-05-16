package com.triagemate.chps.domain.model

/**
 * Records a single tool invocation during the agentic triage loop.
 * Used for the full audit trail stored in Room.
 */
data class ToolCallRecord(
    val round: Int,
    val toolName: String,
    val arguments: String,
    val timestamp: Long,
    val result: String? = null
)
