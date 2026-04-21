package com.miaclean.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.miaclean.app.domain.MediaCategory
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
fun MiaCleanRoot(
    // Defaults are wrapped in `remember` so a `@Preview` or test that calls `MiaCleanRoot()`
    // without args gets a single stable `MutableState` instance across recompositions instead
    // of a fresh one per invocation. The production call site in `MainActivity.setContent`
    // always passes both arguments explicitly, so the defaults are dead code in the shipped
    // app — but wrapping is free and prevents a future `@Preview` author from tripping on it.
    pendingOpenResults: MutableState<Boolean> = remember { mutableStateOf(false) },
    pendingCategoryFilter: MutableState<MediaCategory?> = remember { mutableStateOf(null) },
) {
    val nav: NavHostController = rememberNavController()

    // Deep-link from the "found N new duplicates" notification. Consuming the flag here rather
    // than in MainActivity keeps all navigation decisions inside the Compose tree and avoids a
    // second source of truth for the back stack. The flag is reset after we trigger nav so a
    // recomposition (rotation, theme change) doesn't re-fire.
    LaunchedEffect(pendingOpenResults.value) {
        if (pendingOpenResults.value) {
            nav.navigate(Routes.Results) {
                // On cold start from a notification tap, Onboarding is the start destination
                // but the user already completed it (otherwise the worker never produced a
                // duplicate to notify about). Pop Onboarding off the stack so pressing Back on
                // Results exits the app instead of dumping them into the first-run flow.
                popUpTo(Routes.Onboarding) { inclusive = true }
                // If the activity is already alive on a different screen (e.g. Settings) this
                // collapses the stack to a single Results entry rather than stacking duplicates.
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
                    pendingCategoryFilter = pendingCategoryFilter,
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
