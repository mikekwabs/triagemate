package com.triagemate.chps.data.engine

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.triagemate.chps.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that owns the LiteRT-LM [Engine] instance.
 * Supports hot-reload: [ensureEngineReady] can be called at any time after
 * the model file is downloaded — no app restart needed.
 *
 * Backend selection is automatic based on device capabilities:
 *  - GPU (OpenCL) on devices with 10+ GB total RAM
 *  - CPU on all other devices (safe fallback)
 *
 * If GPU is selected but fails at runtime (broken driver, insufficient VRAM),
 * it silently falls back to CPU.
 */
@Singleton
class EngineProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EngineProvider"

        /** Minimum total device RAM (in MB) required to attempt GPU backend.
         *  GPU duplicates model weights into VRAM (~1.5 GB for this model),
         *  so we need at least ~10 GB total to avoid OOM kills. */
        private const val GPU_MIN_RAM_MB = 10_000L
    }

    private var _engine: Engine? = null
    val engine: Engine? get() = _engine

    @Volatile private var isInitialized = false
    private val mutex = Mutex()

    /** The backend that was successfully used to build the engine. */
    var activeBackend: String = "NONE"
        private set

    /**
     * Returns total device RAM in megabytes.
     */
    private fun getTotalRamMB(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    /**
     * Determines the best backend for this device based on available RAM.
     */
    private fun selectBackend(): Backend {
        val totalRamMB = getTotalRamMB()
        Log.d(TAG, "selectBackend: totalRAM=${totalRamMB}MB, GPU threshold=${GPU_MIN_RAM_MB}MB")

        return if (totalRamMB >= GPU_MIN_RAM_MB) {
            Log.d(TAG, "selectBackend: Device has sufficient RAM → attempting GPU")
            Backend.GPU()
        } else {
            Log.d(TAG, "selectBackend: Insufficient RAM for GPU → using CPU")
            Backend.CPU()
        }
    }

    private fun shouldEnableMtp(modelPath: String, backend: Backend): Boolean {
        val isGemma4Model = modelPath.contains("gemma-4", ignoreCase = true)
        return isGemma4Model && backend is Backend.GPU
    }

    /**
     * Builds the [Engine] using the best available backend for this device.
     *
     * Strategy:
     *  1. Check device RAM → pick GPU or CPU
     *  2. If GPU was picked but fails → silently fall back to CPU
     *  3. If CPU fails → report failure (should never happen)
     *
     * Returns true if an engine was built (or already exists), false if the
     * model file is not yet present on disk.
     */
    @OptIn(ExperimentalApi::class)
    private fun tryBuildEngine(): Boolean {
        if (_engine != null) return true

        val modelPath = context.getExternalFilesDir(null)!!.absolutePath +
                "/" + Constants.MODEL_FILENAME
        if (!File(modelPath).exists()) return false

        val preferredBackend = selectBackend()
        val enableGpuMtp = shouldEnableMtp(modelPath, preferredBackend)

        // ── Try preferred backend ──────────────────────────────────────
        if (preferredBackend is Backend.GPU) {
            try {
                ExperimentalFlags.enableSpeculativeDecoding = enableGpuMtp
                Log.d(
                    TAG,
                    "tryBuildEngine: GPU path speculative decoding (MTP) " +
                        if (enableGpuMtp) "ENABLED" else "DISABLED"
                )
                _engine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend   = Backend.GPU(),
                        visionBackend = Backend.GPU(),
                        cacheDir  = context.cacheDir.path
                    )
                )
                activeBackend = "GPU"
                Log.d(TAG, "tryBuildEngine: Engine built with GPU backend")
                return true
            } catch (gpuEx: Exception) {
                Log.w(TAG, "tryBuildEngine: GPU failed (${gpuEx.message}) — falling back to CPU")
                _engine = null
            }
        }

        // ── CPU (either primary choice or GPU fallback) ────────────────
        return try {
            ExperimentalFlags.enableSpeculativeDecoding = false
            Log.d(TAG, "tryBuildEngine: CPU path speculative decoding (MTP) DISABLED")
            _engine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend   = Backend.CPU(),
                    visionBackend = Backend.CPU(),
                    cacheDir  = context.cacheDir.path
                )
            )
            activeBackend = "CPU"
            Log.d(TAG, "tryBuildEngine: Engine built with CPU backend")
            true
        } catch (cpuEx: Exception) {
            Log.e(TAG, "tryBuildEngine: CPU backend also failed: ${cpuEx.message}", cpuEx)
            _engine = null
            false
        }
    }

    /**
     * Ensures the engine is built AND initialized. Everything runs on
     * [Dispatchers.IO] — safe to call from any thread including Main.
     * Uses a [Mutex] to prevent concurrent double-initialization.
     */
    suspend fun ensureEngineReady(): Boolean {
        if (isInitialized) return true
        return mutex.withLock {
            if (isInitialized) return@withLock true
            withContext(Dispatchers.IO) {
                if (!tryBuildEngine()) return@withContext false
                _engine!!.initialize()
                isInitialized = true
                Log.d(TAG, "ensureEngineReady: Engine initialized on $activeBackend backend (RAM: ${getTotalRamMB()}MB)")
                true
            }
        }
    }
}
