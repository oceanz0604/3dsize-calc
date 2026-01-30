package com.aismartmeasure.app.navigation

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object ARMeasure : Screen("ar_measure")
    object QuickPhoto : Screen("quick_photo")
    object PrecisionPhoto : Screen("precision_photo")
}
