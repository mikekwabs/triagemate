package com.triagemate.chps.presentation.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.triagemate.chps.domain.model.ConfidenceLevel
import com.triagemate.chps.domain.model.FindingType
import com.triagemate.chps.presentation.screens.assessment.VisualAssessmentState
import com.triagemate.chps.presentation.theme.PrimaryNavy
import com.triagemate.chps.presentation.theme.StepperTeal
import com.triagemate.chps.util.CameraTier
import java.io.File
import java.util.UUID

@Composable
fun ClinicalPhotoCard(
    cameraTier: CameraTier,
    visualState: VisualAssessmentState,
    capturedPhotoUri: Uri?,
    onPhotoCaptured: (Uri?) -> Unit,
    onConfirmFinding: () -> Unit,
    onDismissFinding: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingCapture by remember { mutableStateOf<CapturedTempPhoto?>(null) }
    val bitmap = remember(capturedPhotoUri) {
        when (capturedPhotoUri?.scheme) {
            "file", null -> capturedPhotoUri?.path?.let(BitmapFactory::decodeFile)
            else -> null
        }?.asImageBitmap()
    }

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val captured = pendingCapture
        pendingCapture = null
        if (captured == null) return@rememberLauncherForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            onPhotoCaptured(captured.outputUri)
        } else {
            captured.file.delete()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraIntent(context, captureLauncher) { pendingCapture = it }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraIntent(context, captureLauncher) { pendingCapture = it }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            capturedPhotoUri == null -> EmptyCaptureCard(
                primaryText = "Tap to photograph",
                secondaryText = if (cameraTier is CameraTier.WeakCue) {
                    "No specific visual check suggested for this symptom combination"
                } else {
                    null
                },
                onClick = ::openCamera
            )

            else -> PhotoPreview(
                bitmap = bitmap,
                visualState = visualState,
                onRetake = ::openCamera,
                onRemovePhoto = onRemovePhoto,
                onConfirmFinding = onConfirmFinding,
                onDismissFinding = onDismissFinding
            )
        }
    }
}

private data class CapturedTempPhoto(
    val file: File,
    val outputUri: Uri
)

private fun launchCameraIntent(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onPending: (CapturedTempPhoto) -> Unit
) {
    val cacheDir = File(context.cacheDir, "camera").apply { mkdirs() }
    val photoFile = File(cacheDir, "capture_${UUID.randomUUID()}.jpg")
    val outputUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        photoFile
    )
    onPending(CapturedTempPhoto(photoFile, outputUri))

    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    launcher.launch(intent)
}

@Composable
private fun EmptyCaptureCard(
    primaryText: String,
    secondaryText: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp))
            .dashBorder(
                color = Color(0xFFD1D5DB),
                strokeWidth = 1.5.dp,
                cornerRadius = 12.dp,
                dashOn = 18f,
                dashOff = 12f
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = StepperTeal,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = primaryText,
                color = Color(0xFF6B7280),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            if (!secondaryText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = secondaryText,
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

private fun Modifier.dashBorder(
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    dashOn: Float,
    dashOff: Float
) = this.then(
    this.drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(dashOn, dashOff)
                ),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        )
    }
)

@Composable
private fun PhotoPreview(
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    visualState: VisualAssessmentState,
    onRetake: () -> Unit,
    onRemovePhoto: () -> Unit,
    onConfirmFinding: () -> Unit,
    onDismissFinding: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFD7E8F7)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Clinical photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = Color(0xFF5A9FD6),
                    modifier = Modifier.size(48.dp)
                )
            }

            IconButton(
                onClick = onRemovePhoto,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove photo",
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        when (visualState) {
            is VisualAssessmentState.QualityChecking -> FooterRow(
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = StepperTeal,
                        strokeWidth = 2.dp
                    )
                },
                text = "Checking photo quality...",
                actionLabel = "Retake",
                actionColor = Color(0xFF9CA3AF),
                onAction = onRetake
            )

            is VisualAssessmentState.QualityFailed -> FooterRow(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = visualState.reason,
                actionLabel = "Retake",
                actionColor = Color(0xFF9CA3AF),
                onAction = onRetake
            )

            is VisualAssessmentState.Analysing -> FooterRow(
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = StepperTeal,
                        strokeWidth = 2.dp
                    )
                },
                text = "Analysing photo...",
                actionLabel = "Retake",
                actionColor = Color(0xFF9CA3AF),
                onAction = onRetake
            )

            is VisualAssessmentState.FindingReady -> FindingReadyCard(
                finding = visualState.finding,
                onConfirm = onConfirmFinding,
                onDismiss = onDismissFinding
            )

            is VisualAssessmentState.LowConfidenceFinding -> LowConfidenceCard(
                finding = visualState.finding,
                onRetake = onRetake
            )

            is VisualAssessmentState.Confirmed -> ConfirmedFooter(
                finding = visualState.finding,
                onRetake = onRetake
            )

            is VisualAssessmentState.Dismissed -> NeutralFooter(
                text = "Finding dismissed",
                actionLabel = "Retake",
                actionColor = Color(0xFF9CA3AF),
                onAction = onRetake
            )

            is VisualAssessmentState.NoFinding -> NeutralFooter(
                text = "No visual finding detected",
                actionLabel = "Retake",
                actionColor = Color(0xFF9CA3AF),
                onAction = onRetake
            )

            is VisualAssessmentState.AnalysisError -> NeutralFooter(
                text = visualState.message.ifBlank { "Unable to analyse photo" },
                actionLabel = "Retake",
                actionColor = Color(0xFF9CA3AF),
                onAction = onRetake
            )

            else -> Unit
        }
    }
}

@Composable
private fun FindingReadyCard(
    finding: com.triagemate.chps.domain.model.VisualFinding,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StepperTeal,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Visual finding detected",
                color = StepperTeal,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = finding.findingText,
            color = Color(0xFF111111),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(6.dp))
        BadgeRow(finding)
        Spacer(Modifier.height(6.dp))
        ConfidenceBadge(finding.confidence)
        Spacer(Modifier.height(10.dp))
        Text(
            text = finding.clinicalImplication,
            color = Color(0xFF6B7280),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
            ) {
                Text("Dismiss")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StepperTeal)
            ) {
                Text("Confirm finding")
            }
        }
    }
}

@Composable
private fun LowConfidenceCard(
    finding: com.triagemate.chps.domain.model.VisualFinding,
    onRetake: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFFBEB))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Photo unclear - low confidence",
                color = Color(0xFF111111),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Retake",
                color = Color(0xFF9CA3AF),
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onRetake)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = finding.findingText,
            color = Color(0xFF6B7280),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "You may proceed with the assessment if you judge the photo useful enough.",
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun BadgeRow(finding: com.triagemate.chps.domain.model.VisualFinding) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Badge(
            text = finding.findingType.name.replace("_", " "),
            background = when (finding.findingType) {
                FindingType.ESCALATING -> Color(0xFFFFE4E6)
                FindingType.CONFIRMING -> Color(0xFFEAF9F7)
                FindingType.NEW_OBSERVATION -> Color(0xFFF3F4F6)
            },
            foreground = when (finding.findingType) {
                FindingType.ESCALATING -> Color(0xFFB91C1C)
                FindingType.CONFIRMING -> StepperTeal
                FindingType.NEW_OBSERVATION -> Color(0xFF6B7280)
            }
        )
    }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = foreground, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ConfidenceBadge(confidence: ConfidenceLevel) {
    Badge(
        text = when (confidence) {
            ConfidenceLevel.HIGH -> "HIGH confidence"
            ConfidenceLevel.MEDIUM -> "MEDIUM confidence"
            ConfidenceLevel.LOW -> "LOW confidence"
        },
        background = when (confidence) {
            ConfidenceLevel.HIGH -> Color(0xFFEAF9F7)
            ConfidenceLevel.MEDIUM -> Color(0xFFFFFBEB)
            ConfidenceLevel.LOW -> Color(0xFFF3F4F6)
        },
        foreground = when (confidence) {
            ConfidenceLevel.HIGH -> StepperTeal
            ConfidenceLevel.MEDIUM -> Color(0xFFD97706)
            ConfidenceLevel.LOW -> Color(0xFF6B7280)
        }
    )
}

@Composable
private fun ConfirmedFooter(
    finding: com.triagemate.chps.domain.model.VisualFinding,
    onRetake: () -> Unit
) {
    FooterRow(
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StepperTeal,
                modifier = Modifier.size(18.dp)
            )
        },
        text = "${finding.findingText} - confirmed by CHO",
        actionLabel = "Retake",
        actionColor = Color(0xFF9CA3AF),
        onAction = onRetake
    )
}

@Composable
private fun NeutralFooter(
    text: String,
    actionLabel: String,
    actionColor: Color,
    onAction: () -> Unit
) {
    FooterRow(
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StepperTeal,
                modifier = Modifier.size(18.dp)
            )
        },
        text = text,
        actionLabel = actionLabel,
        actionColor = actionColor,
        onAction = onAction
    )
}

@Composable
private fun FooterRow(
    icon: @Composable () -> Unit,
    text: String,
    actionLabel: String,
    actionColor: Color,
    onAction: () -> Unit,
    outerPadding: androidx.compose.ui.unit.Dp = 16.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = outerPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = if (text.contains("dismissed", true) || text.contains("unable", true) || text.contains("no visual finding", true)) Color(0xFF6B7280) else StepperTeal,
            fontSize = 13.sp
        )
        Text(
            text = actionLabel,
            color = actionColor,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onAction)
        )
    }
}
