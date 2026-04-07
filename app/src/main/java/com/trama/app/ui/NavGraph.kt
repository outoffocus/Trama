package com.trama.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trama.app.ui.screens.CalendarScreen
import com.trama.app.ui.screens.EntryDetailScreen
import com.trama.app.ui.screens.HomeScreen
import com.trama.app.ui.screens.RecordingDetailScreen
import com.trama.app.ui.screens.RecordingsListScreen
import com.trama.app.ui.screens.SearchScreen
import com.trama.app.ui.screens.SettingsScreen
import com.trama.app.ui.screens.SummaryScreen

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{entryId}"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val SUMMARY = "summary"
    const val CALENDAR = "calendar"
    const val RECORDINGS_LIST = "recordings"
    const val RECORDING_DETAIL = "recording/{recordingId}"

    fun detail(entryId: Long) = "detail/$entryId"
    fun recordingDetail(recordingId: Long) = "recording/$recordingId"
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
                onCalendarClick = { navController.navigate(Routes.CALENDAR) },
                onRecordingClick = { recordingId ->
                    navController.navigate(Routes.recordingDetail(recordingId))
                },
                onRecordingsListClick = {
                    navController.navigate(Routes.RECORDINGS_LIST)
                }
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

        composable(Routes.RECORDINGS_LIST) {
            RecordingsListScreen(
                onBack = { navController.popBackStack() },
                onRecordingClick = { recordingId ->
                    navController.navigate(Routes.recordingDetail(recordingId))
                }
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

        composable(
            route = Routes.RECORDING_DETAIL,
            arguments = listOf(navArgument("recordingId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recordingId = backStackEntry.arguments?.getLong("recordingId") ?: return@composable
            RecordingDetailScreen(
                recordingId = recordingId,
                onBack = { navController.popBackStack() },
                onActionClick = { entryId -> navController.navigate(Routes.detail(entryId)) }
            )
        }
    }
}
