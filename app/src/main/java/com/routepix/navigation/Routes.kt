package com.routepix.navigation


sealed class Routes(val route: String) {
    data object Auth : Routes("auth")
    data object TripHome : Routes("trip_home")
    data object CreateTrip : Routes("create_trip")
    data object Settings : Routes("settings")
    data object JoinTrip : Routes("join_trip")
    data object Timeline : Routes("timeline/{tripId}") {
        fun createRoute(tripId: String) = "timeline/$tripId"
    }
    data object Permissions : Routes("permissions")
}

