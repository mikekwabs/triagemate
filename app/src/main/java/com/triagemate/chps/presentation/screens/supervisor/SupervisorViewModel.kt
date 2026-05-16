package com.triagemate.chps.presentation.screens.supervisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triagemate.chps.data.local.db.AssessmentDao
import com.triagemate.chps.data.local.db.UrgencyCount
import com.triagemate.chps.data.local.model.AssessmentEntity
import com.triagemate.chps.data.local.prefs.CompoundPreferences
import com.triagemate.chps.data.local.prefs.CompoundProfile
import com.triagemate.chps.data.sync.SyncEngine
import com.triagemate.chps.data.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SupervisorViewModel @Inject constructor(
    private val assessmentDao: AssessmentDao,
    private val compoundPreferences: CompoundPreferences,
    private val syncEngine: SyncEngine
) : ViewModel() {

    data class UrgencyDistribution(
        val red: Int = 0,
        val amber: Int = 0,
        val green: Int = 0
    ) {
        val referralRate: Float
            get() = if (red + amber + green == 0) 0f
            else (red + amber).toFloat() / (red + amber + green) * 100f
    }

    data class DailyCount(
        val date: LocalDate,
        val total: Int,
        val hasRedCase: Boolean
    )

    data class SymptomCount(
        val symptom: String,
        val count: Int,
        val isAutoRed: Boolean
    )

    sealed class SyncState {
        data object Idle : SyncState()
        data object Syncing : SyncState()
        data class Success(val count: Int) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val now: LocalDate
        get() = LocalDate.now()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _filterUrgency = MutableStateFlow<String?>(null)
    private val _filterPathway = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _showThisMonthOnly = MutableStateFlow(true)

    val filterUrgency: StateFlow<String?> = _filterUrgency.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val showThisMonthOnly: StateFlow<Boolean> = _showThisMonthOnly.asStateFlow()

    val compoundProfile: StateFlow<CompoundProfile?> = compoundPreferences.profileFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, compoundPreferences.getProfile())

    private val startOfMonthMillis: Long
        get() = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val startOfLastMonthMillis: Long
        get() = now.minusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val endOfLastMonthMillis: Long
        get() = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val totalThisMonth: StateFlow<Int> = assessmentDao.getCountThisMonth(startOfMonthMillis)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lastMonthTotal: StateFlow<Int> = assessmentDao.getCountBetween(startOfLastMonthMillis, endOfLastMonthMillis)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val urgencyDistribution: StateFlow<UrgencyDistribution> =
        assessmentDao.getUrgencyDistributionThisMonth(startOfMonthMillis)
            .map(::mapUrgencyDistribution)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UrgencyDistribution())

    val averageDurationMillis: StateFlow<Long> = assessmentDao.getAverageDurationThisMonth(startOfMonthMillis)
        .map { avg -> avg?.toLong() ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val allAssessments: StateFlow<List<AssessmentEntity>> = assessmentDao.getAllAssessments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dailyCounts: StateFlow<List<DailyCount>> = allAssessments
        .map(::buildDailyCounts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val topSymptoms: StateFlow<List<SymptomCount>> = assessmentDao.getAssessmentsThisMonth(startOfMonthMillis)
        .map(::computeTopSymptoms)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalAssessmentsCount: StateFlow<Int> = allAssessments
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pendingCount: StateFlow<Int> = assessmentDao.getPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lastSyncTime: StateFlow<Long?> = compoundPreferences.lastSyncFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, compoundPreferences.getLastSync())

    val filteredAssessments: StateFlow<List<AssessmentEntity>> = combine(
        allAssessments,
        _filterUrgency,
        _filterPathway,
        _searchQuery,
        _showThisMonthOnly
    ) { assessments, urgency, pathway, query, thisMonthOnly ->
        assessments.filter { entity ->
            val matchesUrgency = urgency == null || entity.urgency.equals(urgency, ignoreCase = true)
            val matchesPathway = pathway == null || entity.pathway.contains(pathway, ignoreCase = true)
            val matchesMonth = !thisMonthOnly || entity.timestamp >= startOfMonthMillis
            val haystack = buildString {
                append(entity.pathway)
                append(' ')
                append(entity.patientSex)
                append(' ')
                append(entity.patientAge)
                append(' ')
                append(entity.urgency)
                append(' ')
                append(entity.presentingComplaint)
                append(' ')
                append(entity.symptomsJson)
            }
            val matchesQuery = query.isBlank() || haystack.contains(query, ignoreCase = true)
            matchesUrgency && matchesPathway && matchesMonth && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setUrgencyFilter(urgency: String?) {
        _filterUrgency.value = urgency
    }

    fun setPathwayFilter(pathway: String?) {
        _filterPathway.value = pathway
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setThisMonthOnly(enabled: Boolean) {
        _showThisMonthOnly.value = enabled
    }

    fun triggerSync() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            _syncState.value = when (val result = syncEngine.syncNow()) {
                SyncResult.NothingToSync -> SyncState.Success(0)
                is SyncResult.Success -> SyncState.Success(result.count)
                is SyncResult.Error -> SyncState.Error(result.message)
            }
        }
    }

    private fun mapUrgencyDistribution(counts: List<UrgencyCount>): UrgencyDistribution {
        return UrgencyDistribution(
            red = counts.firstOrNull { it.urgency.equals("RED", true) }?.count ?: 0,
            amber = counts.firstOrNull { it.urgency.equals("AMBER", true) }?.count ?: 0,
            green = counts.firstOrNull { it.urgency.equals("GREEN", true) }?.count ?: 0
        )
    }

    private fun buildDailyCounts(assessments: List<AssessmentEntity>): List<DailyCount> {
        val today = now
        val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
        return days.map { date ->
            val dayAssessments = assessments.filter {
                Instant.ofEpochMilli(it.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate() == date
            }
            DailyCount(
                date = date,
                total = dayAssessments.size,
                hasRedCase = dayAssessments.any { it.urgency.equals("RED", true) }
            )
        }
    }

    private fun computeTopSymptoms(assessments: List<AssessmentEntity>): List<SymptomCount> {
        val counts = mutableMapOf<String, Int>()
        assessments.forEach { entity ->
            runCatching {
                Json.decodeFromString<List<String>>(entity.symptomsJson)
            }.getOrDefault(emptyList()).forEach { symptom ->
                counts[symptom] = (counts[symptom] ?: 0) + 1
            }
        }

        return counts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { entry ->
                SymptomCount(
                    symptom = entry.key,
                    count = entry.value,
                    isAutoRed = entry.key in autoRedChild || entry.key in autoRedAntenatal
                )
            }
    }

    companion object {
        // Exact strings from Constants.CHILD_U5_SYMPTOMS that are WHO IMCI auto-RED
        private val autoRedChild = setOf(
            "Unable to drink or breastfeed",
            "Vomiting everything",
            "Convulsions",
            "Lethargic or unconscious",
            "Severe chest indrawing",
            "Stridor"
        )

        // Exact strings from Constants.ANTENATAL_SYMPTOMS that are GHS auto-RED
        private val autoRedAntenatal = setOf(
            "Vaginal bleeding",
            "Convulsions / fits",
            "Absent fetal movement",
            "Blurred or lost vision",
            "Severe headache",
            "Prolonged labour (>24h)"
        )
    }
}
