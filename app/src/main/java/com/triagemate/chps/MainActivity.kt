package com.triagemate.chps

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.triagemate.chps.data.local.prefs.CompoundPreferences
import com.triagemate.chps.data.sync.SyncEngine
import com.triagemate.chps.presentation.navigation.AppNavGraph
import com.triagemate.chps.presentation.theme.TriageMateTheme
import com.triagemate.chps.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var compoundPreferences: CompoundPreferences

    @Inject
    lateinit var syncEngine: SyncEngine

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission granted or denied — no action needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        syncEngine.scheduleSyncOnConnectivity()
        val modelFile = File(getExternalFilesDir(null), Constants.MODEL_FILENAME)
        val startDestination = when {
            !modelFile.exists() -> "model_setup"
            !compoundPreferences.isSetupComplete() -> "setup_welcome"
            else -> "home"
        }
        setContent {
            TriageMateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
