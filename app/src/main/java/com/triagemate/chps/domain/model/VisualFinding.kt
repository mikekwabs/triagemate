package com.triagemate.chps.domain.model

enum class FindingType {
    ESCALATING,
    CONFIRMING,
    NEW_OBSERVATION
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class VisualFinding(
    val detected: Boolean,
    val findingText: String,
    val clinicalImplication: String,
    val referralNoteText: String,
    val findingType: FindingType = FindingType.NEW_OBSERVATION,
    val confidence: ConfidenceLevel = ConfidenceLevel.LOW,
    val confirmedByCHO: Boolean = false,
    val dismissed: Boolean = false,
    val imageQualitySufficient: Boolean = true
)
