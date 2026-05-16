package com.triagemate.chps.presentation.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.favre.lib.crypto.bcrypt.BCrypt
import com.triagemate.chps.BuildConfig
import com.triagemate.chps.data.local.prefs.CompoundPreferences
import com.triagemate.chps.data.local.prefs.CompoundProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val compoundPreferences: CompoundPreferences
) : ViewModel() {

    private val _compoundName = MutableStateFlow("")
    private val _region = MutableStateFlow("")
    private val _district = MutableStateFlow("")
    private val _pin = MutableStateFlow("")

    val compoundName: StateFlow<String> = _compoundName
    val region: StateFlow<String> = _region
    val district: StateFlow<String> = _district
    val pin: StateFlow<String> = _pin

    val regions = listOf(
        "Ahafo", "Ashanti", "Bono", "Bono East", "Central",
        "Eastern", "Greater Accra", "North East", "Northern",
        "Oti", "Savannah", "Upper East", "Upper West",
        "Volta", "Western", "Western North"
    )

    val districtsByRegion: Map<String, List<String>> = mapOf(
        "Ahafo" to listOf("Asunafo North", "Asutifi North", "Tano North"),
        "Ashanti" to listOf("Kumasi Metro", "Ejisu", "Obuasi Municipal", "Mampong Municipal"),
        "Bono" to listOf("Sunyani Municipal", "Berekum West", "Dormaa Central"),
        "Bono East" to listOf("Techiman Municipal", "Kintampo North", "Atebubu-Amantin"),
        "Central" to listOf("Cape Coast Metro", "Komenda Edina Eguafo Abrem", "Mfantsiman"),
        "Eastern" to listOf("New Juaben South", "Birim Central", "Akuapem North"),
        "Greater Accra" to listOf("Accra Metro", "Ga East", "Tema Metro", "Ashaiman Municipal"),
        "North East" to listOf("Nalerigu-Gambaga", "West Mamprusi", "Bunkpurugu-Nakpanduri"),
        "Northern" to listOf("Tamale Metro", "Sagnarigu", "Yendi Municipal"),
        "Oti" to listOf("Jasikan", "Kadjebi", "Krachi East"),
        "Savannah" to listOf("West Gonja", "Bole", "Sawla-Tuna-Kalba"),
        "Upper East" to listOf("Bolgatanga Municipal", "Bawku Municipal", "Kassena-Nankana West"),
        "Upper West" to listOf("Wa Municipal", "Jirapa", "Lawra"),
        "Volta" to listOf("Ho Municipal", "Keta Municipal", "Ketu South"),
        "Western" to listOf("Sekondi-Takoradi Metro", "Tarkwa-Nsuaem", "Ahanta West"),
        "Western North" to listOf("Sefwi Wiawso", "Bibiani Anhwiaso Bekwai", "Aowin")
    )

    val canProceedFromName: StateFlow<Boolean> = _compoundName
        .map { it.trim().isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canProceedFromLocation: StateFlow<Boolean> = combine(_region, _district) { region, district ->
        region.isNotBlank() && district.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val pinComplete: StateFlow<Boolean> = _pin
        .map { it.length == 4 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun updateCompoundName(name: String) {
        _compoundName.value = name
    }

    fun updateRegion(region: String) {
        _region.value = region
        if (_district.value !in districtsByRegion[region].orEmpty()) {
            _district.value = ""
        }
    }

    fun updateDistrict(district: String) {
        _district.value = district
    }

    fun updatePin(digit: String) {
        if (_pin.value.length >= 4) return
        _pin.update { current -> current + digit.take(1) }
    }

    fun deleteLastDigit() {
        if (_pin.value.isNotEmpty()) {
            _pin.value = _pin.value.dropLast(1)
        }
    }

    fun completeSetup() {
        val compoundId = compoundPreferences.getOrCreateCompoundId()
        val pinHash = BCrypt.withDefaults().hashToString(12, _pin.value.toCharArray())
        compoundPreferences.saveProfile(
            CompoundProfile(
                compoundName = _compoundName.value.trim(),
                district = _district.value,
                region = _region.value,
                compoundId = compoundId,
                supervisorPinHash = pinHash,
                setupComplete = true,
                appVersion = BuildConfig.VERSION_NAME
            )
        )
    }
}
