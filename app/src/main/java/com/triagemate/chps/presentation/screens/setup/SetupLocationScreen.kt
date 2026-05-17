package com.triagemate.chps.presentation.screens.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.triagemate.chps.presentation.theme.PrimaryNavy
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.triagemate.chps.presentation.theme.StepperTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupLocationScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val region by viewModel.region.collectAsState()
    val district by viewModel.district.collectAsState()
    val canProceed by viewModel.canProceedFromLocation.collectAsState()
    var regionExpanded by remember { mutableStateOf(false) }
    var districtExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compound Location", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1F2937)
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = Color(0xFFF8F9FA)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Where is this compound located?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Choose the region first, then select the district used for dashboard reporting and sync.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(Modifier.height(24.dp))

                    ExposedDropdownMenuBox(
                        expanded = regionExpanded,
                        onExpandedChange = { regionExpanded = !regionExpanded }
                    ) {
                        OutlinedTextField(
                            value = region,
                            onValueChange = {},
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            readOnly = true,
                            label = { Text("Region") },
                            placeholder = { Text("Select region") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = StepperTeal,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = regionExpanded,
                            onDismissRequest = { regionExpanded = false }
                        ) {
                            viewModel.regions.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        viewModel.updateRegion(item)
                                        regionExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    ExposedDropdownMenuBox(
                        expanded = districtExpanded,
                        onExpandedChange = {
                            if (region.isNotBlank()) districtExpanded = !districtExpanded
                        }
                    ) {
                        OutlinedTextField(
                            value = district,
                            onValueChange = {},
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .clickable(enabled = region.isNotBlank()) { districtExpanded = true },
                            readOnly = true,
                            enabled = region.isNotBlank(),
                            label = { Text("District") },
                            placeholder = { Text(if (region.isBlank()) "Choose region first" else "Select district") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = StepperTeal,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = districtExpanded,
                            onDismissRequest = { districtExpanded = false }
                        ) {
                            viewModel.districtsByRegion[region].orEmpty().forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        viewModel.updateDistrict(item)
                                        districtExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onNext,
                    enabled = canProceed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryNavy,
                        disabledContainerColor = Color(0xFFD1D5DB)
                    )
                ) {
                    Text("Continue")
                }
            }
        }
    }
}
