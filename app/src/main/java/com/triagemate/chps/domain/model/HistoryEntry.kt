package com.triagemate.chps.domain.model

/**
 * Lightweight domain model for the Assessment History screen.
 * Contains exactly what the UI card needs — no more, no less.
 */
data class HistoryEntry(
    val id: Long,
    val pathway: String,
    val patientAge: String,
    val symptoms: List<String>,
    val urgency: String,
    val timestamp: Long,
    val safetyOverrideApplied: Boolean = false
)
