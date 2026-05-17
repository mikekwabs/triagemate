package com.triagemate.chps.presentation.screens.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triagemate.chps.data.engine.EngineProvider
import com.triagemate.chps.domain.model.DownloadState
import com.triagemate.chps.domain.repository.ModelDownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    private val modelDownloadRepository: ModelDownloadRepository,
    private val engineProvider: EngineProvider
) : ViewModel() {

    val downloadState: StateFlow<DownloadState> = modelDownloadRepository
        .getDownloadState()
        .onEach { state -> Log.d(TAG, "downloadState → $state") }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DownloadState.Idle
        )

    fun startDownload() {
        Log.i(TAG, "startDownload called")
        modelDownloadRepository.startDownload()
    }

    fun cancelDownload() {
        Log.i(TAG, "cancelDownload called")
        modelDownloadRepository.cancelDownload()
    }

    fun onDownloadComplete(onReady: () -> Unit) {
        // Navigate immediately — the engine load takes several seconds for a
        // 2.58 GB model and blocks the UI button. Warm it up in the background
        // so the first assessment is fast; the inference layer also calls
        // ensureEngineReady() lazily and is mutex-guarded against double init.
        onReady()
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "onDownloadComplete: warming engine in background…")
            try {
                engineProvider.ensureEngineReady()
                Log.i(TAG, "onDownloadComplete: engine ready")
            } catch (e: Exception) {
                Log.e(TAG, "onDownloadComplete: engine warm-up failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "ModelSetupVM"
    }
}
