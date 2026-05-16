package com.triagemate.chps.data.repository

import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.triagemate.chps.data.local.db.AssessmentDao
import com.triagemate.chps.data.local.model.AssessmentEntity
import com.triagemate.chps.data.local.prefs.CompoundPreferences
import com.triagemate.chps.domain.model.AgenticTriageResult
import com.triagemate.chps.domain.model.HistoryEntry
import com.triagemate.chps.domain.model.ToolCallRecord
import com.triagemate.chps.domain.model.TriageInput
import com.triagemate.chps.domain.model.TriageResult
import com.triagemate.chps.domain.model.VisualFinding
import com.triagemate.chps.domain.repository.AssessmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class AssessmentRepositoryImpl @Inject constructor(
    private val assessmentDao: AssessmentDao,
    private val compoundPreferences: CompoundPreferences,
    private val moshi: Moshi
) : AssessmentRepository {

    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )
    private val visualFindingAdapter = moshi.adapter(VisualFinding::class.java)

    override suspend fun saveAssessment(input: TriageInput, result: AgenticTriageResult): Long {
        val triage = result.triageResult ?: TriageResult(
            urgency = "AMBER",
            action = "Use clinical judgement",
            reportedSymptoms = input.symptoms,
            dangerSignsDetected = emptyList(),
            referralNote = "Assessment incomplete",
            visualFinding = result.visualFinding,
            confirmedVisualFinding = result.confirmedVisualFinding,
            photoUri = input.photoUri
        )

        val entity = AssessmentEntity(
            pathway = input.pathway.displayName,
            patientAge = input.patientAge,
            patientSex = input.patientSex,
            symptomsJson = stringListAdapter.toJson(input.symptoms),
            presentingComplaint = input.presentingComplaint,
            dangerSignsJson = stringListAdapter.toJson(triage.dangerSignsDetected),
            vitalSignsJson = result.vitalSignsCollected?.let { JSONObject(it).toString() },
            drugInteractionJson = result.drugInteraction,
            urgency = triage.urgency,
            action = triage.action,
            referralNote = result.referralNote ?: triage.referralNote,
            photoUri = input.photoUri?.toString(),
            visualFinding = result.visualFinding ?: triage.visualFinding,
            confirmedVisualFindingJson = result.confirmedVisualFinding?.let(visualFindingAdapter::toJson),
            toolCallLogJson = serializeToolCallLog(result.toolCallLog),
            agentRounds = result.currentRound,
            durationMillis = input.assessmentDurationMillis,
            syncStatus = "PENDING",
            syncedAt = null,
            compoundId = compoundPreferences.getOrCreateCompoundId()
        )
        return assessmentDao.insertAssessment(entity)
    }

    override fun getAssessmentHistory(): Flow<List<TriageResult>> {
        return assessmentDao.getAllAssessments().map { entities ->
            entities.map { entity ->
                TriageResult(
                    urgency = entity.urgency,
                    action = entity.action,
                    reportedSymptoms = stringListAdapter.fromJson(entity.symptomsJson) ?: emptyList(),
                    dangerSignsDetected = stringListAdapter.fromJson(entity.dangerSignsJson) ?: emptyList(),
                    referralNote = entity.referralNote,
                    visualFinding = entity.visualFinding,
                    confirmedVisualFinding = entity.confirmedVisualFindingJson?.let(visualFindingAdapter::fromJson),
                    photoUri = entity.photoUri?.let(Uri::parse),
                    rawJson = null
                )
            }
        }
    }

    override fun getHistoryEntries(): Flow<List<HistoryEntry>> {
        return assessmentDao.getAllAssessments().map { entities ->
            entities.map { entity ->
                HistoryEntry(
                    id = entity.id,
                    pathway = entity.pathway,
                    patientAge = entity.patientAge,
                    symptoms = stringListAdapter.fromJson(entity.symptomsJson) ?: emptyList(),
                    urgency = entity.urgency,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    override suspend fun deleteHistory() {
        assessmentDao.deleteAllAssessments()
    }

    override suspend fun getAssessmentById(id: Long): TriageResult? {
        val entity = assessmentDao.getAssessmentById(id) ?: return null
        val vitalSigns: Map<String, String> = entity.vitalSignsJson?.let { json ->
            try {
                val obj = JSONObject(json)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } catch (e: Exception) { emptyMap() }
        } ?: emptyMap()
        return TriageResult(
            urgency             = entity.urgency,
            action              = entity.action,
            reportedSymptoms    = stringListAdapter.fromJson(entity.symptomsJson) ?: emptyList(),
            dangerSignsDetected = stringListAdapter.fromJson(entity.dangerSignsJson) ?: emptyList(),
            referralNote        = entity.referralNote,
            visualFinding       = entity.visualFinding,
            confirmedVisualFinding = entity.confirmedVisualFindingJson?.let(visualFindingAdapter::fromJson),
            photoUri            = entity.photoUri?.let(Uri::parse),
            vitalSigns          = vitalSigns,
            toolCallLog         = deserializeToolCallLog(entity.toolCallLogJson),
            rawJson             = null
        )
    }

    private fun deserializeToolCallLog(json: String): List<ToolCallRecord> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ToolCallRecord(
                    round     = obj.getInt("round"),
                    toolName  = obj.getString("tool"),
                    arguments = obj.optString("args", ""),
                    timestamp = obj.optLong("ts", 0L),
                    result    = if (obj.has("result")) obj.getString("result") else null
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun serializeToolCallLog(log: List<ToolCallRecord>): String {
        val arr = JSONArray()
        log.forEach { record ->
            arr.put(JSONObject().apply {
                put("round", record.round)
                put("tool", record.toolName)
                put("args", record.arguments)
                put("ts", record.timestamp)
                record.result?.let { put("result", it) }
            })
        }
        return arr.toString()
    }
}
