package com.mydiary.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mydiary.app.ui.screens.CalendarScreen
import com.mydiary.app.ui.screens.EntryDetailScreen
import com.mydiary.app.ui.screens.HomeScreen
import com.mydiary.app.ui.screens.SearchScreen
import com.mydiary.app.ui.screens.SettingsScreen
import com.mydiary.app.ui.screens.SummaryScreen

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{entryId}"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val SUMMARY = "summary"
    const val CALENDAR = "calendar"

    fun detail(entryId: Long) = "detail/$entryId"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onEntryClick = { entryId -> navController.navigate(Routes.detail(entryId)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onSummaryClick = { navController.navigate(Routes.SUMMARY) },
                onCalendarClick = { navController.navigate(Routes.CALENDAR) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: return@composable
            EntryDetailScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onEntryClick = { entryId -> navController.navigate(Routes.detail(entryId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SUMMARY) {
            SummaryScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CALENDAR) {
            CalendarScreen(
                onEntryClick = { entryId -> navController.navigate(Routes.detail(entryId)) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
