package com.triagemate.chps.presentation.screens.assessment

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triagemate.chps.domain.model.AgenticStatus
import com.triagemate.chps.domain.model.AgenticTriageResult
import com.triagemate.chps.domain.model.ConfidenceLevel
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.domain.model.ToolCallRecord
import com.triagemate.chps.domain.model.TriageInput
import com.triagemate.chps.domain.model.VisualFinding
import com.triagemate.chps.domain.model.VoiceInputResult
import com.triagemate.chps.domain.repository.InferenceRepository
import com.triagemate.chps.util.AudioRecorderManager
import com.triagemate.chps.domain.usecase.RunTriageUseCase
import com.triagemate.chps.domain.usecase.SaveAssessmentUseCase
import com.triagemate.chps.util.CameraTier
import com.triagemate.chps.util.ImageQualityChecker
import com.triagemate.chps.util.VisualCue
import com.triagemate.chps.util.VisualCueMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed class VoiceInputState {
    data object Idle : VoiceInputState()
    data object Recording : VoiceInputState()
    data object Processing : VoiceInputState()
    data class Confirmed(
        val translation: String,
        val matchedSymptoms: List<String>
    ) : VoiceInputState()
    data class Error(val message: String) : VoiceInputState()
}

sealed class VisualAssessmentState {
    data object Idle : VisualAssessmentState()
    data object QualityChecking : VisualAssessmentState()
    data class QualityFailed(val reason: String) : VisualAssessmentState()
    data object Analysing : VisualAssessmentState()
    data class FindingReady(val finding: VisualFinding) : VisualAssessmentState()
    data class LowConfidenceFinding(val finding: VisualFinding) : VisualAssessmentState()
    data class Confirmed(val finding: VisualFinding) : VisualAssessmentState()
    data object Dismissed : VisualAssessmentState()
    data object NoFinding : VisualAssessmentState()
    data class AnalysisError(val message: String) : VisualAssessmentState()
}

data class AssessmentUiState(
    val pathway: Pathway = Pathway.CHILD_U5,
    val selectedSymptoms: Set<String> = emptySet(),
    val patientAge: String = "",
    val patientSex: String = "",
    val medications: String = "",
    val capturedPhotoUri: Uri? = null,
    val cameraTier: CameraTier = CameraTier.WeakCue,
    val visualAssessmentState: VisualAssessmentState = VisualAssessmentState.Idle,
    val visualCueSuggestion: VisualCue? = null,
    val canStartAssessment: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val awaitingVitals: Boolean = false,
    val requiredVitals: List<String> = emptyList(),
    val vitalSignValues: Map<String, String> = emptyMap(),
    val toolCallLog: List<ToolCallRecord> = emptyList(),
    val currentRound: Int = 0,
    val dangerSignCount: Int = 0,
    val assessmentStartMs: Long = 0L,
    val voiceInputState: VoiceInputState = VoiceInputState.Idle,
)

@HiltViewModel
class AssessmentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runTriageUseCase: RunTriageUseCase,
    private val saveAssessmentUseCase: SaveAssessmentUseCase,
    private val inferenceRepository: InferenceRepository,
    private val audioRecorderManager: AudioRecorderManager
) : ViewModel() {

    private val pathway = MutableStateFlow(Pathway.CHILD_U5)
    private val selectedSymptoms = MutableStateFlow<Set<String>>(emptySet())
    private val patientAge = MutableStateFlow("")
    private val patientSex = MutableStateFlow("")
    private val medications = MutableStateFlow("")
    private val capturedPhotoUri = MutableStateFlow<Uri?>(null)
    private val visualAssessmentMutableState = MutableStateFlow<VisualAssessmentState>(VisualAssessmentState.Idle)
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val awaitingVitals = MutableStateFlow(false)
    private val requiredVitals = MutableStateFlow<List<String>>(emptyList())
    private val vitalSignValues = MutableStateFlow<Map<String, String>>(emptyMap())
    private val toolCallLog = MutableStateFlow<List<ToolCallRecord>>(emptyList())
    private val currentRound = MutableStateFlow(0)
    private val dangerSignCount = MutableStateFlow(0)
    private val assessmentStartMs = MutableStateFlow(0L)
    private val voiceInputState = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)

    val cameraTier: StateFlow<CameraTier> =
        combine(selectedSymptoms, pathway, patientAge) { symptoms, currentPathway, age ->
            VisualCueMapper.computeTier(symptoms.toList(), currentPathway, age.toIntOrNull())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, CameraTier.WeakCue)

    val visualCueSuggestion: StateFlow<VisualCue?> =
        cameraTier
            .map { tier -> (tier as? CameraTier.StrongCue)?.cue }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val visualAssessmentState: StateFlow<VisualAssessmentState> = visualAssessmentMutableState.asStateFlow()

    val canStartAssessment: StateFlow<Boolean> =
        combine(selectedSymptoms, visualAssessmentMutableState) { symptoms, visualState ->
            symptoms.isNotEmpty() &&
                visualState !is VisualAssessmentState.QualityChecking &&
                visualState !is VisualAssessmentState.Analysing &&
                visualState !is VisualAssessmentState.FindingReady
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<AssessmentUiState> = combine(
        pathway,
        selectedSymptoms,
        patientAge,
        patientSex,
        medications,
        capturedPhotoUri,
        cameraTier,
        visualAssessmentMutableState,
        visualCueSuggestion,
        canStartAssessment,
        isLoading,
        error,
        awaitingVitals,
        requiredVitals,
        vitalSignValues,
        toolCallLog,
        currentRound,
        dangerSignCount,
        assessmentStartMs,
        voiceInputState
    ) { values: Array<Any?> ->
        AssessmentUiState(
            pathway = values[0] as Pathway,
            selectedSymptoms = values[1] as Set<String>,
            patientAge = values[2] as String,
            patientSex = values[3] as String,
            medications = values[4] as String,
            capturedPhotoUri = values[5] as Uri?,
            cameraTier = values[6] as CameraTier,
            visualAssessmentState = values[7] as VisualAssessmentState,
            visualCueSuggestion = values[8] as VisualCue?,
            canStartAssessment = values[9] as Boolean,
            isLoading = values[10] as Boolean,
            error = values[11] as String?,
            awaitingVitals = values[12] as Boolean,
            requiredVitals = values[13] as List<String>,
            vitalSignValues = values[14] as Map<String, String>,
            toolCallLog = values[15] as List<ToolCallRecord>,
            currentRound = values[16] as Int,
            dangerSignCount = values[17] as Int,
            assessmentStartMs = values[18] as Long,
            voiceInputState = values[19] as VoiceInputState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssessmentUiState())

    private var currentInput: TriageInput? = null
    private var visualAnalysisJob: Job? = null
    private var voiceRecordingJob: Job? = null

    fun initPathway(pathway: Pathway) {
        this.pathway.value = pathway
    }

    fun toggleSymptom(symptom: String) {
        val current = selectedSymptoms.value
        selectedSymptoms.value = if (current.contains(symptom)) current - symptom else current + symptom
    }

    fun updateAge(age: String) {
        patientAge.value = age
    }

    fun updateSex(sex: String) {
        patientSex.value = sex
    }

    fun updateMedications(meds: String) {
        medications.value = meds
    }

    fun onPhotoCaptured(uri: Uri?) {
        if (uri == null) return
        val persistedUri = persistPhoto(uri) ?: run {
            visualAssessmentMutableState.value = VisualAssessmentState.AnalysisError("Unable to save captured photo")
            return
        }

        capturedPhotoUri.value?.takeIf { it != persistedUri }?.let(::deletePhoto)
        capturedPhotoUri.value = persistedUri

        visualAnalysisJob?.cancel()
        visualAnalysisJob = viewModelScope.launch {
            visualAssessmentMutableState.value = VisualAssessmentState.QualityChecking
            try {
                val quality = ImageQualityChecker.check(persistedUri, context)
                if (!quality.isAcceptable) {
                    if (capturedPhotoUri.value == persistedUri) {
                        visualAssessmentMutableState.value = VisualAssessmentState.QualityFailed(
                            quality.reason ?: "Photo quality insufficient"
                        )
                    }
                    return@launch
                }

                val cue = when (val tier = cameraTier.value) {
                    is CameraTier.StrongCue -> tier.cue
                    else -> VisualCue.generic()
                }

                visualAssessmentMutableState.value = VisualAssessmentState.Analysing
                val finding = inferenceRepository.analyseVisualSign(
                    imageUri = persistedUri,
                    visualCue = cue,
                    pathway = pathway.value,
                    selectedSymptoms = selectedSymptoms.value.toList()
                )
                if (capturedPhotoUri.value != persistedUri) return@launch
                visualAssessmentMutableState.value = if (finding.detected) {
                    if (finding.confidence == ConfidenceLevel.LOW) {
                        VisualAssessmentState.LowConfidenceFinding(finding)
                    } else {
                        VisualAssessmentState.FindingReady(finding)
                    }
                } else {
                    VisualAssessmentState.NoFinding
                }
            } catch (e: Exception) {
                if (capturedPhotoUri.value == persistedUri) {
                    visualAssessmentMutableState.value = VisualAssessmentState.AnalysisError(
                    e.message ?: "Visual analysis failed"
                    )
                }
            }
        }
    }

    fun confirmVisualFinding() {
        val state = visualAssessmentMutableState.value
        if (state is VisualAssessmentState.FindingReady || state is VisualAssessmentState.LowConfidenceFinding) {
            val finding = when (state) {
                is VisualAssessmentState.FindingReady -> state.finding
                is VisualAssessmentState.LowConfidenceFinding -> state.finding
                else -> return
            }
            visualAssessmentMutableState.value = VisualAssessmentState.Confirmed(
                finding.copy(confirmedByCHO = true, dismissed = false)
            )
        }
    }

    fun dismissVisualFinding() {
        val state = visualAssessmentMutableState.value
        visualAssessmentMutableState.value = when (state) {
            is VisualAssessmentState.FindingReady -> VisualAssessmentState.Dismissed
            is VisualAssessmentState.Confirmed -> VisualAssessmentState.Dismissed
            is VisualAssessmentState.LowConfidenceFinding -> VisualAssessmentState.Dismissed
            else -> VisualAssessmentState.Dismissed
        }
    }

    fun retakePhoto() {
        visualAnalysisJob?.cancel()
        capturedPhotoUri.value?.let(::deletePhoto)
        capturedPhotoUri.value = null
        visualAssessmentMutableState.value = VisualAssessmentState.Idle
    }

    fun removePhoto() {
        visualAnalysisJob?.cancel()
        capturedPhotoUri.value?.let(::deletePhoto)
        capturedPhotoUri.value = null
        visualAssessmentMutableState.value = VisualAssessmentState.Idle
    }

    fun updateVitalSign(vitalName: String, value: String) {
        val current = vitalSignValues.value.toMutableMap()
        current[vitalName] = value
        vitalSignValues.value = current
    }

    fun runAssessment(onSuccess: (Long) -> Unit) {
        if (!canStartAssessment.value) return

        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            isLoading.value = true
            error.value = null
            awaitingVitals.value = false
            vitalSignValues.value = emptyMap()
            toolCallLog.value = emptyList()
            dangerSignCount.value = 0
            assessmentStartMs.value = startMs

            val complaint = buildComplaint(medications.value)
            val confirmedFinding = (visualAssessmentMutableState.value as? VisualAssessmentState.Confirmed)?.finding
            val input = TriageInput(
                pathway = pathway.value,
                symptoms = selectedSymptoms.value.toList(),
                presentingComplaint = complaint,
                patientAge = patientAge.value,
                patientSex = patientSex.value,
                medications = medications.value,
                photoUri = capturedPhotoUri.value,
                confirmedVisualFinding = confirmedFinding
            )
            currentInput = input

            try {
                val agenticResult = runTriageUseCase(input)
                handleAgenticResult(agenticResult, input, onSuccess)
            } catch (e: Exception) {
                isLoading.value = false
                error.value = e.message ?: "An unknown error occurred"
            }
        }
    }

    fun submitVitals(onSuccess: (Long) -> Unit) {
        val input = currentInput ?: return

        viewModelScope.launch {
            isLoading.value = true
            awaitingVitals.value = false
            error.value = null

            try {
                val agenticResult = inferenceRepository.resumeWithVitals(vitalSignValues.value)
                handleAgenticResult(agenticResult, input, onSuccess)
            } catch (e: Exception) {
                isLoading.value = false
                error.value = e.message ?: "Failed to resume assessment"
            }
        }
    }

    fun skipVitals(onSuccess: (Long) -> Unit) {
        val input = currentInput ?: return

        viewModelScope.launch {
            isLoading.value = true
            awaitingVitals.value = false
            error.value = null
            vitalSignValues.value = emptyMap()

            try {
                val agenticResult = inferenceRepository.resumeWithVitals(emptyMap())
                handleAgenticResult(agenticResult, input, onSuccess)
            } catch (e: Exception) {
                isLoading.value = false
                error.value = e.message ?: "Failed to continue without vitals"
            }
        }
    }

    private suspend fun handleAgenticResult(
        result: AgenticTriageResult,
        input: TriageInput,
        onSuccess: (Long) -> Unit
    ) {
        when (result.status) {
            AgenticStatus.AWAITING_VITALS -> {
                dangerSignCount.value = extractDangerSignCount(result.toolCallLog)
                isLoading.value = false
                awaitingVitals.value = true
                requiredVitals.value = result.requiredVitals ?: emptyList()
                toolCallLog.value = result.toolCallLog
                currentRound.value = result.currentRound
            }

            AgenticStatus.COMPLETE, AgenticStatus.ERROR -> {
                val duration = (System.currentTimeMillis() - assessmentStartMs.value).coerceAtLeast(0L)
                val savedId = saveAssessmentUseCase(
                    input.copy(assessmentDurationMillis = duration),
                    result
                )
                isLoading.value = false
                onSuccess(savedId)
            }
        }
    }

    private fun buildComplaint(medications: String): String {
        return if (medications.isNotBlank()) {
            "Multiple symptoms reported via checklist. Current medications: $medications."
        } else {
            "Multiple symptoms reported via checklist."
        }
    }

    private fun extractDangerSignCount(log: List<ToolCallRecord>): Int {
        val assessResult = log.firstOrNull { it.toolName == "assessSymptoms" }?.result ?: return 0
        return try {
            val json = JSONObject(assessResult)
            json.optJSONArray("danger_signs")?.length() ?: json.optInt("danger_sign_count", 0)
        } catch (_: Exception) {
            0
        }
    }

    private fun persistPhoto(sourceUri: Uri): Uri? {
        return runCatching {
            val sourceFile = when (sourceUri.scheme) {
                "file", null -> File(requireNotNull(sourceUri.path))
                else -> null
            }

            val photoDir = File(context.filesDir, "clinical_photos").apply { mkdirs() }
            val destination = File(photoDir, "photo_${UUID.randomUUID()}.jpg")

            val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false

            if (!copied) {
                if (sourceFile?.exists() == true) {
                    sourceFile.copyTo(destination, overwrite = true)
                } else {
                    error("Unable to read captured photo")
                }
            }

            if (sourceFile?.exists() == true && sourceFile.absolutePath != destination.absolutePath) {
                sourceFile.delete()
            }

            Uri.fromFile(destination)
        }.getOrNull()
    }

    fun startVoiceRecording() {
        if (voiceRecordingJob?.isActive == true) return
        if (!audioRecorderManager.hasPermission()) {
            voiceInputState.value = VoiceInputState.Error("Microphone permission is required to record voice input.")
            return
        }

        voiceInputState.value = VoiceInputState.Recording
        voiceRecordingJob = viewModelScope.launch {
            try {
                val pcmBytes = audioRecorderManager.startAndCollect()
                if (pcmBytes.isEmpty()) {
                    voiceInputState.value = VoiceInputState.Error("No audio was captured. Please try again.")
                    return@launch
                }

                voiceInputState.value = VoiceInputState.Processing
                val wavBytes = audioRecorderManager.pcmToWav(pcmBytes)
                val result = inferenceRepository.processVoiceInput(
                    audioBytes = wavBytes,
                    pathway = pathway.value
                )
                when (result) {
                    is VoiceInputResult.Success -> {
                        if (result.extractedSymptoms.isNotEmpty()) {
                            val current = selectedSymptoms.value.toMutableSet()
                            current.addAll(result.extractedSymptoms)
                            selectedSymptoms.value = current
                        }
                        voiceInputState.value = VoiceInputState.Confirmed(
                            translation = result.rawTranslation,
                            matchedSymptoms = result.extractedSymptoms
                        )
                    }
                    is VoiceInputResult.Error -> {
                        voiceInputState.value = VoiceInputState.Error(result.message)
                    }
                }
            } catch (e: SecurityException) {
                voiceInputState.value = VoiceInputState.Error("Microphone permission is required to record voice input.")
            } catch (e: Exception) {
                voiceInputState.value = VoiceInputState.Error(e.message ?: "Voice input failed.")
            }
        }
    }

    fun stopVoiceRecording() {
        audioRecorderManager.stop()
    }

    fun dismissVoiceInput() {
        voiceRecordingJob?.cancel()
        audioRecorderManager.stop()
        voiceInputState.value = VoiceInputState.Idle
    }

    fun retryVoiceInput() {
        voiceRecordingJob?.cancel()
        audioRecorderManager.stop()
        voiceInputState.value = VoiceInputState.Idle
    }

    private fun deletePhoto(uri: Uri) {
        if (uri.scheme == "file" || uri.scheme == null) {
            uri.path?.let { File(it).delete() }
        }
    }
}
