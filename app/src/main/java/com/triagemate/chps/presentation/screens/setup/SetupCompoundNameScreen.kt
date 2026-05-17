package com.triagemate.chps.presentation.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.triagemate.chps.presentation.theme.PrimaryNavy
import com.triagemate.chps.presentation.theme.StepperTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupCompoundNameScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val compoundName by viewModel.compoundName.collectAsState()
    val canProceed by viewModel.canProceedFromName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compound Profile", fontWeight = FontWeight.SemiBold) },
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
                Column {
                    Text(
                        text = "What is the name of this compound?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "This name appears in the supervisor dashboard and sync exports.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(Modifier.height(28.dp))
                    OutlinedTextField(
                        value = compoundName,
                        onValueChange = viewModel::updateCompoundName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Compound name") },
                        placeholder = { Text("e.g. Aboabo CHPS Compound") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StepperTeal,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
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
