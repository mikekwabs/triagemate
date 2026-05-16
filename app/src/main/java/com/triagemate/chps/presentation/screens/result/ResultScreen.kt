package com.triagemate.chps.presentation.screens.result

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.triagemate.chps.domain.model.ConfidenceLevel
import com.triagemate.chps.domain.model.ToolCallRecord
import com.triagemate.chps.domain.model.VisualFinding
import com.triagemate.chps.domain.safety.SafetyOverrideResult
import com.triagemate.chps.presentation.components.ConfidenceChip
import com.triagemate.chps.presentation.components.LearnMoreCard
import com.triagemate.chps.presentation.components.SafetyOverrideCard
import com.triagemate.chps.presentation.theme.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class UrgencyMeta(
    val bannerColor: Color, val icon: ImageVector,
    val title: String, val subtitle: String
)

private data class VitalDisplayMeta(val label: String, val unit: String)

private fun vitalDisplayMeta(key: String): VitalDisplayMeta = when (key.lowercase().replace(" ", "_")) {
    "temperature"       -> VitalDisplayMeta("Temperature", "°C")
    "respiratory_rate"  -> VitalDisplayMeta("Resp Rate",   "/min")
    "pulse"             -> VitalDisplayMeta("Pulse Rate",  "bpm")
    "blood_pressure"    -> VitalDisplayMeta("Blood Press.", "mmHg")
    "oxygen_saturation" -> VitalDisplayMeta("SpO₂",       "%")
    else                -> VitalDisplayMeta(key.replaceFirstChar { it.uppercase() }, "")
}

@Composable
private fun VitalTile(vitalKey: String, value: String, modifier: Modifier = Modifier) {
    val meta = vitalDisplayMeta(vitalKey)
    val displayValue = if (meta.unit.isNotEmpty()) "$value${meta.unit}" else value
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(UrgencyRed.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayValue,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = UrgencyRed
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = UrgencyRed,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta.label,
                fontSize = 12.sp,
                color = Color(0xFF7F8C8D)
            )
        }
    }
}

private fun urgencyMeta(u: String) = when (u.uppercase()) {
    "RED"   -> UrgencyMeta(BannerRed,   Icons.Outlined.CrisisAlert,  "URGENT — Refer Immediately",  "Within 4 hours")
    "AMBER" -> UrgencyMeta(BannerAmber, Icons.Outlined.Warning,       "CAUTION — Monitor Closely",   "Review within 24 hours")
    "GREEN" -> UrgencyMeta(BannerGreen, Icons.Outlined.CheckCircle,   "ROUTINE — Continue Care",     "Follow-up as scheduled")
    else    -> UrgencyMeta(Color.Gray,  Icons.Outlined.Info,          u.uppercase(),                 "Use clinical judgement")
}

private fun stepSummary(record: ToolCallRecord): String = when (record.toolName.lowercase()) {
    "assesssymptoms" -> {
        val parsed = runCatching { record.result?.let { JSONObject(it) } }.getOrNull()
        val dangerSigns = parsed?.optJSONArray("danger_signs")
            ?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
            .orEmpty()
        val pathway = parsed?.optString("pathway", "").orEmpty()
        val preliminarySeverity = parsed?.optString("preliminary_severity", "") ?: ""
        when {
            isAutoRedDanger(pathway, dangerSigns) -> "auto-RED danger sign detected"
            dangerSigns.isNotEmpty() && preliminarySeverity.equals("HIGH", ignoreCase = true) -> "${dangerSigns.size} danger sign(s) found"
            dangerSigns.isNotEmpty() -> "${dangerSigns.size} danger sign(s) found"
            else -> {
                val symptomCount = record.arguments.split("|")
                    .getOrNull(1)?.split(",")?.count { it.isNotBlank() } ?: 0
                if (symptomCount > 0) "$symptomCount symptom(s) assessed" else "Symptoms assessed"
            }
        }
    }
    "analysephoto", "analyse_photo" -> {
        record.result?.takeIf { it.isNotBlank() }
            ?: record.arguments.split("|").firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "Photo analysed"
    }
    "requestvitalsigns" -> {
        val count = record.arguments.split(",").count { it.isNotBlank() }
        "$count vital sign(s) requested"
    }
    "requestvitalsigns_skipped" -> "Vital signs skipped — immediate referral required"
    "checkdruginteraction_skipped" -> "Drug interaction check skipped — immediate referral required"
    "checkdruginteraction" -> "Drug interactions checked"
    "classifytriage" -> {
        val urgency = record.arguments.split("|").firstOrNull()?.uppercase() ?: ""
        if (urgency.isNotBlank()) "Classified as $urgency" else "Triage classified"
    }
    "generatereferralnote" -> "Referral note generated"
    else -> record.toolName
}

private fun normalizedLabel(value: String): String = value.trim().lowercase()

private fun isAutoRedDanger(pathway: String, dangerSigns: List<String>): Boolean {
    val autoRedSigns = when (pathway.uppercase()) {
        "ANTENATAL" -> setOf(
            "vaginal bleeding",
            "heavy vaginal bleeding",
            "convulsions / fits",
            "fits or convulsions",
            "absent fetal movement",
            "fetal movements stopped",
            "prolonged labour (>24h)",
            "cord prolapse"
        )
        else -> setOf(
            "unable to drink or breastfeed",
            "vomiting everything",
            "convulsions",
            "lethargic or unconscious",
            "stridor",
            "stridor at rest",
            "severe chest indrawing"
        )
    }
    return dangerSigns.any { normalizedLabel(it) in autoRedSigns }
}

private fun clinicalConcernsFor(result: com.triagemate.chps.domain.model.TriageResult): List<String> {
    val dangerSet = result.dangerSignsDetected.map(::normalizedLabel).toSet()
    return result.reportedSymptoms
        .filter { normalizedLabel(it) !in dangerSet }
        .distinct()
}

private fun visualReviewSummary(result: com.triagemate.chps.domain.model.TriageResult): String {
    return when {
        result.confirmedVisualFinding != null -> result.confirmedVisualFinding.findingText
        result.photoUri != null -> "No abnormal visual sign was confirmed from the clinical photo review."
        else -> "No clinical photo was reviewed."
    }
}

@Composable
private fun AssessmentStepsCard(
    toolCallLog: List<ToolCallRecord>,
    safetyOverride: SafetyOverrideResult? = null
) {
    if (toolCallLog.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        RoundedCornerShape(16.dp),
        CardDefaults.cardColors(Color.White),
        CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.PlaylistAddCheck, null, tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Assessment Steps (${toolCallLog.size} rounds)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF2C3E50),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    null, tint = Color(0xFF7F8C8D)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                toolCallLog.forEach { record ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(StepperTeal))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Round ${record.round}: ${record.toolName} \u2192 ${stepSummary(record)}",
                            fontSize = 13.sp,
                            color = Color(0xFF34495E),
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Safety guardrail audit row
                if (safetyOverride != null) {
                    Spacer(Modifier.height(4.dp))
                    if (safetyOverride.wasOverridden) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Safety override applied",
                                fontSize = 13.sp,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Gemma: ${safetyOverride.originalGemmaUrgency} \u2192 Override: RED — ${safetyOverride.overrideReason}",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280),
                                lineHeight = 17.sp
                            )
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, null, tint = StepperTeal, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Safety check passed — no override required",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClinicalConcernsCard(concerns: List<String>) {
    if (concerns.isEmpty()) return

    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(16.dp),
        CardDefaults.cardColors(Color.White),
        CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clinical Concerns", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2C3E50))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "These reported symptoms are clinically important, even if they do not meet the strict danger-sign threshold on their own.",
                color = Color(0xFF6B7280),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                concerns.forEach { concern ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFF4E5))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = concern,
                            color = Color(0xFFB45309),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualAssessmentCard(
    photoUriString: String?,
    finding: VisualFinding?
) {
    val bitmap = remember(photoUriString) {
        photoUriString
            ?.removePrefix("file://")
            ?.let(BitmapFactory::decodeFile)
            ?.asImageBitmap()
    }

    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(16.dp),
        CardDefaults.cardColors(Color.White),
        CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CameraAlt, null, tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Visual Assessment", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2C3E50))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFDDEEDF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Clinical photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x1A0D9488)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.CameraAlt, null, tint = StepperTeal, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = finding?.findingText ?: "Clinical photo reviewed",
                            color = Color(0xFF111111),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp
                        )
                        if (finding != null) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (finding.findingType) {
                                            com.triagemate.chps.domain.model.FindingType.ESCALATING -> Color(0xFFFFE4E6)
                                            com.triagemate.chps.domain.model.FindingType.CONFIRMING -> Color(0xFFEAF9F7)
                                            com.triagemate.chps.domain.model.FindingType.NEW_OBSERVATION -> Color(0xFFF3F4F6)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = finding.findingType.name.replace("_", " "),
                                    color = when (finding.findingType) {
                                        com.triagemate.chps.domain.model.FindingType.ESCALATING -> Color(0xFFB91C1C)
                                        com.triagemate.chps.domain.model.FindingType.CONFIRMING -> StepperTeal
                                        com.triagemate.chps.domain.model.FindingType.NEW_OBSERVATION -> Color(0xFF6B7280)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = finding?.clinicalImplication
                                ?: "No abnormal visual sign was confirmed. The visual review did not change triage on its own.",
                            color = Color(0xFF6B7280),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    if (finding != null) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEAF9F7))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Confirmed by CHO",
                                color = StepperTeal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Visual assessment is advisory only. Clinical judgement takes priority.",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(onNewAssessment: () -> Unit, viewModel: ResultViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val safetyOverride by viewModel.safetyOverride.collectAsState()
    val learnMoreState by viewModel.learnMoreState.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val context = LocalContext.current

    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PrimaryNavy)
            }
        }

        uiState.result != null -> {
            val r = uiState.result!!
            val m = urgencyMeta(r.urgency)
            val clinicalConcerns = clinicalConcernsFor(r)
            val df = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

            Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {

                // ── Urgency banner ─────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxWidth().background(m.bannerColor).statusBarsPadding()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(m.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                m.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            ConfidenceChip(confidence = confidence, wasOverridden = safetyOverride?.wasOverridden == true)
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .clickable { viewModel.toggleSpeak() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                                    contentDescription = if (isSpeaking) "Stop reading aloud" else "Read aloud",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(m.subtitle, color = Color.White.copy(0.85f), fontSize = 14.sp,
                            modifier = Modifier.padding(start = 40.dp))
                    }
                }

                // ── Scrollable cards ───────────────────────────────
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // Safety Override Card (shown only when guardrail triggered)
                    safetyOverride?.takeIf { it.wasOverridden }?.let { override ->
                        SafetyOverrideCard(override = override)
                    }

                    // Recommended Action
                    Card(
                        Modifier.fillMaxWidth(),
                        RoundedCornerShape(16.dp),
                        CardDefaults.cardColors(Color.White),
                        CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Description, null, tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Recommended Action", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2C3E50))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = r.action,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF34495E),
                                lineHeight = 22.sp
                            )
                            r.confirmedVisualFinding?.let { finding ->
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFEAF9F7))
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(StepperTeal)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = finding.referralNoteText,
                                        color = StepperTeal,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                            if (r.confirmedVisualFinding == null && r.photoUri != null) {
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFF4F7F8))
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Outlined.CameraAlt, null, tint = Color(0xFF6B7280), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "${visualReviewSummary(r)} The recommendation remained symptom-led.",
                                        color = Color(0xFF6B7280),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )
                                }
                            }

                            // Clinical disclaimer
                            if (confidence == ConfidenceLevel.LOW) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFFFBEB))
                                        .then(Modifier.padding(10.dp))
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Default.Shield, null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Low confidence result. Use your clinical judgement and consider seeking supervisor input before referring.",
                                            color = Color(0xFF92400E),
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            } else {
                                HorizontalDivider(
                                    color = Color(0xFFF3F4F6),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shield, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "TriageMate supports clinical decision-making — your judgement always takes priority.",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Danger Signs — uses FlowRow so chips wrap naturally, no fixed rows
                    Card(
                        Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                        CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Warning, null, tint = UrgencyAmber, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Danger Signs Detected", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2C3E50))
                            }
                            Spacer(Modifier.height(12.dp))
                            if (r.dangerSignsDetected.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    r.dangerSignsDetected.forEach { sign ->
                                        Box(
                                            Modifier.clip(RoundedCornerShape(20.dp))
                                                .background(UrgencyRed.copy(alpha = 0.1f))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                                        .background(UrgencyRed)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(sign, color = UrgencyRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "No guideline-defined danger signs were confirmed from the assessment data.",
                                    color = Color(0xFF6B7280),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    ClinicalConcernsCard(clinicalConcerns)

                    if (r.photoUri != null) {
                        VisualAssessmentCard(
                            photoUriString = r.photoUri.toString(),
                            finding = r.confirmedVisualFinding
                        )
                    }

                    // Vital Signs (only shown if any were collected)
                    if (r.vitalSigns.isNotEmpty()) {
                        Card(
                            Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                            CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.MonitorHeart, null, tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Vital Signs", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2C3E50))
                                }
                                Spacer(Modifier.height(12.dp))
                                // Tile grid — two per row
                                val vitalEntries = r.vitalSigns.entries.toList()
                                vitalEntries.chunked(2).forEach { rowEntries ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowEntries.forEach { (key, value) ->
                                            VitalTile(
                                                vitalKey = key,
                                                value = value,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        // Pad odd row
                                        if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                    }

                    // Referral Note (expandable)
                    var expanded by remember { mutableStateOf(true) }
                    Card(
                        Modifier.fillMaxWidth().animateContentSize(), RoundedCornerShape(16.dp),
                        CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Description, null, tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Referral Note", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = Color(0xFF2C3E50), modifier = Modifier.weight(1f))
                                Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                    null, tint = Color(0xFF7F8C8D))
                            }
                            if (expanded) {
                                Spacer(Modifier.height(12.dp))
                                Text(r.referralNote, style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF34495E), lineHeight = 20.sp)
                                Spacer(Modifier.height(12.dp))
                                Text("Generated by TriageMate | ${df.format(Date())}",
                                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF95A5A6))
                            }
                        }
                    }

                    // Assessment Steps (audit trail)
                    AssessmentStepsCard(r.toolCallLog, safetyOverride)

                    // Learn about this case — Gemma educational explanation, on-demand
                    LearnMoreCard(
                        state = learnMoreState,
                        onExpand = { viewModel.loadClinicalExplanation() },
                        onRetry = { viewModel.retryClinicalExplanation() }
                    )
                }

                // ── Bottom buttons ─────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F9FA))
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular share icon button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, r.referralNote)
                                }
                                context.startActivity(Intent.createChooser(i, "Share referral note"))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share referral note", tint = PrimaryNavy, modifier = Modifier.size(22.dp))
                    }

                    // Save & New — navy filled pill
                    Button(
                        onClick = onNewAssessment,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save & New", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        else -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No result data found.", textAlign = TextAlign.Center, color = Color(0xFF7F8C8D))
            }
        }
    }
}
