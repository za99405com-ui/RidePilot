package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "الرئيسية")
    object WorkZones : Screen("work_zones", "مناطق العمل")
    object UberPricing : Screen("uber_pricing", "تسعير Uber")
    object InDrivePricing : Screen("indrive_pricing", "تسعير inDrive")
    object Negotiation : Screen("negotiation", "إعدادات التفاوض")
    object Calibration : Screen("calibration", "المعايرة المباشرة")
    object Logs : Screen("logs", "سجل العمليات")
    object Permissions : Screen("permissions", "معالج الأذونات")
    object AdvancedSettings : Screen("advanced_settings", "الإعدادات المتقدمة")
}
