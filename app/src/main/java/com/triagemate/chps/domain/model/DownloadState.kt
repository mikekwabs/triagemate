package com.triagemate.chps.domain.model

sealed class DownloadState {
    object Idle : DownloadState()
    object Enqueued : DownloadState()
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progress: Float          // 0f..1f
    ) : DownloadState()
    object Completed : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}
