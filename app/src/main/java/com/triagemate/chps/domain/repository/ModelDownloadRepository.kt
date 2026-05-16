package com.triagemate.chps.domain.repository

import com.triagemate.chps.domain.model.DownloadState
import kotlinx.coroutines.flow.Flow

interface ModelDownloadRepository {
    fun getDownloadState(): Flow<DownloadState>
    fun startDownload()
    fun cancelDownload()
    fun isModelAvailable(): Boolean
}
