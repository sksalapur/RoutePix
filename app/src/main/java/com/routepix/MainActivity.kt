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
import androidx.compose.ui.platform.LocalContext
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
import com.routepix.ui.permissions.PermissionsScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

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

    val context = LocalContext.current
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    
    val hasStorage = ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
    val startDest = if (hasStorage) Routes.Auth.route else Routes.Permissions.route

    NavHost(
        navController = navController,
        startDestination = startDest,
        modifier = modifier,
        enterTransition = {
            androidx.compose.animation.slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = androidx.compose.animation.core.tween(350)
            ) + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(350)
            )
        },
        exitTransition = {
            androidx.compose.animation.slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec = androidx.compose.animation.core.tween(350)
            ) + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(350)
            )
        },
        popEnterTransition = {
            androidx.compose.animation.slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec = androidx.compose.animation.core.tween(350)
            ) + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(350)
            )
        },
        popExitTransition = {
            androidx.compose.animation.slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = androidx.compose.animation.core.tween(350)
            ) + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(350)
            )
        }
    ) {
        composable(Routes.Permissions.route) {
            PermissionsScreen(
                onAllGranted = {
                    navController.navigate(Routes.Auth.route) {
                        popUpTo(Routes.Permissions.route) { inclusive = true }
                    }
                }
            )
        }

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

