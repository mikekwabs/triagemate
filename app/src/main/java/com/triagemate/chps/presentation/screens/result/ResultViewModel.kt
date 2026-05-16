package com.triagemate.chps.presentation.screens.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triagemate.chps.domain.model.TriageResult
import com.triagemate.chps.domain.repository.AssessmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultUiState(
    val result: TriageResult? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val assessmentRepository: AssessmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        // Android restores this id from SavedStateHandle after process death,
        // so the Result screen always has a path back to its data.
        val id = savedStateHandle.get<Long>("id") ?: -1L
        if (id != -1L) {
            viewModelScope.launch {
                val result = assessmentRepository.getAssessmentById(id)
                _uiState.value = ResultUiState(result = result, isLoading = false)
            }
        } else {
            _uiState.value = ResultUiState(result = null, isLoading = false)
        }
    }
}
