package com.triagemate.chps.data.local.prefs

import android.content.Context
import com.triagemate.chps.BuildConfig
import at.favre.lib.crypto.bcrypt.BCrypt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CompoundProfile(
    val compoundName: String,
    val district: String,
    val region: String,
    val compoundId: String,
    val supervisorPinHash: String,
    val setupComplete: Boolean,
    val appVersion: String
)

@Singleton
class CompoundPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _profileFlow = MutableStateFlow(readProfile())
    val profileFlow: StateFlow<CompoundProfile?> = _profileFlow.asStateFlow()

    private val _lastSyncFlow = MutableStateFlow(readLastSync())
    val lastSyncFlow: StateFlow<Long?> = _lastSyncFlow.asStateFlow()

    fun isSetupComplete(): Boolean = prefs.getBoolean(KEY_SETUP_COMPLETE, false)

    fun getProfile(): CompoundProfile? = readProfile()

    fun saveProfile(profile: CompoundProfile) {
        prefs.edit()
            .putString(KEY_COMPOUND_NAME, profile.compoundName)
            .putString(KEY_DISTRICT, profile.district)
            .putString(KEY_REGION, profile.region)
            .putString(KEY_COMPOUND_ID, profile.compoundId)
            .putString(KEY_PIN_HASH, profile.supervisorPinHash)
            .putBoolean(KEY_SETUP_COMPLETE, profile.setupComplete)
            .putString(KEY_APP_VERSION, profile.appVersion)
            .apply()
        _profileFlow.value = readProfile()
    }

    fun getOrCreateCompoundId(): String {
        val existing = prefs.getString(KEY_COMPOUND_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val compoundId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_COMPOUND_ID, compoundId).apply()
        _profileFlow.value = readProfile()
        return compoundId
    }

    fun verifyPin(enteredPin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return BCrypt.verifyer().verify(enteredPin.toCharArray(), storedHash).verified
    }

    fun updateLastSync(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
        _lastSyncFlow.value = timestamp
    }

    fun getLastSync(): Long? = readLastSync()

    fun clearForTesting() {
        prefs.edit().clear().apply()
        _profileFlow.value = null
        _lastSyncFlow.value = null
    }

    private fun readProfile(): CompoundProfile? {
        val compoundName = prefs.getString(KEY_COMPOUND_NAME, null) ?: return null
        val district = prefs.getString(KEY_DISTRICT, null) ?: return null
        val region = prefs.getString(KEY_REGION, null) ?: return null
        val compoundId = prefs.getString(KEY_COMPOUND_ID, null) ?: return null
        val pinHash = prefs.getString(KEY_PIN_HASH, null) ?: return null

        return CompoundProfile(
            compoundName = compoundName,
            district = district,
            region = region,
            compoundId = compoundId,
            supervisorPinHash = pinHash,
            setupComplete = prefs.getBoolean(KEY_SETUP_COMPLETE, false),
            appVersion = prefs.getString(KEY_APP_VERSION, BuildConfig.VERSION_NAME)
                ?: BuildConfig.VERSION_NAME
        )
    }

    private fun readLastSync(): Long? {
        if (!prefs.contains(KEY_LAST_SYNC)) return null
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    private companion object {
        const val PREFS_NAME = "compound_prefs"
        const val KEY_COMPOUND_NAME = "compound_name"
        const val KEY_DISTRICT = "district"
        const val KEY_REGION = "region"
        const val KEY_COMPOUND_ID = "compound_id"
        const val KEY_PIN_HASH = "supervisor_pin_hash"
        const val KEY_SETUP_COMPLETE = "setup_complete"
        const val KEY_APP_VERSION = "app_version"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
