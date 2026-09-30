package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AppTarget
import com.example.ui.screens.AdvancedSettingsScreen
import com.example.ui.screens.CalibrationScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.NegotiationScreen
import com.example.ui.screens.PermissionsWizardScreen
import com.example.ui.screens.PricingManagementScreen
import com.example.ui.screens.ZonesScreen

@Composable
fun RidePilotNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.WorkZones.route) {
            ZonesScreen(navController = navController)
        }
        composable(Screen.UberPricing.route) {
            PricingManagementScreen(navController = navController, appTarget = AppTarget.UBER)
        }
        composable(Screen.InDrivePricing.route) {
            PricingManagementScreen(navController = navController, appTarget = AppTarget.INDRIVE)
        }
        composable(Screen.Negotiation.route) {
            NegotiationScreen(navController = navController)
        }
        composable(Screen.Calibration.route) {
            CalibrationScreen(navController = navController)
        }
        composable(Screen.Logs.route) {
            LogsScreen(navController = navController)
        }
        composable(Screen.Permissions.route) {
            PermissionsWizardScreen(navController = navController)
        }
        composable(Screen.AdvancedSettings.route) {
            AdvancedSettingsScreen(navController = navController)
        }
    }
}
