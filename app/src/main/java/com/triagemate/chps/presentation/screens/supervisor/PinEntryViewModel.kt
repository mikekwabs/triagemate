package com.triagemate.chps.presentation.screens.supervisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triagemate.chps.data.local.prefs.CompoundPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinEntryUiState(
    val enteredDigits: List<Int> = emptyList(),
    val attempts: Int = 0,
    val errorMessage: String? = null,
    val shakeAnimation: Boolean = false,
    val lockoutSecondsRemaining: Int = 0,
    val verified: Boolean = false
)

@HiltViewModel
class PinEntryViewModel @Inject constructor(
    private val compoundPreferences: CompoundPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinEntryUiState())
    val uiState: StateFlow<PinEntryUiState> = _uiState.asStateFlow()

    fun enterDigit(digit: Int) {
        val current = _uiState.value
        if (current.lockoutSecondsRemaining > 0 || current.enteredDigits.size >= 4) return

        val updated = current.enteredDigits + digit
        _uiState.value = current.copy(enteredDigits = updated, errorMessage = null, shakeAnimation = false)

        if (updated.size == 4) {
            verifyPin(updated.joinToString(""))
        }
    }

    fun deleteLastDigit() {
        val current = _uiState.value
        if (current.lockoutSecondsRemaining > 0 || current.enteredDigits.isEmpty()) return
        _uiState.value = current.copy(
            enteredDigits = current.enteredDigits.dropLast(1),
            errorMessage = null,
            shakeAnimation = false
        )
    }

    private fun verifyPin(pin: String) {
        if (compoundPreferences.verifyPin(pin)) {
            _uiState.value = _uiState.value.copy(verified = true, errorMessage = null, shakeAnimation = false)
            return
        }

        val newAttempts = _uiState.value.attempts + 1
        if (newAttempts >= 5) {
            startLockout(newAttempts)
        } else {
            _uiState.value = _uiState.value.copy(
                enteredDigits = emptyList(),
                attempts = newAttempts,
                errorMessage = "Incorrect PIN. Try again.",
                shakeAnimation = true
            )
            clearShakeFlag()
        }
    }

    private fun startLockout(attempts: Int) {
        _uiState.value = _uiState.value.copy(
            enteredDigits = emptyList(),
            attempts = attempts,
            errorMessage = "Too many attempts. Wait 30 seconds.",
            shakeAnimation = true,
            lockoutSecondsRemaining = 30
        )
        clearShakeFlag()
        viewModelScope.launch {
            for (seconds in 29 downTo 0) {
                delay(1_000)
                _uiState.value = _uiState.value.copy(lockoutSecondsRemaining = seconds)
            }
            _uiState.value = _uiState.value.copy(
                attempts = 0,
                errorMessage = null,
                lockoutSecondsRemaining = 0
            )
        }
    }

    private fun clearShakeFlag() {
        viewModelScope.launch {
            delay(350)
            _uiState.value = _uiState.value.copy(shakeAnimation = false)
        }
    }
}
