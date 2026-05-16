package com.triagemate.chps.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.triagemate.chps.domain.model.DownloadState
import com.triagemate.chps.domain.repository.ModelDownloadRepository
import com.triagemate.chps.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager
) : ModelDownloadRepository {

    private val prefs = context.getSharedPreferences("model_download_prefs", Context.MODE_PRIVATE)
    private val keyDownloadId = "download_id"

    override fun isModelAvailable(): Boolean {
        return File(context.getExternalFilesDir(null), Constants.MODEL_FILENAME).exists()
    }

    override fun startDownload() {
        // Remove any stale download first
        val existingId = prefs.getLong(keyDownloadId, -1L)
        if (existingId != -1L) downloadManager.remove(existingId)

        val request = DownloadManager.Request(Uri.parse(Constants.MODEL_DOWNLOAD_URL)).apply {
            setTitle("TriageMate AI Model")
            setDescription("Downloading Gemma 4 (2.58 GB)…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, null, Constants.MODEL_FILENAME)
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            )
        }
        val id = downloadManager.enqueue(request)
        prefs.edit().putLong(keyDownloadId, id).apply()
    }

    override fun cancelDownload() {
        val id = prefs.getLong(keyDownloadId, -1L)
        if (id != -1L) {
            downloadManager.remove(id)
            prefs.edit().remove(keyDownloadId).apply()
        }
    }

    override fun getDownloadState(): Flow<DownloadState> = flow {
        while (true) {
            val downloadId = prefs.getLong(keyDownloadId, -1L)
            if (downloadId == -1L) {
                emit(DownloadState.Idle)
                delay(1000)
                continue
            }

            val cursor = downloadManager.query(
                DownloadManager.Query().setFilterById(downloadId)
            )

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                emit(DownloadState.Idle)
                delay(1000)
                continue
            }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            cursor.close()

            val state: DownloadState = when (status) {
                DownloadManager.STATUS_PENDING -> DownloadState.Enqueued
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED -> {
                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                    DownloadState.Downloading(downloaded, total, progress)
                }
                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Completed
                DownloadManager.STATUS_FAILED -> {
                    val msg = when (reason) {
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space."
                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found."
                        DownloadManager.ERROR_FILE_ERROR -> "File system error."
                        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network data error."
                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error."
                        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects."
                        DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download."
                        else -> "Connection lost. Please check your network and try again."
                    }
                    prefs.edit().remove(keyDownloadId).apply()
                    DownloadState.Failed(msg)
                }
                else -> DownloadState.Idle
            }

            emit(state)
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)
}
