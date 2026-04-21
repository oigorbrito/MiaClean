package com.miaclean.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.miaclean.app.ui.onboarding.OnboardingScreen
import com.miaclean.app.ui.results.ResultsScreen
import com.miaclean.app.ui.scan.ScanScreen
import com.miaclean.app.ui.settings.SettingsScreen

object Routes {
    const val Onboarding = "onboarding"
    const val Scan = "scan"
    const val Results = "results"
    const val Settings = "settings"
}

@Composable
fun MiaCleanRoot() {
    val nav: NavHostController = rememberNavController()
    Scaffold { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.Onboarding,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable(Routes.Onboarding) {
                OnboardingScreen(onContinue = {
                    nav.navigate(Routes.Scan) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                })
            }
            composable(Routes.Scan) {
                ScanScreen(onOpenResults = { nav.navigate(Routes.Results) })
            }
            composable(Routes.Results) {
                ResultsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
