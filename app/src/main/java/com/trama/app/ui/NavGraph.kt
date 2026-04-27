package com.trama.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trama.app.ui.screens.CalendarScreen
import com.trama.app.ui.screens.ChatScreen
import com.trama.app.ui.screens.EntryDetailScreen
import com.trama.app.ui.screens.HomeScreen
import com.trama.app.ui.screens.PlaceDetailScreen
import com.trama.app.ui.screens.RecordingDetailScreen
import com.trama.app.ui.screens.RecordingsListScreen
import com.trama.app.ui.screens.SearchScreen
import com.trama.app.ui.screens.SettingsSection
import com.trama.app.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{entryId}"
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings/{section}"
    const val SEARCH = "search"
    const val CALENDAR = "calendar?selectedDayStart={selectedDayStart}"
    const val CHAT = "chat"
    const val RECORDINGS_LIST = "recordings"
    const val RECORDING_DETAIL = "recording/{recordingId}"
    const val PLACE_DETAIL = "place/{placeId}"

    fun detail(entryId: Long) = "detail/$entryId"
    fun settings(section: SettingsSection) =
        if (section == SettingsSection.ROOT) SETTINGS else "settings/${section.route}"
    fun recordingDetail(recordingId: Long) = "recording/$recordingId"
    fun placeDetail(placeId: Long) = "place/$placeId"
    fun calendar(selectedDayStart: Long? = null) =
        if (selectedDayStart == null) "calendar"
        else "calendar?selectedDayStart=$selectedDayStart"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onEntryClick = { entryId -> navController.navigate(Routes.detail(entryId)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onCalendarClick = { navController.navigate(Routes.calendar()) },
                onChatClick = { navController.navigate(Routes.CHAT) },
                onRecordingClick = { recordingId ->
                    navController.navigate(Routes.recordingDetail(recordingId))
                },
                onPlaceClick = { placeId -> navController.navigate(Routes.placeDetail(placeId)) },
                onRecordingsListClick = {
                    navController.navigate(Routes.RECORDINGS_LIST)
                }
            )
        }

        composable(Routes.CHAT) {
            ChatScreen(onBack = { navController.popBackStack() })
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
            SettingsScreen(
                section = SettingsSection.ROOT,
                onBack = { navController.popBackStack() },
                onOpenSection = { navController.navigate(Routes.settings(it)) }
            )
        }

        composable(
            route = Routes.SETTINGS_SECTION,
            arguments = listOf(navArgument("section") { type = NavType.StringType })
        ) { backStackEntry ->
            val section = SettingsSection.fromRoute(backStackEntry.arguments?.getString("section"))
            SettingsScreen(
                section = section,
                onBack = { navController.popBackStack() },
                onOpenSection = { navController.navigate(Routes.settings(it)) }
            )
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

        composable(
            route = Routes.CALENDAR,
            arguments = listOf(
                navArgument("selectedDayStart") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val selectedDayStart = backStackEntry.arguments?.getLong("selectedDayStart") ?: -1L
            CalendarScreen(
                initialSelectedDayStart = selectedDayStart.takeIf { it >= 0L },
                onEntryClick = { entryId -> navController.navigate(Routes.detail(entryId)) },
                onRecordingClick = { recordingId -> navController.navigate(Routes.recordingDetail(recordingId)) },
                onBack = { navController.popBackStack() },
                onPlaceClick = { placeId -> navController.navigate(Routes.placeDetail(placeId)) }
            )
        }

        composable(
            route = Routes.PLACE_DETAIL,
            arguments = listOf(navArgument("placeId") { type = NavType.LongType })
        ) { backStackEntry ->
            val placeId = backStackEntry.arguments?.getLong("placeId") ?: return@composable
            PlaceDetailScreen(
                placeId = placeId,
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
