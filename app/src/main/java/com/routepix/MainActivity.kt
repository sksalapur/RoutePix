package com.routepix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import android.os.Build
import com.routepix.navigation.Routes
import com.routepix.security.SecurityManager
import com.routepix.ui.auth.AuthScreen
import com.routepix.ui.create.CreateTripScreen
import com.routepix.ui.home.TripHomeScreen
import com.routepix.ui.join.JoinTripScreen
import com.routepix.ui.settings.SettingsScreen
import com.routepix.ui.settings.SettingsViewModel
import com.routepix.ui.theme.RoutepixTheme
import com.routepix.ui.timeline.TimelineScreen
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityManager.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            RoutepixTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RoutepixNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun RoutepixNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Auth.route,
        modifier = modifier
    ) {

        composable(Routes.Auth.route) {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.TripHome.route) {
                        popUpTo(Routes.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.TripHome.route) {
            TripHomeScreen(
                onCreateTrip = { navController.navigate(Routes.CreateTrip.route) },
                onJoinTrip = { navController.navigate(Routes.JoinTrip.route) },
                onSettingsClick = { navController.navigate(Routes.Settings.route) },
                onTripClick = { trip ->
                    navController.navigate(Routes.Timeline.createRoute(trip.tripId))
                }
            )
        }

        composable(Routes.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onLogout = {
                    settingsViewModel.signOut() // Need to add this to ViewModel
                    navController.navigate(Routes.Auth.route) {
                        popUpTo(Routes.TripHome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CreateTrip.route) {
            CreateTripScreen(
                onTripCreated = { tripId ->
                    navController.navigate(Routes.Timeline.createRoute(tripId)) {
                        popUpTo(Routes.TripHome.route) { inclusive = false }
                    }
                },
                onSettings = { navController.navigate(Routes.Settings.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.JoinTrip.route) {
            JoinTripScreen(
                onTripJoined = { tripId ->
                    navController.navigate(Routes.Timeline.createRoute(tripId)) {
                        popUpTo(Routes.TripHome.route) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.Timeline.route,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            TimelineScreen(
                tripId = tripId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

