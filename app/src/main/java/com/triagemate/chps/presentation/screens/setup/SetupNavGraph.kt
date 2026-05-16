package com.triagemate.chps.presentation.screens.setup

import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object SetupRoutes {
    const val Welcome = "setup_welcome"
    const val Compound = "setup_compound"
    const val Location = "setup_location"
    const val Pin = "setup_pin"
    const val Complete = "setup_complete"
}

fun NavGraphBuilder.setupNavGraph(
    navController: NavController,
    onSetupFinished: () -> Unit
) {
    composable(SetupRoutes.Welcome) {
        SetupWelcomeScreen(
            onContinue = { navController.navigate(SetupRoutes.Compound) }
        )
    }
    composable(SetupRoutes.Compound) { entry ->
        val welcomeEntry = remember(entry) { navController.getBackStackEntry(SetupRoutes.Welcome) }
        SetupCompoundNameScreen(
            onNext = { navController.navigate(SetupRoutes.Location) },
            onBack = { navController.popBackStack() },
            viewModel = hiltViewModel(welcomeEntry)
        )
    }
    composable(SetupRoutes.Location) { entry ->
        val welcomeEntry = remember(entry) { navController.getBackStackEntry(SetupRoutes.Welcome) }
        SetupLocationScreen(
            onNext = { navController.navigate(SetupRoutes.Pin) },
            onBack = { navController.popBackStack() },
            viewModel = hiltViewModel(welcomeEntry)
        )
    }
    composable(SetupRoutes.Pin) { entry ->
        val welcomeEntry = remember(entry) { navController.getBackStackEntry(SetupRoutes.Welcome) }
        SetupPinScreen(
            onBack = { navController.popBackStack() },
            onComplete = { navController.navigate(SetupRoutes.Complete) },
            viewModel = hiltViewModel(welcomeEntry)
        )
    }
    composable(SetupRoutes.Complete) {
        SetupCompleteScreen(onContinue = onSetupFinished)
    }
}
