package com.triagemate.chps.domain.usecase

import com.triagemate.chps.domain.model.AgenticTriageResult
import com.triagemate.chps.domain.model.TriageInput
import com.triagemate.chps.domain.repository.AssessmentRepository
import javax.inject.Inject

class SaveAssessmentUseCase @Inject constructor(
    private val assessmentRepository: AssessmentRepository
) {
    suspend operator fun invoke(input: TriageInput, result: AgenticTriageResult): Long {
        return assessmentRepository.saveAssessment(input, result)
    }
}
