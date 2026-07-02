package com.nehonar.operator.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nehonar.operator.feature.console.ConsoleScreen
import com.nehonar.operator.feature.home.HomeScreen
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
                onOpenConsole = { navController.navigate(ConsoleRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<ConsoleRoute> {
            ConsoleScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
