package com.triagemate.chps.data.repository

import android.content.Context
import android.util.Log
import com.triagemate.chps.domain.model.DownloadState
import com.triagemate.chps.domain.repository.ModelDownloadRepository
import com.triagemate.chps.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelDownloadRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    private var downloadJob: Job? = null

    init {
        if (isModelAvailable()) {
            Log.i(TAG, "init: model already present, emitting Completed")
            _state.value = DownloadState.Completed
        }
    }

    override fun isModelAvailable(): Boolean =
        File(context.getExternalFilesDir(null), Constants.MODEL_FILENAME).exists()

    override fun getDownloadState(): Flow<DownloadState> = _state.asStateFlow()

    override fun startDownload() {
        downloadJob?.cancel()
        _state.value = DownloadState.Enqueued

        val destination = File(context.getExternalFilesDir(null), Constants.MODEL_FILENAME)
        if (destination.exists()) {
            Log.i(TAG, "startDownload: deleting stale file (${destination.length()} bytes)")
            destination.delete()
        }

        Log.i(TAG, "startDownload: destination=${destination.absolutePath}")
        Log.i(TAG, "startDownload: url=${Constants.MODEL_DOWNLOAD_URL}")

        downloadJob = scope.launch {
            var attempt = 0
            while (isActive) {
                attempt++
                Log.i(TAG, "download: attempt $attempt")
                try {
                    performDownload(destination)
                    break
                } catch (e: CancellationException) {
                    Log.i(TAG, "download: cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "download: attempt $attempt failed — ${e::class.simpleName}: ${e.message}")
                    if (attempt >= 3) {
                        Log.e(TAG, "download: giving up after $attempt attempts")
                        _state.value = DownloadState.Failed(
                            "Download failed after $attempt attempts. Please check your connection and try again."
                        )
                        break
                    }
                    Log.i(TAG, "download: retrying in 5 s…")
                    delay(5_000)
                }
            }
        }
    }

    private suspend fun performDownload(destination: File) {
        val connection = openConnection(Constants.MODEL_DOWNLOAD_URL)
        val responseCode = connection.responseCode
        Log.i(TAG, "performDownload: HTTP $responseCode  contentLength=${connection.contentLengthLong}")

        if (responseCode !in 200..299) {
            connection.disconnect()
            val msg = when (responseCode) {
                401, 403 -> "Access denied (HTTP $responseCode). The model file requires authentication."
                404      -> "Model file not found on server (HTTP 404)."
                429      -> "Too many requests. Please wait a moment and try again."
                in 500..599 -> "Server error (HTTP $responseCode). Please try again later."
                else     -> "Unexpected response (HTTP $responseCode). Please try again."
            }
            Log.e(TAG, "performDownload: $msg")
            _state.value = DownloadState.Failed(msg)
            return
        }

        val total = connection.contentLengthLong
        Log.i(TAG, "performDownload: file size = $total bytes (${total / 1_000_000} MB)")

        var downloaded = 0L
        var lastLoggedMb = 0L
        var lastEmitTime = 0L
        val emitIntervalMs = 100L
        val buffer = ByteArray(32 * 1024)

        try {
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read

                        val progress = if (total > 0L) downloaded.toFloat() / total else 0f
                        val now = System.currentTimeMillis()
                        val isComplete = total > 0L && downloaded >= total
                        if (now - lastEmitTime >= emitIntervalMs || isComplete) {
                            lastEmitTime = now
                            _state.value = DownloadState.Downloading(downloaded, total, progress)
                        }

                        val mb = downloaded / 1_000_000
                        if (mb - lastLoggedMb >= 50) {
                            lastLoggedMb = mb
                            Log.d(TAG, "performDownload: $mb MB / ${total / 1_000_000} MB  (${(progress * 100).toInt()}%)")
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        val finalSize = destination.length()
        Log.i(TAG, "performDownload: complete — file size on disk = $finalSize bytes")

        if (total > 0L && finalSize < total) {
            Log.e(TAG, "performDownload: incomplete! expected $total got $finalSize")
            destination.delete()
            throw Exception("Download incomplete (expected $total bytes, got $finalSize)")
        }

        _state.value = DownloadState.Completed
    }

    private fun openConnection(rawUrl: String): HttpURLConnection {
        var url = rawUrl
        var connection: HttpURLConnection
        var redirects = 0

        while (true) {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "*/*")
                connectTimeout = 30_000
                readTimeout    = 60_000
                instanceFollowRedirects = false  // we follow manually to log each hop
            }
            val code = connection.responseCode
            if (code in 300..399 && redirects < 15) {
                val location = connection.getHeaderField("Location")
                Log.d(TAG, "openConnection: HTTP $code redirect → $location")
                connection.disconnect()
                url = location
                redirects++
            } else {
                Log.d(TAG, "openConnection: resolved after $redirects redirect(s), HTTP $code")
                return connection
            }
        }
    }

    override fun cancelDownload() {
        Log.i(TAG, "cancelDownload: cancelling job and deleting partial file")
        downloadJob?.cancel()
        downloadJob = null
        File(context.getExternalFilesDir(null), Constants.MODEL_FILENAME).delete()
        _state.value = DownloadState.Idle
    }

    companion object {
        private const val TAG = "ModelDownload"
    }
}
