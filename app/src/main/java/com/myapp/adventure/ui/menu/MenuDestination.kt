package com.myapp.adventure.ui.menu

/**
 * Destinations reachable from the main menu. Declared as a sealed hierarchy
 * so the Fragment owns the mapping to concrete fragments (and, in Phase 4,
 * Navigation Compose owns the mapping to routes) without MenuScreen knowing
 * anything about FragmentManager or NavController.
 *
 * Import is intentionally NOT a destination — it is a side-effect (launch
 * SAF picker) and is handled inline by MenuScreen.
 */
sealed interface MenuDestination {
    data object Library : MenuDestination
    data object Create : MenuDestination
    data object Statistics : MenuDestination
    data object Logs : MenuDestination
    data object Settings : MenuDestination
}