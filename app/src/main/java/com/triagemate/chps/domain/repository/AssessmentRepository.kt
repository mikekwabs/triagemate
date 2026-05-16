package com.triagemate.chps.domain.repository

import com.triagemate.chps.domain.model.AgenticTriageResult
import com.triagemate.chps.domain.model.HistoryEntry
import com.triagemate.chps.domain.model.TriageInput
import com.triagemate.chps.domain.model.TriageResult
import kotlinx.coroutines.flow.Flow

interface AssessmentRepository {
    suspend fun saveAssessment(input: TriageInput, result: AgenticTriageResult): Long
    suspend fun getAssessmentById(id: Long): TriageResult?
    /**
     * Reconstructs the patient input that produced the stored assessment.
     * Used by the result screen's "Learn about this case" feature to
     * rebuild context for an explanation prompt without re-running triage.
     */
    suspend fun getInputById(id: Long): TriageInput?
    fun getAssessmentHistory(): Flow<List<TriageResult>>
    fun getHistoryEntries(): Flow<List<HistoryEntry>>
    suspend fun deleteHistory()
}
