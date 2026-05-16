package com.triagemate.chps.presentation.screens.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triagemate.chps.domain.model.ClinicalExplanation
import com.triagemate.chps.domain.model.ConfidenceLevel
import com.triagemate.chps.domain.model.TriageInput
import com.triagemate.chps.domain.model.TriageResult
import com.triagemate.chps.domain.repository.AssessmentRepository
import com.triagemate.chps.domain.repository.InferenceRepository
import com.triagemate.chps.domain.safety.SafetyOverrideResult
import com.triagemate.chps.util.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultUiState(
    val result: TriageResult? = null,
    val input: TriageInput? = null,
    val isLoading: Boolean = true
)

sealed class LearnMoreState {
    object Idle : LearnMoreState()
    object Loading : LearnMoreState()
    data class Ready(val explanation: ClinicalExplanation) : LearnMoreState()
    data class Error(val message: String) : LearnMoreState()
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val assessmentRepository: AssessmentRepository,
    private val inferenceRepository: InferenceRepository,
    private val textToSpeechManager: TextToSpeechManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    val confidence: StateFlow<ConfidenceLevel> = _uiState
        .map { it.result?.confidence ?: ConfidenceLevel.HIGH }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConfidenceLevel.HIGH)

    val safetyOverride: StateFlow<SafetyOverrideResult?> = _uiState
        .map { state ->
            val r = state.result ?: return@map null
            if (!r.safetyOverrideApplied) return@map null
            SafetyOverrideResult(
                finalUrgency = r.urgency,
                wasOverridden = true,
                originalGemmaUrgency = r.originalGemmaUrgency ?: r.urgency,
                overrideReason = r.safetyOverrideReason,
                overriddenSigns = r.dangerSignsDetected
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _learnMoreState = MutableStateFlow<LearnMoreState>(LearnMoreState.Idle)
    val learnMoreState: StateFlow<LearnMoreState> = _learnMoreState.asStateFlow()

    init {
        val id = savedStateHandle.get<Long>("id") ?: -1L
        if (id != -1L) {
            viewModelScope.launch {
                val result = assessmentRepository.getAssessmentById(id)
                val input = assessmentRepository.getInputById(id)
                _uiState.value = ResultUiState(result = result, input = input, isLoading = false)
            }
        } else {
            _uiState.value = ResultUiState(result = null, input = null, isLoading = false)
        }
    }

    /**
     * Fired ONLY when the CHO taps the "Learn about this case" card.
     * Never auto-runs. Caches the result on first success so re-expanding
     * the card is free.
     */
    fun loadClinicalExplanation() {
        if (_learnMoreState.value is LearnMoreState.Ready) return
        if (_learnMoreState.value is LearnMoreState.Loading) return

        val result = _uiState.value.result ?: run {
            _learnMoreState.value = LearnMoreState.Error("No assessment loaded.")
            return
        }
        val input = _uiState.value.input ?: run {
            _learnMoreState.value = LearnMoreState.Error("Patient context is unavailable for this case.")
            return
        }

        _learnMoreState.value = LearnMoreState.Loading
        viewModelScope.launch {
            try {
                val explanation = inferenceRepository.generateClinicalExplanation(
                    result = result,
                    input = input,
                    safetyOverride = safetyOverride.value
                )
                _learnMoreState.value = LearnMoreState.Ready(explanation)
            } catch (e: Exception) {
                _learnMoreState.value = LearnMoreState.Error(
                    e.message ?: "The explanation could not be generated."
                )
            }
        }
    }

    fun retryClinicalExplanation() {
        _learnMoreState.value = LearnMoreState.Idle
        loadClinicalExplanation()
    }

    /**
     * Reads the final urgency + recommended action aloud over the device
     * speaker. Toggle: if already speaking, the second tap stops playback.
     */
    fun toggleSpeak() {
        if (_isSpeaking.value) {
            textToSpeechManager.stop()
            _isSpeaking.value = false
            return
        }
        val result = _uiState.value.result ?: return
        val urgencyWord = when (result.urgency.uppercase()) {
            "RED" -> "Red, urgent referral."
            "AMBER" -> "Amber, refer for review."
            "GREEN" -> "Green, manage locally."
            else -> "${result.urgency}."
        }
        val script = "Triage outcome: $urgencyWord ${result.action}".trim()
        _isSpeaking.value = true
        textToSpeechManager.speak(script) {
            _isSpeaking.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeechManager.stop()
    }
}
