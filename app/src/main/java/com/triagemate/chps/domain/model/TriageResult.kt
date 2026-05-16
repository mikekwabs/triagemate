package com.triagemate.chps.domain.model

import android.net.Uri

data class TriageResult(
    val urgency: String,
    val action: String,
    val reportedSymptoms: List<String> = emptyList(),
    val dangerSignsDetected: List<String>,
    val referralNote: String,
    val visualFinding: String? = null,
    val confirmedVisualFinding: VisualFinding? = null,
    val photoUri: Uri? = null,
    val vitalSigns: Map<String, String> = emptyMap(),
    val toolCallLog: List<ToolCallRecord> = emptyList(),
    val rawJson: String? = null
)
