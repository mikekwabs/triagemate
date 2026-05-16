package com.triagemate.chps.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.triagemate.chps.BuildConfig
import com.triagemate.chps.data.local.db.AssessmentDao
import com.triagemate.chps.data.local.model.AssessmentEntity
import com.triagemate.chps.data.local.prefs.CompoundPreferences
import com.triagemate.chps.data.local.prefs.CompoundProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AssessmentSyncRecord(
    val localId: Long,
    val timestamp: Long,
    val pathway: String,
    val urgency: String,
    val dangerSigns: String,
    val presentingComplaint: String,
    val referralNote: String,
    val agentRounds: Int,
    val hasVisualFinding: Boolean
)

@Serializable
data class SyncPayload(
    val token: String,
    val compoundId: String,
    val compoundName: String,
    val district: String,
    val region: String,
    val appVersion: String,
    val syncTimestamp: Long,
    val assessments: List<AssessmentSyncRecord>
)

@Serializable
data class SyncResponse(
    val success: Boolean,
    val message: String? = null
)

sealed class SyncResult {
    data object NothingToSync : SyncResult()
    data class Success(val count: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

private val lenientJson = Json { ignoreUnknownKeys = true }

@Singleton
class SyncEngine @Inject constructor(
    private val assessmentDao: AssessmentDao,
    private val compoundPreferences: CompoundPreferences,
    @ApplicationContext private val context: Context
) {
    private val endpoint = BuildConfig.SYNC_ENDPOINT_URL
    private val secret = BuildConfig.SYNC_SECRET

    suspend fun syncNow(): SyncResult = withContext(Dispatchers.IO) {
        try {
            if (endpoint.isBlank()) {
                return@withContext SyncResult.Error("Sync endpoint not configured")
            }

            val pending = assessmentDao.getPendingAssessments()
            if (pending.isEmpty()) return@withContext SyncResult.NothingToSync

            val profile = compoundPreferences.getProfile()
                ?: return@withContext SyncResult.Error("Compound profile not set")

            val payload = buildSyncPayload(pending, profile)
            val response = postToGoogleSheets(payload)

            if (response.success) {
                val ids = pending.map { it.id }
                val timestamp = System.currentTimeMillis()
                assessmentDao.markAsSynced(ids, timestamp)
                compoundPreferences.updateLastSync(timestamp)
                SyncResult.Success(pending.size)
            } else {
                SyncResult.Error(response.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Sync failed")
        }
    }

    fun scheduleSyncOnConnectivity() {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "triagemate_sync",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun buildSyncPayload(
        assessments: List<AssessmentEntity>,
        profile: CompoundProfile
    ): SyncPayload {
        return SyncPayload(
            token = secret,
            compoundId = profile.compoundId,
            compoundName = profile.compoundName,
            district = profile.district,
            region = profile.region,
            appVersion = profile.appVersion,
            syncTimestamp = System.currentTimeMillis(),
            assessments = assessments.map { entity ->
                AssessmentSyncRecord(
                    localId = entity.id,
                    timestamp = entity.timestamp,
                    pathway = entity.pathway,
                    urgency = entity.urgency,
                    dangerSigns = entity.dangerSignsJson,
                    presentingComplaint = entity.presentingComplaint,
                    referralNote = entity.referralNote,
                    agentRounds = entity.agentRounds,
                    hasVisualFinding = entity.confirmedVisualFindingJson != null || entity.visualFinding != null
                )
            }
        )
    }

    private suspend fun postToGoogleSheets(payload: SyncPayload): SyncResponse = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        try {
            val jsonBody = lenientJson.encodeToString(SyncPayload.serializer(), payload)
            connection.outputStream.use { it.write(jsonBody.toByteArray()) }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().readText()
                lenientJson.decodeFromString(SyncResponse.serializer(), body)
            } else {
                SyncResponse(success = false, message = "HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
