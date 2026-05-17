package com.triagemate.chps.presentation.screens.supervisor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.triagemate.chps.presentation.theme.PrimaryNavy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorDashboardScreen(
    onNavigateBack: () -> Unit,
    onViewCaseLog: () -> Unit,
    viewModel: SupervisorViewModel = hiltViewModel()
) {
    val compoundProfile by viewModel.compoundProfile.collectAsState()
    val totalThisMonth by viewModel.totalThisMonth.collectAsState()
    val lastMonthTotal by viewModel.lastMonthTotal.collectAsState()
    val urgencyDistribution by viewModel.urgencyDistribution.collectAsState()
    val dailyCounts by viewModel.dailyCounts.collectAsState()
    val topSymptoms by viewModel.topSymptoms.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val averageDurationMillis by viewModel.averageDurationMillis.collectAsState()

    val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    val monthShort = monthLabel.uppercase(Locale.getDefault())
    val delta = totalThisMonth - lastMonthTotal
    val maxSymptomCount = topSymptoms.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Supervisor Dashboard",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = PrimaryNavy
                )
            )
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding), color = Color(0xFFF8F9FA)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    CompoundHeader(
                        name = compoundProfile?.compoundName?.ifBlank { "Compound not set" }
                            ?: "Compound not set",
                        district = compoundProfile?.district,
                        compoundId = compoundProfile?.compoundId?.takeLast(6)?.uppercase(Locale.getDefault()) ?: "------"
                    )
                }

                item {
                    Text(
                        text = monthShort,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = Color(0xFF9CA3AF),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp
                    )
                }

                item {
                    DashboardCard {
                        Column {
                            // Total + delta
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "$totalThisMonth Assessments",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                                Text(
                                    text = "${if (delta >= 0) "↑" else "↓"} ${abs(delta)} from\nlast month",
                                    color = if (delta >= 0) Color(0xFF0D9488) else Color(0xFFDC2626),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            // RED / AMBER / GREEN with vertical dividers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatColumn(
                                    "RED", urgencyDistribution.red,
                                    Color(0xFFDC2626), "Referred",
                                    Modifier.weight(1f)
                                )
                                VerticalDivider(
                                    modifier = Modifier.height(56.dp),
                                    color = Color(0xFFE5E7EB)
                                )
                                StatColumn(
                                    "AMBER", urgencyDistribution.amber,
                                    Color(0xFFF59E0B), "Monitor",
                                    Modifier.weight(1f)
                                )
                                VerticalDivider(
                                    modifier = Modifier.height(56.dp),
                                    color = Color(0xFFE5E7EB)
                                )
                                StatColumn(
                                    "GREEN", urgencyDistribution.green,
                                    Color(0xFF16A34A), "Local mgmt",
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFE5E7EB))
                            Spacer(Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                MetricColumn(
                                    "Referral rate",
                                    "${urgencyDistribution.referralRate.toInt()}%",
                                    Modifier.weight(1f)
                                )
                                MetricColumn(
                                    "Avg assessment",
                                    formatDuration(averageDurationMillis),
                                    Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "ACTIVITY \u2014 LAST 30 DAYS") {
                        ActivityChart(dailyCounts)
                    }
                }

                item {
                    SectionCard(title = "TOP PRESENTING SYMPTOMS") {
                        Column {
                            topSymptoms.forEachIndexed { index, symptom ->
                                SymptomRow(
                                    symptom = symptom.symptom,
                                    count = symptom.count,
                                    maxCount = maxSymptomCount,
                                    isAutoRed = symptom.isAutoRed,
                                    isLast = index == topSymptoms.lastIndex
                                )
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "DATA SYNC") {
                        Column {
                            SyncRow(
                                icon = Icons.Outlined.Wifi,
                                label = "Last synced",
                                value = formatLastSync(lastSyncTime)
                            )
                            HorizontalDivider(color = Color(0xFFE5E7EB))
                            SyncRow(
                                icon = Icons.Default.Schedule,
                                label = "Pending cases",
                                value = if (pendingCount == 0) "0 pending"
                                        else if (pendingCount == 1) "1 pending"
                                        else "$pendingCount pending",
                                valueColor = if (pendingCount > 0) Color(0xFFF59E0B) else Color(0xFF16A34A)
                            )
                            HorizontalDivider(color = Color(0xFFE5E7EB))
                            SyncNowRow(
                                isSyncing = syncState is SupervisorViewModel.SyncState.Syncing,
                                onClick = viewModel::triggerSync
                            )
                            if (syncState is SupervisorViewModel.SyncState.Error) {
                                Text(
                                    text = (syncState as SupervisorViewModel.SyncState.Error).message,
                                    color = Color(0xFFDC2626),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onViewCaseLog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                    ) {
                        Text("View Case Log", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun CompoundHeader(name: String, district: String?, compoundId: String) {
    val displayName = if (district != null) "$name · $district" else name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrimaryNavy)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.HomeWork,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = displayName,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(PrimaryNavy, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "ID: $compoundId",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DashboardCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(10.dp))
        DashboardCard(content)
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: Int,
    color: Color,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value.toString(), color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = subtitle, color = Color(0xFF9CA3AF), fontSize = 12.sp)
    }
}

@Composable
private fun MetricColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, color = Color(0xFF111827), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color(0xFF6B7280), fontSize = 13.sp)
    }
}

@Composable
private fun ActivityChart(dailyCounts: List<SupervisorViewModel.DailyCount>) {
    val max = (dailyCounts.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("Max: $max/day", color = Color(0xFF9CA3AF), fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        // Bar chart
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            dailyCounts.forEach { day ->
                val barHeight = if (day.total == 0) 4.dp
                                else (8 + (day.total.toFloat() / max * 80)).dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .background(
                            if (day.hasRedCase) Color(0xFFDC2626) else PrimaryNavy,
                            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                        )
                )
            }
        }
        // Day labels: 1, 10, 20, 30
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("1", color = Color(0xFF9CA3AF), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("10", color = Color(0xFF9CA3AF), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("20", color = Color(0xFF9CA3AF), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("30", color = Color(0xFF9CA3AF), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SymptomRow(
    symptom: String,
    count: Int,
    maxCount: Int,
    isAutoRed: Boolean,
    isLast: Boolean
) {
    val textColor = if (isAutoRed) Color(0xFFDC2626) else Color(0xFF111827)
    val barColor  = if (isAutoRed) Color(0xFFDC2626) else PrimaryNavy
    val maxBarWidth = 120.dp
    val barWidth = (count.toFloat() / maxCount * maxBarWidth.value).coerceAtLeast(8f).dp

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = symptom,
                modifier = Modifier.weight(1f),
                color = textColor,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(10.dp)
                    .background(barColor, RoundedCornerShape(20.dp))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$count cases",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isAutoRed) FontWeight.Medium else FontWeight.Normal
            )
        }
        if (!isLast) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SyncRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color(0xFF16A34A)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = label, modifier = Modifier.weight(1f), color = Color(0xFF111827), fontSize = 15.sp)
        Text(text = value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SyncNowRow(isSyncing: Boolean, onClick: () -> Unit) {
    val tint = if (isSyncing) Color(0xFF9CA3AF) else Color(0xFF374151)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSyncing, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Sync, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isSyncing) "Syncing..." else "Sync Now",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = tint
        )
    }
}

private fun formatDuration(durationMillis: Long): String {
    if (durationMillis <= 0L) return "--"
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds}s"
}

private fun formatLastSync(timestamp: Long?): String {
    if (timestamp == null) return "Never synced"
    val syncCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCal = Calendar.getInstance()
    val isToday = syncCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                  syncCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    val timePart = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    return if (isToday) "Today, $timePart"
           else SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
