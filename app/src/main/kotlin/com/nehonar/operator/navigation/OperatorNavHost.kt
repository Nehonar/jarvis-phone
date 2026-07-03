package com.nehonar.operator.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.nehonar.operator.feature.capture.CaptureScreen
import com.nehonar.operator.feature.console.ConsoleScreen
import com.nehonar.operator.feature.history.HistoryScreen
import com.nehonar.operator.feature.home.HomeScreen
import com.nehonar.operator.feature.reminders.RemindersScreen
import com.nehonar.operator.feature.review.ReviewScreen
import com.nehonar.operator.feature.settings.SettingsScreen

@Composable
fun OperatorNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenCapture = { navController.navigate(CaptureRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenConsole = { navController.navigate(ConsoleRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenReminders = { navController.navigate(RemindersRoute) },
            )
        }
        composable<CaptureRoute>(
            deepLinks = listOf(navDeepLink<CaptureRoute>(basePath = "operator://capture")),
        ) {
            CaptureScreen(
                onBack = { navController.popBackStack() },
                onCaptured = { voiceNoteId ->
                    navController.navigate(ReviewRoute(voiceNoteId)) {
                        popUpTo(CaptureRoute) { inclusive = true }
                    }
                },
                onOpenHistory = {
                    navController.navigate(HistoryRoute) {
                        popUpTo(CaptureRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<ReviewRoute> {
            ReviewScreen(onDone = { navController.popBackStack() })
        }
        composable<HistoryRoute> {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenReview = { voiceNoteId -> navController.navigate(ReviewRoute(voiceNoteId)) },
            )
        }
        composable<ConsoleRoute> {
            ConsoleScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<RemindersRoute> {
            RemindersScreen(onBack = { navController.popBackStack() })
        }
    }
}
