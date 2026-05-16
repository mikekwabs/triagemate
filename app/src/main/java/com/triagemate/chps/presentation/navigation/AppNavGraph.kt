package com.triagemate.chps.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.presentation.screens.assessment.AssessmentScreen
import com.triagemate.chps.presentation.screens.history.HistoryScreen
import com.triagemate.chps.presentation.screens.home.HomeScreen
import com.triagemate.chps.presentation.screens.result.ResultScreen
import com.triagemate.chps.presentation.screens.setup.ModelSetupScreen
import com.triagemate.chps.presentation.screens.setup.SetupRoutes
import com.triagemate.chps.presentation.screens.setup.setupNavGraph
import com.triagemate.chps.presentation.screens.supervisor.CaseLogScreen
import com.triagemate.chps.presentation.screens.supervisor.PinEntryScreen
import com.triagemate.chps.presentation.screens.supervisor.SupervisorDashboardScreen

sealed class Screen(val route: String) {
    data object ModelSetup : Screen("model_setup")
    data object Home : Screen("home")
    data object PinEntry : Screen("pin_entry")
    data object SupervisorDashboard : Screen("supervisor_dashboard")
    data object CaseLog : Screen("case_log")
    data object Assessment : Screen("assessment/{pathway}") {
        fun createRoute(pathway: String) = "assessment/$pathway"
    }
    data object Result : Screen("result/{id}") {
        fun createRoute(id: Long) = "result/$id"
    }
    data object History : Screen("history")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.ModelSetup.route) {
            ModelSetupScreen(
                onSetupComplete = {
                    navController.navigate(SetupRoutes.Welcome) {
                        popUpTo(Screen.ModelSetup.route) { inclusive = true }
                    }
                }
            )
        }

        setupNavGraph(
            navController = navController,
            onSetupFinished = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )

        composable(Screen.Home.route) {
            HomeScreen(
                onPathwaySelected = { pathway ->
                    navController.navigate(Screen.Assessment.createRoute(pathway.name))
                },
                onViewHistory = {
                    navController.navigate(Screen.History.route)
                },
                onSupervisorAccess = {
                    navController.navigate(Screen.PinEntry.route)
                }
            )
        }

        composable(Screen.PinEntry.route) {
            PinEntryScreen(
                onSuccess = {
                    navController.navigate(Screen.SupervisorDashboard.route) {
                        popUpTo(Screen.PinEntry.route) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.SupervisorDashboard.route) {
            SupervisorDashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onViewCaseLog = { navController.navigate(Screen.CaseLog.route) }
            )
        }

        composable(Screen.CaseLog.route) {
            CaseLogScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Assessment.route,
            arguments = listOf(navArgument("pathway") { type = NavType.StringType })
        ) { backStackEntry ->
            val pathwayName = backStackEntry.arguments?.getString("pathway")
            val pathway = pathwayName?.let { Pathway.valueOf(it) } ?: Pathway.CHILD_U5
            AssessmentScreen(
                pathway = pathway,
                onResultReady = { id ->
                    navController.navigate(Screen.Result.createRoute(id)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Result.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) {
            ResultScreen(
                onNewAssessment = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onItemClick = { id -> navController.navigate(Screen.Result.createRoute(id)) }
            )
        }
    }
}
