package com.triagemate.chps.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assessments")
data class AssessmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "pathway")
    val pathway: String,
    @ColumnInfo(name = "patient_age")
    val patientAge: String = "",
    @ColumnInfo(name = "patient_sex")
    val patientSex: String = "",
    @ColumnInfo(name = "symptoms_json")
    val symptomsJson: String = "[]",
    @ColumnInfo(name = "presenting_complaint")
    val presentingComplaint: String,
    @ColumnInfo(name = "danger_signs_json")
    val dangerSignsJson: String,
    @ColumnInfo(name = "vital_signs_json")
    val vitalSignsJson: String? = null,
    @ColumnInfo(name = "drug_interaction_json")
    val drugInteractionJson: String? = null,
    @ColumnInfo(name = "urgency")
    val urgency: String,
    @ColumnInfo(name = "action")
    val action: String,
    @ColumnInfo(name = "referral_note")
    val referralNote: String,
    @ColumnInfo(name = "photo_uri")
    val photoUri: String? = null,
    @ColumnInfo(name = "visual_finding")
    val visualFinding: String? = null,
    @ColumnInfo(name = "confirmed_visual_finding_json")
    val confirmedVisualFindingJson: String? = null,
    @ColumnInfo(name = "tool_call_log_json")
    val toolCallLogJson: String = "[]",
    @ColumnInfo(name = "agent_rounds")
    val agentRounds: Int = 0,
    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long = 0L,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING",
    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null,
    @ColumnInfo(name = "compound_id")
    val compoundId: String = "",
    @ColumnInfo(name = "safety_override_applied", defaultValue = "0")
    val safetyOverrideApplied: Boolean = false,
    @ColumnInfo(name = "safety_override_reason")
    val safetyOverrideReason: String? = null,
    @ColumnInfo(name = "original_gemma_urgency")
    val originalGemmaUrgency: String? = null,
    @ColumnInfo(name = "confidence_level", defaultValue = "HIGH")
    val confidenceLevel: String = "HIGH"
)
