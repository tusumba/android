package com.example.tusumba.ui.navigation

import android.net.Uri
import com.example.tusumba.ui.applications.ApplicationsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.tusumba.ui.auth.AuthScreen
import com.example.tusumba.ui.auth.AuthViewModel
import com.example.tusumba.ui.clubs.ClubDetailScreen
import com.example.tusumba.ui.clubs.ClubsScreen
import com.example.tusumba.ui.registrations.MyRegistrationsScreen
import com.example.tusumba.ui.screens.CatalogScreen
import com.example.tusumba.ui.screens.EventDetailScreen
import com.example.tusumba.ui.screens.ProfileScreen

sealed class Screen(val route: String) {
    data object Catalog : Screen("catalog")
    data object Clubs : Screen("clubs")
    data object ClubDetail : Screen("club_detail/{clubId}") {
        fun createRoute(clubId: String) = "club_detail/${Uri.encode(clubId)}"
    }
    data object EventDetail : Screen("event_detail/{eventId}") {
        fun createRoute(eventId: String) = "event_detail/${Uri.encode(eventId)}"
    }
    data object Profile : Screen("profile")
    data object MyRegistrations : Screen("my_registrations")
    data object MyApplications : Screen("my_applications")
    data object NewApplication : Screen("new_application")
    data object ApplicationDetail : Screen("application/{applicationId}") {
        fun createRoute(id: String) = "application/${Uri.encode(id)}"
    }
    data object SignIn : Screen("sign_in")
    data object Registration : Screen("registration")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    LaunchedEffect(navController, authViewModel) {
        authViewModel.signedOut.collect {
            navController.navigate(Screen.SignIn.route) { launchSingleTop = true }
        }
    }
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
                },
                onClubsClick = {
                    navController.navigate(Screen.Clubs.route) { launchSingleTop = true }
                },
            )
        }
        composable(Screen.Clubs.route) {
            ClubsScreen(
                onClubClick = { clubId -> navController.navigate(Screen.ClubDetail.createRoute(clubId)) },
                onEventsClick = { navController.popBackStack(Screen.Catalog.route, inclusive = false) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
            )
        }
        composable(Screen.ClubDetail.route) {
            ClubDetailScreen(
                onBackClick = { navController.popBackStack() },
                onEventClick = { eventId ->
                    navController.navigate(Screen.EventDetail.createRoute(eventId))
                },
            )
        }
        composable(Screen.EventDetail.route) {
            EventDetailScreen(
                userId = authViewModel.user?.id,
                onBackClick = { navController.popBackStack() },
                onSignIn = { navController.navigate(Screen.SignIn.route) { launchSingleTop = true } },
                onMyRegistrations = {
                    if (!navController.popBackStack(Screen.MyRegistrations.route, inclusive = false)) {
                        navController.navigate(Screen.MyRegistrations.route)
                    }
                },
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                user = authViewModel.user,
                onBackClick = { navController.popBackStack() },
                onSignIn = { navController.navigate(Screen.SignIn.route) { launchSingleTop = true } },
                onSignOut = authViewModel::signOut,
                onMyApplications = { navController.navigate(Screen.MyApplications.route) { launchSingleTop = true } },
                onMyRegistrations = {
                    navController.navigate(Screen.MyRegistrations.route) { launchSingleTop = true }
                },
            )
        }
        composable(Screen.MyRegistrations.route) {
            MyRegistrationsScreen(
                userId = authViewModel.user?.id,
                onBackClick = { navController.popBackStack() },
                onEventClick = { eventId -> navController.navigate(Screen.EventDetail.createRoute(eventId)) },
                onSignIn = { navController.navigate(Screen.SignIn.route) { launchSingleTop = true } },
            )
        }
        composable(Screen.MyApplications.route) {
            ApplicationsScreen(userId = authViewModel.user?.id,
                onBack = { navController.popBackStack() },
                onSignIn = { navController.navigate(Screen.SignIn.route) },
                onCreate = { navController.navigate(Screen.NewApplication.route) },
                onApplication = { navController.navigate(Screen.ApplicationDetail.createRoute(it)) })
        }
        composable(Screen.NewApplication.route) {
            ApplicationsScreen(userId = authViewModel.user?.id, mode = "form",
                onBack = { navController.popBackStack() },
                onSignIn = { navController.navigate(Screen.SignIn.route) },
                onApplication = { id ->
                    navController.navigate(Screen.ApplicationDetail.createRoute(id)) {
                        popUpTo(Screen.NewApplication.route) { inclusive = true }
                    }
                })
        }
        composable(Screen.ApplicationDetail.route) { entry ->
            ApplicationsScreen(userId = authViewModel.user?.id, mode = "detail",
                applicationId = entry.arguments?.getString("applicationId"),
                onBack = { navController.popBackStack() },
                onSignIn = { navController.navigate(Screen.SignIn.route) },
                onObject = { id, event -> navController.navigate(
                    if (event) Screen.EventDetail.createRoute(id) else Screen.ClubDetail.createRoute(id)) })
        }
        composable(Screen.SignIn.route) {
            AuthScreen(
                isRegistration = false,
                onBackClick = { navController.popBackStack() },
                onSwitchMode = {
                    navController.navigate(Screen.Registration.route) { launchSingleTop = true }
                },
                onAuthenticate = { form -> authViewModel.signIn(form.login, form.password) },
                onAuthenticated = { navController.popBackStack() }
            )
        }
        composable(Screen.Registration.route) {
            AuthScreen(
                isRegistration = true,
                onBackClick = { navController.popBackStack() },
                onSwitchMode = { navController.popBackStack() },
                onAuthenticate = { form ->
                    authViewModel.register(form.login, form.password, form.nickname)
                },
                onAuthenticated = { navController.popBackStack(Screen.SignIn.route, inclusive = true) }
            )
        }
    }
}
