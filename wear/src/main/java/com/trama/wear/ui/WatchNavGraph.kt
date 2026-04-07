package com.trama.wear.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.trama.wear.ui.screens.WatchAllEntriesScreen
import com.trama.wear.ui.screens.WatchEnrollmentScreen
import com.trama.wear.ui.screens.WatchHomeScreen
import com.trama.wear.ui.screens.WatchSettingsScreen

object WatchRoutes {
    const val HOME = "home"
    const val ALL_ENTRIES = "all_entries"
    const val SETTINGS = "settings"
    const val ENROLLMENT = "enrollment"
}

@Composable
fun WatchNavGraph() {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WatchRoutes.HOME
    ) {
        composable(WatchRoutes.HOME) {
            WatchHomeScreen(
                onViewAll = { navController.navigate(WatchRoutes.ALL_ENTRIES) },
                onSettingsClick = { navController.navigate(WatchRoutes.SETTINGS) }
            )
        }

        composable(WatchRoutes.ALL_ENTRIES) {
            WatchAllEntriesScreen()
        }

        composable(WatchRoutes.SETTINGS) {
            WatchSettingsScreen(
                onEnrollClick = { navController.navigate(WatchRoutes.ENROLLMENT) }
            )
        }

        composable(WatchRoutes.ENROLLMENT) {
            WatchEnrollmentScreen(
                onComplete = { navController.popBackStack() }
            )
        }
    }
}
