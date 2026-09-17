package com.trama.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trama.app.ui.screens.AgendaScreen
import com.trama.app.ui.screens.CalendarScreen
import com.trama.app.ui.screens.ChatScreen
import com.trama.app.ui.screens.EntryDetailScreen
import com.trama.app.ui.screens.PlaceDetailScreen
import com.trama.app.ui.screens.RecordingDetailScreen
import com.trama.app.ui.screens.RecordingsListScreen
import com.trama.app.ui.screens.SearchScreen
import com.trama.app.ui.screens.SettingsSection
import com.trama.app.ui.screens.SettingsScreen
import com.trama.app.ui.components.CaptureQuickActions

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{entryId}"
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings/{section}"
    const val SEARCH = "search"
    const val CHAT = "chat"
    const val AGENDA = "agenda"
    const val RECORDINGS_LIST = "recordings"
    const val RECORDING_DETAIL = "recording/{recordingId}"
    const val PLACE_DETAIL = "place/{placeId}"

    /** Public destinations that Home must expose directly or through its calendar content. */
    val HOME_REACHABLE_DESTINATIONS = setOf(
        SETTINGS,
        SEARCH,
        AGENDA,
        RECORDINGS_LIST
    )

    fun detail(entryId: Long) = "detail/$entryId"
    fun settings(section: SettingsSection) =
        if (section == SettingsSection.ROOT) SETTINGS else "settings/${section.route}"
    fun recordingDetail(recordingId: Long) = "recording/$recordingId"
    fun placeDetail(placeId: Long) = "place/$placeId"
}

@Composable
fun NavGraph() {
    NavGraph(startDestination = Routes.HOME)
}

@Composable
fun NavGraph(startDestination: String) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val primaryRoutes = setOf(Routes.HOME, Routes.AGENDA, Routes.SEARCH)

    fun navigatePrimary(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        floatingActionButton = {
            if (currentRoute == Routes.AGENDA || currentRoute == Routes.SEARCH) {
                CaptureQuickActions()
            }
        },
        bottomBar = {
            if (currentRoute in primaryRoutes) {
                NavigationBar {
                    listOf(
                        Triple(Routes.HOME, "Hoy", Icons.Default.Today),
                        Triple(Routes.AGENDA, "Acciones", Icons.Default.TaskAlt),
                        Triple(Routes.SEARCH, "Recuerdos", Icons.Default.Search)
                    ).forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = { navigatePrimary(route) },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { outerPadding ->
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.padding(outerPadding)
    ) {
        composable(Routes.HOME) {
            CalendarScreen(
                onEntryClick = { entryId -> navController.navigate(Routes.detail(entryId)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onSearchClick = { navigatePrimary(Routes.SEARCH) },
                onRecordingsListClick = { navController.navigate(Routes.RECORDINGS_LIST) },
                onAgendaClick = { navigatePrimary(Routes.AGENDA) },
                onRecordingClick = { recordingId ->
                    navController.navigate(Routes.recordingDetail(recordingId))
                },
                onPlaceClick = { placeId -> navController.navigate(Routes.placeDetail(placeId)) }
            )
        }

        composable(Routes.CHAT) {
            ChatScreen(
                initialQuery = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>("memoryQuery").orEmpty(),
                onBack = { navController.popBackStack() },
                onEntryClick = { navController.navigate(Routes.detail(it)) },
                onPlaceClick = { navController.navigate(Routes.placeDetail(it)) },
                onRecordingClick = { navController.navigate(Routes.recordingDetail(it)) }
            )
        }

        composable(Routes.AGENDA) {
            AgendaScreen(
                onBack = { navController.popBackStack() },
                onEntryClick = { entryId -> navController.navigate(Routes.detail(entryId)) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: return@composable
            EntryDetailScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onRecordingClick = { recordingId ->
                    navController.navigate(Routes.recordingDetail(recordingId))
                }
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
                onPlaceClick = { placeId -> navController.navigate(Routes.placeDetail(placeId)) },
                onRecordingClick = { recordingId ->
                    navController.navigate(Routes.recordingDetail(recordingId))
                },
                onAsk = { query ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("memoryQuery", query)
                    navController.navigate(Routes.CHAT)
                },
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
}
