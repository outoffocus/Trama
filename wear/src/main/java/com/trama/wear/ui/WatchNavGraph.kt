package com.trama.wear.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.trama.wear.ui.screens.WatchHomeScreen

object WatchRoutes {
    const val HOME = "home"
}

@Composable
fun WatchNavGraph() {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WatchRoutes.HOME
    ) {
        composable(WatchRoutes.HOME) {
            WatchHomeScreen()
        }
    }
}
