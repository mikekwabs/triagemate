package com.triagemate.chps.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triagemate.chps.data.local.db.AssessmentDao
import com.triagemate.chps.data.local.prefs.CompoundPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val compoundPreferences: CompoundPreferences,
    assessmentDao: AssessmentDao
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    val pendingCasesCount: StateFlow<Int> = assessmentDao.getPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lastSyncText: StateFlow<String> = compoundPreferences.lastSyncFlow
        .map(::formatRelativeTime)
        .stateIn(viewModelScope, SharingStarted.Eagerly, formatRelativeTime(compoundPreferences.getLastSync()))

    val compoundName: StateFlow<String> = compoundPreferences.profileFlow
        .map { it?.compoundName ?: "TriageMate" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, compoundPreferences.getProfile()?.compoundName ?: "TriageMate")

    val homeSubtitle: StateFlow<String> = combine(
        compoundPreferences.profileFlow,
        pendingCasesCount
    ) { profile, pending ->
        if (profile == null) {
            "Ready - offline triage active."
        } else {
            "${profile.compoundName} ready - $pending pending case${if (pending == 1) "" else "s"}."
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Ready - offline triage active.")

    private fun formatRelativeTime(timestamp: Long?): String {
        if (timestamp == null) return "Never synced"
        val delta = System.currentTimeMillis() - timestamp
        return when {
            delta < TimeUnit.MINUTES.toMillis(1) -> "Just synced"
            delta < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(delta)} mins ago"
            delta < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(delta)} hrs ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(delta)} days ago"
        }
    }
}
