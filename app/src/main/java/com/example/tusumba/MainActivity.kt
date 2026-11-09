package com.example.tusumba

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.tusumba.ui.auth.authColorScheme
import com.example.tusumba.ui.catalog.catalogColorScheme
import com.example.tusumba.ui.navigation.AppNavigation
import com.example.tusumba.ui.navigation.Screen
import com.example.tusumba.ui.theme.TusumbaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TusumbaTheme {
                val navController = rememberNavController()
                val route = navController.currentBackStackEntryAsState().value?.destination?.route
                val background = when (route) {
                    Screen.SignIn.route, Screen.Registration.route -> authColorScheme().background
                    Screen.Clubs.route, Screen.ClubDetail.route -> catalogColorScheme().surface
                    else -> MaterialTheme.colorScheme.background
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = background
                ) { innerPadding ->
                    AppNavigation(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                    )
                }
            }
        }
    }
}
