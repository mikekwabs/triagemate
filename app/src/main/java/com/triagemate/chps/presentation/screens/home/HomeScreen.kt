package com.triagemate.chps.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.presentation.theme.PrimaryBlue

private val ScreenBackground = Color(0xFFF2F4F7)
private val CardWhite = Color.White
private val StatCardBg = Color(0xFFEEF0F4)
private val NavyText = PrimaryBlue
private val SubtleGrey = Color(0xFF6B7280)
private val BannerBg = Color(0xFFDBEAFA)
private val PathwayCircleBg = Color(0xFFDCEBFA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPathwaySelected: (Pathway) -> Unit,
    onViewHistory: () -> Unit,
    onSupervisorAccess: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val pendingCount by viewModel.pendingCasesCount.collectAsState()
    val lastSyncText by viewModel.lastSyncText.collectAsState()
    val homeSubtitle by viewModel.homeSubtitle.collectAsState()

    Scaffold(
        topBar = { HomeTopBar(onSupervisorAccess) },
        bottomBar = {
            HomeBottomNav(
                onHistoryClick = onViewHistory
            )
        },
        containerColor = ScreenBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OfflineStatusBanner(homeSubtitle)

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Select Assessment",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(Modifier.height(16.dp))

            AssessmentPathwayCard(
                icon = Icons.Default.SentimentSatisfied,
                label = "Child Under 5",
                onClick = { onPathwaySelected(Pathway.CHILD_U5) }
            )

            Spacer(Modifier.height(12.dp))

            AssessmentPathwayCard(
                icon = Icons.Default.PregnantWoman,
                label = "Antenatal",
                onClick = { onPathwaySelected(Pathway.ANTENATAL) }
            )

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onViewHistory),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View Assessment History",
                    fontWeight = FontWeight.Bold,
                    color = NavyText,
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = NavyText,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.Sync,
                    label = "LAST SYNC",
                    value = lastSyncText,
                    modifier = Modifier.weight(1f),
                    onLongPress = onSupervisorAccess
                )
                StatCard(
                    icon = Icons.Default.Archive,
                    label = "PENDING CASES",
                    value = "$pendingCount ${if (pendingCount == 1) "Case" else "Cases"}",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(onSupervisorAccess: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(PrimaryBlue, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MonitorHeart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "TriageMate",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = NavyText
                )
            }
        },
        actions = {
            IconButton(onClick = onSupervisorAccess) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Supervisor access",
                    tint = NavyText,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
    )
}

@Composable
private fun OfflineStatusBanner(statusText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BannerBg, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(48.dp)
                .background(NavyText, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = NavyText,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = statusText,
            fontWeight = FontWeight.SemiBold,
            color = NavyText,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 14.dp)
        )
    }
}

@Composable
private fun AssessmentPathwayCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(PathwayCircleBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NavyText,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = NavyText
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    Card(
        modifier = if (onLongPress != null) {
            modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
        } else {
            modifier
        },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StatCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SubtleGrey,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = SubtleGrey,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
        }
    }
}

@Composable
private fun HomeBottomNav(
    onHistoryClick: () -> Unit
) {
    NavigationBar(
        containerColor = CardWhite,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("HOME", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = NavyText,
                indicatorColor = NavyText,
                unselectedIconColor = SubtleGrey,
                unselectedTextColor = SubtleGrey
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = onHistoryClick,
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = NavyText,
                indicatorColor = NavyText,
                unselectedIconColor = SubtleGrey,
                unselectedTextColor = SubtleGrey
            )
        )
    }
}
