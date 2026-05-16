package com.triagemate.chps.domain.model

import android.net.Uri

data class TriageInput(
    val pathway: Pathway,
    val symptoms: List<String> = emptyList(),
    val presentingComplaint: String = "",
    val photoUri: Uri? = null,
    val patientAge: String = "",
    val patientSex: String = "",
    val medications: String = "",
    val confirmedVisualFinding: VisualFinding? = null,
    val assessmentDurationMillis: Long = 0L
)
