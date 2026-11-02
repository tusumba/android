package com.example.tusumba.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.tusumba.ui.screens.EventDetailScreen
import com.example.tusumba.ui.screens.CatalogScreen
import com.example.tusumba.ui.screens.ProfileScreen

sealed class Screen(val route: String) {
    data object Catalog : Screen("catalog")
    data object EventDetail : Screen("event_detail/{eventId}") {
        fun createRoute(eventId: String) = "event_detail/$eventId"
    }
    data object Profile : Screen("profile")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Catalog.route,
        modifier = modifier
    ) {
        composable(Screen.Catalog.route) {
            CatalogScreen(
                onEventClick = { eventId ->
                    navController.navigate(Screen.EventDetail.createRoute(eventId))
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }
        composable(Screen.EventDetail.route) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            EventDetailScreen(
                eventId = eventId,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}