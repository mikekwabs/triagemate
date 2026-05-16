package com.triagemate.chps.presentation.screens.assessment

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.presentation.components.ActivityLogCard
import com.triagemate.chps.presentation.components.ClinicalPhotoCard
import com.triagemate.chps.presentation.components.UrgentActionBanner
import com.triagemate.chps.presentation.components.SymptomCheckItem
import com.triagemate.chps.presentation.components.VitalSignsSheet
import com.triagemate.chps.presentation.components.VoiceInputCard
import com.triagemate.chps.presentation.components.VoiceInputRow
import com.triagemate.chps.presentation.theme.PrimaryNavy
import com.triagemate.chps.presentation.theme.StepperTeal
import com.triagemate.chps.util.CameraTier
import com.triagemate.chps.util.Constants
import kotlinx.coroutines.launch

@Composable
private fun AssessmentStepper(currentStep: Int, modifier: Modifier = Modifier) {
    val labels = listOf("Input", "Assessment", "Result")

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, _ ->
                val step = index + 1
                val isComplete = step < currentStep
                val isActive = step == currentStep

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isComplete || isActive) StepperTeal else Color.Transparent)
                        .then(
                            if (!isComplete && !isActive) Modifier.border(2.dp, Color(0xFFBDBDBD), CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isComplete) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            text = step.toString(),
                            color = if (isActive) Color.White else Color(0xFFBDBDBD),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                if (index < labels.size - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (step < currentStep) StepperTeal else Color(0xFFE0E0E0))
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val step = index + 1
                val isComplete = step < currentStep
                val isActive = step == currentStep
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = if (index == 0) TextAlign.Start else if (index == labels.lastIndex) TextAlign.End else TextAlign.Center,
                    fontSize = 11.sp,
                    color = if (isActive || isComplete) StepperTeal else Color(0xFF9E9E9E),
                    fontWeight = if (isActive || isComplete) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentScreen(
    pathway: Pathway,
    onResultReady: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: AssessmentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val symptoms = if (pathway == Pathway.CHILD_U5) Constants.CHILD_U5_SYMPTOMS else Constants.ANTENATAL_SYMPTOMS
    val isChildU5 = pathway == Pathway.CHILD_U5
    val isAnalysingAssessment = uiState.isLoading || uiState.awaitingVitals
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    val currentStep = if (isAnalysingAssessment) 2 else 1
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceRecording()
    }

    val onVoiceTap: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(pathway) { viewModel.initPathway(pathway) }

    if (uiState.awaitingVitals) {
        ModalBottomSheet(
            onDismissRequest = { },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            VitalSignsSheet(
                requiredVitals = uiState.requiredVitals,
                vitalValues = uiState.vitalSignValues,
                onVitalChanged = { key, value -> viewModel.updateVitalSign(key, value) },
                onSubmit = {
                    scope.launch {
                        sheetState.hide()
                        viewModel.submitVitals(onResultReady)
                    }
                },
                onSkip = {
                    scope.launch {
                        sheetState.hide()
                        viewModel.skipVitals(onResultReady)
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isChildU5) "Child Under 5 Assessment" else "Antenatal Assessment",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF2C3E50)
                )
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            AssessmentStepper(currentStep = currentStep)
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE8E8E8))

            AnimatedContent(
                targetState = isAnalysingAssessment,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "AnalysisTransition"
            ) { analysing ->
                if (analysing) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Spacer(Modifier.weight(1f))
                        ActivityLogCard(
                            uiState = uiState,
                            hasPhoto = uiState.capturedPhotoUri != null
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SignalWifiOff,
                                contentDescription = null,
                                tint = Color(0xFF9E9E9E),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Running on-device — no internet required",
                                color = Color(0xFF9E9E9E),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            state = listState,
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Patient Details",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryNavy
                                        )
                                        Spacer(Modifier.height(12.dp))

                                        Text(
                                            text = if (isChildU5) "Age (months)" else "Gestational Age (weeks)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF4A4A4A)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = uiState.patientAge,
                                            onValueChange = { viewModel.updateAge(it) },
                                            placeholder = {
                                                Text(if (isChildU5) "e.g. 14" else "e.g. 28", color = Color(0xFFBDC3C7))
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                                focusedBorderColor = StepperTeal,
                                                unfocusedContainerColor = Color.White,
                                                focusedContainerColor = Color.White
                                            ),
                                            singleLine = true
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        if (isChildU5) {
                                            Text(
                                                text = "Patient sex",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF4A4A4A)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                SexChip(
                                                    label = "Male",
                                                    selected = uiState.patientSex.equals("MALE", true),
                                                    onClick = { viewModel.updateSex("MALE") }
                                                )
                                                SexChip(
                                                    label = "Female",
                                                    selected = uiState.patientSex.equals("FEMALE", true),
                                                    onClick = { viewModel.updateSex("FEMALE") }
                                                )
                                            }
                                            Spacer(Modifier.height(12.dp))
                                        }

                                        Text(
                                            text = "Current medications",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF4A4A4A)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = uiState.medications,
                                            onValueChange = { viewModel.updateMedications(it) },
                                            placeholder = {
                                                Text("e.g. amoxicillin (leave empty if none)", color = Color(0xFFBDC3C7))
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                                focusedBorderColor = StepperTeal,
                                                unfocusedContainerColor = Color.White,
                                                focusedContainerColor = Color.White
                                            ),
                                            singleLine = true
                                        )
                                    }
                                }
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    if (uiState.voiceInputState is VoiceInputState.Idle) {
                                        VoiceInputRow(
                                            enabled = !isAnalysingAssessment,
                                            onClick = onVoiceTap
                                        )
                                    } else {
                                        VoiceInputCard(
                                            state = uiState.voiceInputState,
                                            onStop = viewModel::stopVoiceRecording,
                                            onDismiss = viewModel::dismissVoiceInput,
                                            onRetry = {
                                                viewModel.retryVoiceInput()
                                                onVoiceTap()
                                            }
                                        )
                                    }
                                }
                            }

                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Presenting Symptoms",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2C3E50)
                                    )
                                }
                            }

                            if (uiState.cameraTier is CameraTier.StrongCue && uiState.visualCueSuggestion != null) {
                                item {
                                    VisualCuePromptBanner(
                                        title = uiState.visualCueSuggestion!!.title,
                                        reason = uiState.visualCueSuggestion!!.reason,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (uiState.cameraTier is CameraTier.SuppressCamera) {
                                item {
                                    UrgentActionBanner(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            items(symptoms) { symptom ->
                                SymptomCheckItem(
                                    label = symptom,
                                    checked = uiState.selectedSymptoms.contains(symptom),
                                    onCheckedChange = { viewModel.toggleSymptom(symptom) }
                                )
                            }

                            if (hasCamera && uiState.cameraTier !is CameraTier.SuppressCamera) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        ClinicalPhotoCard(
                                            cameraTier = uiState.cameraTier,
                                            visualState = uiState.visualAssessmentState,
                                            capturedPhotoUri = uiState.capturedPhotoUri,
                                            onPhotoCaptured = viewModel::onPhotoCaptured,
                                            onConfirmFinding = viewModel::confirmVisualFinding,
                                            onDismissFinding = viewModel::dismissVisualFinding,
                                            onRemovePhoto = viewModel::removePhoto
                                        )
                                    }
                                }
                            }

                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { viewModel.runAssessment(onResultReady) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                enabled = uiState.canStartAssessment,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryNavy,
                                    disabledContainerColor = Color(0xFFD1D5DB)
                                )
                            ) {
                                Text("Start Assessment", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }

                            val helperText = when (uiState.visualAssessmentState) {
                                is VisualAssessmentState.QualityChecking -> "Checking photo quality..."
                                is VisualAssessmentState.QualityFailed -> (uiState.visualAssessmentState as VisualAssessmentState.QualityFailed).reason
                                is VisualAssessmentState.Analysing -> "Waiting for visual analysis..."
                                is VisualAssessmentState.FindingReady -> "Confirm or dismiss the visual finding to continue"
                                is VisualAssessmentState.LowConfidenceFinding -> "Low-confidence visual finding - you may continue"
                                is VisualAssessmentState.AnalysisError -> (uiState.visualAssessmentState as VisualAssessmentState.AnalysisError).message
                                else -> null
                            }
                            if (helperText != null) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = helperText,
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.runAssessment(onResultReady) }) {
                            Text("Retry")
                        }
                    }
                ) {
                    Text(uiState.error!!)
                }
            }
        }
    }
}

@Composable
private fun SexChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) StepperTeal.copy(alpha = 0.14f) else Color.White,
            labelColor = if (selected) StepperTeal else Color(0xFF4A4A4A)
        )
    )
}

@Composable
private fun VisualCuePromptBanner(
    title: String,
    reason: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFFBEB))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFF3D6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.RemoveRedEye,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.size(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Suggested: $title",
                color = Color(0xFF111111),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = reason,
                color = Color(0xFF6B7280),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
