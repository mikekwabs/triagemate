package com.triagemate.chps.domain.usecase

import com.triagemate.chps.domain.model.AgenticTriageResult
import com.triagemate.chps.domain.model.TriageInput
import com.triagemate.chps.domain.repository.InferenceRepository
import javax.inject.Inject

class RunTriageUseCase @Inject constructor(
    private val inferenceRepository: InferenceRepository
) {
    suspend operator fun invoke(input: TriageInput): AgenticTriageResult {
        return inferenceRepository.runTriage(input)
    }
}
