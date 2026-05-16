package com.triagemate.chps.domain.usecase

import com.triagemate.chps.domain.model.HistoryEntry
import com.triagemate.chps.domain.repository.AssessmentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSessionHistoryUseCase @Inject constructor(
    private val assessmentRepository: AssessmentRepository
) {
    operator fun invoke(): Flow<List<HistoryEntry>> {
        return assessmentRepository.getHistoryEntries()
    }
}
