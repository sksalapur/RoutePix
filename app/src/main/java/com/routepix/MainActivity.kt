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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.routepix.ui.home.SavedPhotosScreen

import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityManager.init(applicationContext)
        
        // Enable edge-to-edge with transparent bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        // Check for app updates in the background
        lifecycleScope.launch {
            com.routepix.util.UpdateChecker.checkForUpdate(applicationContext)
        }

        setContent {
            RoutepixTheme {
                // Root scaffold now ignores default insets to allow GlassTopBar to flow behind status bar
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    // Content still respects horizontal padding if needed, but we handle vertical insets in screens
                    RoutepixNavHost(modifier = Modifier.fillMaxSize())
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
                },
                onViewSavedPhotos = { navController.navigate(Routes.SavedPhotos.route) }
            )
        }

        composable(Routes.SavedPhotos.route) {
            SavedPhotosScreen(
                onBack = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() },
                onNavigateToOriginal = { photoId ->
                    navController.navigate(Routes.OriginalViewer.createRoute(tripId, photoId))
                }
            )
        }
        
        composable(
            route = Routes.OriginalViewer.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("photoId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            val photoId = backStackEntry.arguments?.getString("photoId") ?: ""
            
            com.routepix.ui.timeline.OriginalViewerScreen(
                tripId = tripId,
                photoId = photoId,
                onNavigateBack = { navController.popBackStack() },
                timelineViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            )
        }
    }
}

