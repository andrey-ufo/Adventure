package com.myapp.adventure.ui.menu

/**
 * Destinations reachable from the main menu. Declared as a sealed hierarchy
 * so `AppNavHost` owns the mapping to typed Navigation Compose routes
 * without MenuScreen knowing anything about NavController.
 *
 * Import is intentionally NOT a destination — it is a side-effect (launch
 * SAF picker) and is handled inline by MenuScreen.
 */
sealed interface MenuDestination {
    data object Library : MenuDestination
    data object Create : MenuDestination
    data object Statistics : MenuDestination
    data object Settings : MenuDestination
}