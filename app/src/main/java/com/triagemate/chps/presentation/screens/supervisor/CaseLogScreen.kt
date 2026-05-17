package com.triagemate.chps.presentation.screens.supervisor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.triagemate.chps.presentation.theme.PrimaryNavy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.triagemate.chps.data.local.model.AssessmentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: SupervisorViewModel = hiltViewModel()
) {
    val assessments by viewModel.filteredAssessments.collectAsState()
    val totalCount by viewModel.totalAssessmentsCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterUrgency by viewModel.filterUrgency.collectAsState()
    val thisMonthOnly by viewModel.showThisMonthOnly.collectAsState()

    var searchVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Case Log",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) viewModel.setSearchQuery("")
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = PrimaryNavy
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = Color(0xFFF8F9FA)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Search bar — animated show/hide
                AnimatedVisibility(
                    visible = searchVisible,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        placeholder = {
                            Text("Search symptoms, urgency, age", color = Color(0xFF9CA3AF))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF9CA3AF))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryNavy,
                            unfocusedBorderColor = Color(0xFFE5E7EB),
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        )
                    )
                }

                // Filter chips — horizontally scrollable so "This month" never overflows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip("All", selected = filterUrgency == null && !thisMonthOnly) {
                        viewModel.setUrgencyFilter(null)
                        viewModel.setThisMonthOnly(false)
                    }
                    FilterChip("RED", selected = filterUrgency == "RED") { viewModel.setUrgencyFilter("RED") }
                    FilterChip("AMBER", selected = filterUrgency == "AMBER") { viewModel.setUrgencyFilter("AMBER") }
                    FilterChip("GREEN", selected = filterUrgency == "GREEN") { viewModel.setUrgencyFilter("GREEN") }
                    FilterChip("This month", selected = thisMonthOnly) { viewModel.setThisMonthOnly(!thisMonthOnly) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(assessments, key = { it.id }) { assessment ->
                        CaseLogCard(assessment)
                    }
                    item {
                        val filtered = assessments.size
                        val footerText = if (filtered < totalCount)
                            "Showing $filtered of $totalCount \u2014 scroll for more"
                        else
                            "Showing $filtered case${if (filtered == 1) "" else "s"}"
                        Text(
                            text = footerText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = TextAlign.Center,
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) PrimaryNavy else Color.White,
            labelColor = if (selected) Color.White else Color(0xFF374151)
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = if (selected) Color.Transparent else Color(0xFFD1D5DB),
            borderWidth = 1.dp
        )
    )
}

@Composable
private fun CaseLogCard(entity: AssessmentEntity) {
    val urgency = entity.urgency.uppercase(Locale.getDefault())
    val accent = when (urgency) {
        "RED"   -> Color(0xFFDC2626)
        "AMBER" -> Color(0xFFF59E0B)
        else    -> Color(0xFF16A34A)
    }
    val chipBg = when (urgency) {
        "RED"   -> Color(0xFFFEE2E2)
        "AMBER" -> Color(0xFFFEF3C7)
        else    -> Color(0xFFDCFCE7)
    }

    // Shorten "Antenatal Care" → "Antenatal" for compact card title
    val pathwayShort = entity.pathway
        .replace("Antenatal Care", "Antenatal")
        .replace("Child Under 5", "Child Under 5")

    val title = buildString {
        append(pathwayShort)
        if (entity.patientSex.isNotBlank()) {
            append(" \u2014 ")
            append(entity.patientSex.replaceFirstChar { it.uppercase() })
        }
        if (entity.patientAge.isNotBlank()) {
            val unit = if (entity.pathway.contains("Child", ignoreCase = true)) "months" else "weeks"
            append(", ${entity.patientAge} $unit")
        }
    }

    val dateText = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        .format(Date(entity.timestamp))

    val symptomsPreview = runCatching {
        kotlinx.serialization.json.Json.decodeFromString<List<String>>(entity.symptomsJson)
    }.getOrDefault(emptyList()).take(2).joinToString(" · ")

    val actionText = when (urgency) {
        "RED"   -> "Immediate Referral"
        "AMBER" -> "Monitor next 48h"
        else    -> "Managed locally"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        // IntrinsicSize.Min makes the colored bar stretch to match content height
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateText,
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = symptomsPreview.ifBlank { entity.presentingComplaint },
                    color = Color(0xFF6B7280),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(chipBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = urgency,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(text = actionText, color = accent, fontSize = 14.sp)
                }
            }
        }
    }
}
