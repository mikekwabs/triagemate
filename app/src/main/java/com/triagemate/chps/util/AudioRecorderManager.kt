package com.triagemate.chps.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures raw 16-bit PCM mono audio at 16 kHz — the exact format that
 * Gemma 4 E2B's USM audio encoder accepts.
 *
 * We deliberately use [AudioRecord] (not [android.media.MediaRecorder]),
 * because MediaRecorder always wraps audio in a container (AMR_WB, AAC, etc.)
 * which Gemma's encoder cannot consume.
 *
 * The recording is held in memory and capped at [MAX_RECORDING_MS] so we
 * never exceed Gemma's 30-second audio window.
 */
@Singleton
class AudioRecorderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioRecorderManager"
        const val SAMPLE_RATE = 16_000
        const val MAX_RECORDING_MS = 28_000L  // leave headroom under Gemma's 30s cap
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val isRecording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isCurrentlyRecording(): Boolean = isRecording.get()

    /**
     * Records audio until [stop] is called or [MAX_RECORDING_MS] elapses.
     * Suspends on a single thread and returns the captured PCM bytes.
     *
     * Throws [SecurityException] if RECORD_AUDIO is not granted, or
     * [IllegalStateException] if the AudioRecord fails to initialise.
     */
    suspend fun startAndCollect(): ByteArray = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            throw SecurityException("RECORD_AUDIO permission has not been granted.")
        }
        if (isRecording.get()) {
            throw IllegalStateException("Audio recorder is already active.")
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Device does not support 16 kHz mono PCM capture.")
        }
        // Use a generous buffer (4x min) to tolerate scheduling jitter under model warmup.
        val bufferSize = minBufferSize * 4

        @Suppress("MissingPermission")
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialise.")
        }

        recorder = record
        val output = ByteArrayOutputStream()
        val readBuffer = ByteArray(bufferSize)

        try {
            record.startRecording()
            isRecording.set(true)
            val startedAt = System.currentTimeMillis()
            Log.d(TAG, "startAndCollect: recording started")

            while (isRecording.get()) {
                val read = record.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    output.write(readBuffer, 0, read)
                }
                if (System.currentTimeMillis() - startedAt >= MAX_RECORDING_MS) {
                    Log.w(TAG, "startAndCollect: reached MAX_RECORDING_MS — stopping")
                    break
                }
            }
        } finally {
            isRecording.set(false)
            try { record.stop() } catch (_: Exception) {}
            record.release()
            recorder = null
            Log.d(TAG, "startAndCollect: recording stopped, captured=${output.size()} bytes")
        }

        output.toByteArray()
    }

    /**
     * Wraps raw PCM 16-bit / mono samples in a WAV (RIFF) container so
     * LiteRT-LM's miniaudio decoder can consume it. Sending unwrapped PCM
     * fails with miniaudio error -10 (invalid file).
     */
    fun pcmToWav(
        pcm: ByteArray,
        sampleRate: Int = SAMPLE_RATE,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val dataSize = pcm.size
        val totalSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                  // PCM subchunk size
        header.putShort(1)                 // PCM format
        header.putShort(channels)
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        return header.array() + pcm
    }

    /** Stops a recording started by [startAndCollect]. Safe to call when idle. */
    fun stop() {
        if (isRecording.compareAndSet(true, false)) {
            Log.d(TAG, "stop: stop requested")
        }
    }
}
