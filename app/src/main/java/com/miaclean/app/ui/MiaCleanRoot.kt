package com.miaclean.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
fun MiaCleanRoot(pendingOpenResults: MutableState<Boolean> = mutableStateOf(false)) {
    val nav: NavHostController = rememberNavController()

    // Deep-link from the "found N new duplicates" notification. Consuming the flag here rather
    // than in MainActivity keeps all navigation decisions inside the Compose tree and avoids a
    // second source of truth for the back stack. The flag is reset after we trigger nav so a
    // recomposition (rotation, theme change) doesn't re-fire.
    LaunchedEffect(pendingOpenResults.value) {
        if (pendingOpenResults.value) {
            nav.navigate(Routes.Results) {
                // Preserve the user's place in the graph — if they're already on Results this
                // is a no-op, if they're deep on Settings this brings Results to the top.
                launchSingleTop = true
            }
            pendingOpenResults.value = false
        }
    }

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
