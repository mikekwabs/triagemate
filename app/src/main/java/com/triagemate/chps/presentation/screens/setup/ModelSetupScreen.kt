package com.triagemate.chps.presentation.screens.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.triagemate.chps.domain.model.DownloadState
import com.triagemate.chps.presentation.theme.PrimaryBlue
import com.triagemate.chps.presentation.theme.UrgencyAmber
import com.triagemate.chps.presentation.theme.UrgencyGreen

// ──────────────────────────────────────────────────────────────────────────────
// Root screen composable
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun ModelSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: ModelSetupViewModel = hiltViewModel()
) {
    val state by viewModel.downloadState.collectAsState()

    val backgroundColor = when (state) {
        is DownloadState.Completed, is DownloadState.Failed -> Color(0xFFF5F5F5)
        else -> Color.White
    }

    Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "setup_state"
            ) { currentState ->
                when (currentState) {
                    is DownloadState.Idle ->
                        IdleContent(onDownload = { viewModel.startDownload() })
                    is DownloadState.Enqueued ->
                        DownloadingContent(
                            downloadedBytes = 0L,
                            totalBytes = -1L,
                            progress = 0f,
                            isPreparing = true,
                            onCancel = { viewModel.cancelDownload() }
                        )
                    is DownloadState.Downloading ->
                        DownloadingContent(
                            downloadedBytes = currentState.downloadedBytes,
                            totalBytes = currentState.totalBytes,
                            progress = currentState.progress,
                            isPreparing = currentState.totalBytes <= 0L,
                            onCancel = { viewModel.cancelDownload() }
                        )
                    is DownloadState.Completed ->
                        CompletedContent(
                            onContinue = { viewModel.onDownloadComplete(onSetupComplete) }
                        )
                    is DownloadState.Failed ->
                        FailedContent(
                            reason = currentState.reason,
                            onRetry = { viewModel.startDownload() },
                            onCancel = { viewModel.cancelDownload() }
                        )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Idle / Enqueued state
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(onDownload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1.2f))

        // App logo row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(PrimaryBlue, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MonitorHeart,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "TriageMate",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Text(
                    text = "Clinical AI for CHPS Compounds",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // Info card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F4FD)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "One-Time Setup Required",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "TriageMate uses an on-device AI model to assess patients without internet. " +
                            "You need to download the model once (2.58 GB). This may take several minutes on mobile data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF374151)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // WiFi + mobile note
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF9CA3AF)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Download works on WiFi and mobile data",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF)
            )
        }

        Spacer(Modifier.weight(1f))

        // Download button
        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text(
                "Download AI Model",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Downloading state
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun DownloadingContent(
    downloadedBytes: Long,
    totalBytes: Long,
    progress: Float,
    isPreparing: Boolean,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // Compact logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(PrimaryBlue, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MonitorHeart,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("TriageMate", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 20.sp)
                Text("Clinical AI for CHPS Compounds", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
            }
        }

        Spacer(Modifier.height(32.dp))

        // Progress card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (isPreparing) "Preparing Download…" else "Downloading AI Model",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryBlue
                )
                Spacer(Modifier.height(16.dp))

                if (isPreparing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryBlue,
                        trackColor = Color(0xFFE5E7EB)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryBlue,
                        trackColor = Color(0xFFE5E7EB)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isPreparing) {
                        "Connecting to server…"
                    } else {
                        "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}  —  ${(progress * 100).toInt()}%"
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Please keep the app open. The download continues in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Notification hint
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF9CA3AF)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "A notification will appear when download is complete",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF)
            )
        }

        Spacer(Modifier.weight(1f))

        // Cancel button
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
        ) {
            Text("Cancel", fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Completed state
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompletedContent(onContinue: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // Success icon with outer ring
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFFE8F5E9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(UrgencyGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Model Ready",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = UrgencyGreen
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Your AI model is installed and ready. Complete setup to register your compound and begin assessing patients.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF374151),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UrgencyGreen)
            ) {
                Text(
                    "Set Up Compound Profile",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Failed state
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun FailedContent(reason: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // Warning icon with outer ring
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFFFFF3E0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(UrgencyAmber, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Download Failed",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Retry Download", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Helper
// ──────────────────────────────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes > 0 -> "${bytes / 1024} KB"
    else -> "—"
}
