package com.mydiary.wear.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mydiary.wear.ui.screens.WatchAllEntriesScreen
import com.mydiary.wear.ui.screens.WatchEnrollmentScreen
import com.mydiary.wear.ui.screens.WatchEntryDetailScreen
import com.mydiary.wear.ui.screens.WatchHomeScreen

object WatchRoutes {
    const val HOME = "home"
    const val ALL_ENTRIES = "all_entries"
    const val SETTINGS = "settings"
    const val ENROLLMENT = "enrollment"
    const val DETAIL = "detail/{entryId}"

    fun detail(entryId: Long) = "detail/$entryId"
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
                onEntryClick = { entryId -> navController.navigate(WatchRoutes.detail(entryId)) },
                onViewAll = { navController.navigate(WatchRoutes.ALL_ENTRIES) },
                onSettingsClick = { navController.navigate(WatchRoutes.SETTINGS) }
            )
        }

        composable(WatchRoutes.ALL_ENTRIES) {
            WatchAllEntriesScreen(
                onEntryClick = { entryId -> navController.navigate(WatchRoutes.detail(entryId)) }
            )
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

        composable(
            route = WatchRoutes.DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: return@composable
            WatchEntryDetailScreen(entryId = entryId)
        }
    }
}
