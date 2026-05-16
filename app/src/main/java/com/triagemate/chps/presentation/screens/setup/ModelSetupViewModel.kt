package com.triagemate.chps.presentation.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triagemate.chps.data.engine.EngineProvider
import com.triagemate.chps.domain.model.DownloadState
import com.triagemate.chps.domain.repository.ModelDownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    private val modelDownloadRepository: ModelDownloadRepository,
    private val engineProvider: EngineProvider
) : ViewModel() {

    val downloadState: StateFlow<DownloadState> = modelDownloadRepository
        .getDownloadState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadState.Idle
        )

    fun startDownload() {
        modelDownloadRepository.startDownload()
    }

    fun cancelDownload() {
        modelDownloadRepository.cancelDownload()
    }

    /**
     * Called when download is confirmed complete.
     * Builds the Engine in the background, then invokes [onReady] on the main thread.
     */
    fun onDownloadComplete(onReady: () -> Unit) {
        viewModelScope.launch {
            engineProvider.ensureEngineReady()
            onReady()
        }
    }
}
