package com.myapp.adventure.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.myapp.adventure.ui.editor.AdventureEditorViewModel
import com.myapp.adventure.ui.editor.EditMetaRoute
import com.myapp.adventure.ui.editor.EditorDestination
import com.myapp.adventure.ui.editor.EditorPreviewRequest
import com.myapp.adventure.ui.editor.EditorRoute
import com.myapp.adventure.ui.editor.HelpRoute
import com.myapp.adventure.ui.editor.InitialStateRoute
import com.myapp.adventure.ui.editor.VariablesRoute
import com.myapp.adventure.ui.editor.SearchRoute
import com.myapp.adventure.ui.editor.StoryStatsRoute
import com.myapp.adventure.ui.editor.SubroutinesRoute
import com.myapp.adventure.ui.editor.TemplateListScreen
import com.myapp.adventure.ui.editor.ValidationIssuesRoute
import com.myapp.adventure.ui.list.AdventureListScreen
import com.myapp.adventure.ui.logs.LogViewerScreen
import com.myapp.adventure.ui.menu.MenuDestination
import com.myapp.adventure.ui.menu.MenuScreen
import com.myapp.adventure.ui.reader.AdventureReaderViewModel
import com.myapp.adventure.ui.reader.ReaderRoute
import com.myapp.adventure.ui.settings.SettingsScreen
import com.myapp.adventure.ui.setup.SetupScreen
import com.myapp.adventure.ui.statistics.StatisticsScreen
import com.myapp.adventure.ui.theme.FontScopeProvider
import com.myapp.adventure.utils.FontScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phase 4-c: typed navigation routes. `@SerialName` keeps the generated
 * route patterns short and stable.
 */
object AppRoutes {
    @Serializable @SerialName("setup") data object Setup
    @Serializable @SerialName("menu") data object Menu
    @Serializable @SerialName("library") data object Library
    @Serializable @SerialName("statistics") data object Statistics
    @Serializable @SerialName("logs") data object Logs
    @Serializable @SerialName("settings") data object Settings
    @Serializable @SerialName("editor") data object Editor
    @Serializable @SerialName("editor_meta") data object EditMeta
    @Serializable @SerialName("editor_variables") data object Variables
    @Serializable @SerialName("editor_initial_state") data object InitialState
    @Serializable @SerialName("editor_templates") data object Templates
    @Serializable @SerialName("editor_subroutines") data object Subroutines
    @Serializable @SerialName("editor_help") data object Help
    @Serializable @SerialName("editor_validation") data object Validation
    @Serializable @SerialName("editor_search") data object Search
    @Serializable @SerialName("editor_stats") data object StoryStats
    @Serializable @SerialName("reader") data class Reader(
        val fileUri: String? = null,
        val previewId: String? = null,
    )
}

/**
 * Phase 4-c: single Activity-level Compose navigation graph.
 *
 * Scoping:
 *  - `AdventureEditorViewModel` is **Activity-scoped** (shared with the
 *    editor child routes) and survives leaving the editor, as before.
 *  - `AdventureReaderViewModel` is **NavBackStackEntry-scoped** via
 *    `viewModel()` inside the reader destination, so popping the reader
 *    disposes it and a fresh open re-runs `init`.
 *  - Reader preview payloads live in the Activity-scoped
 *    [ReaderPreviewViewModel] (large JSON is not put into the route).
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.Setup,
    ) {
        composable<AppRoutes.Setup> {
            SetupScreen(
                onNavigateToList = {
                    navController.navigate(AppRoutes.Menu) {
                        popUpTo<AppRoutes.Setup> { inclusive = true }
                    }
                },
            )
        }

        composable<AppRoutes.Menu> {
            MenuScreen(
                onNavigate = { destination ->
                    when (destination) {
                        MenuDestination.Library -> navController.navigate(AppRoutes.Library)
                        MenuDestination.Create -> navController.navigate(AppRoutes.Editor)
                        MenuDestination.Statistics -> navController.navigate(AppRoutes.Statistics)
                        MenuDestination.Logs -> navController.navigate(AppRoutes.Logs)
                        MenuDestination.Settings -> navController.navigate(AppRoutes.Settings)
                    }
                },
            )
        }

        composable<AppRoutes.Library> {
            AdventureListScreen(
                onNavigateToMenu = { navigateBackOrMenu(navController) },
                onOpenAdventure = { uri ->
                    navController.navigate(AppRoutes.Reader(fileUri = uri.toString()))
                },
            )
        }

        composable<AppRoutes.Statistics> {
            StatisticsScreen(onNavigateToMenu = { navigateBackOrMenu(navController) })
        }

        composable<AppRoutes.Logs> {
            LogViewerScreen()
        }

        composable<AppRoutes.Settings> {
            SettingsScreen()
        }

        composable<AppRoutes.Editor> {
            FontScopeProvider(FontScope.EDITOR) {
                val editorViewModel = activityEditorViewModel()
                val previewViewModel = activityReaderPreviewViewModel()
                EditorRoute(
                    viewModel = editorViewModel,
                    onNavigate = { destination ->
                        when (destination) {
                            EditorDestination.Menu -> navController.navigate(AppRoutes.Menu)
                            EditorDestination.Variables -> navController.navigate(AppRoutes.Variables)
                            EditorDestination.InitialState -> navController.navigate(AppRoutes.InitialState)
                            EditorDestination.Templates -> navController.navigate(AppRoutes.Templates)
                            EditorDestination.Subroutines -> navController.navigate(AppRoutes.Subroutines)
                            EditorDestination.Help -> navController.navigate(AppRoutes.Help)
                            EditorDestination.Meta -> navController.navigate(AppRoutes.EditMeta)
                            EditorDestination.Validation -> navController.navigate(AppRoutes.Validation)
                            EditorDestination.Search -> navController.navigate(AppRoutes.Search)
                            EditorDestination.Stats -> navController.navigate(AppRoutes.StoryStats)
                        }
                    },
                    onPreviewRequest = { request ->
                        val id = previewViewModel.put(request)
                        navController.navigate(AppRoutes.Reader(previewId = id))
                    },
                )
            }
        }

        composable<AppRoutes.EditMeta> {
            FontScopeProvider(FontScope.EDITOR) {
                EditMetaRoute(
                    viewModel = activityEditorViewModel(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<AppRoutes.Templates> {
            FontScopeProvider(FontScope.EDITOR) {
                TemplateListScreen(
                    viewModel = activityEditorViewModel(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<AppRoutes.Variables> {
            FontScopeProvider(FontScope.EDITOR) {
                VariablesRoute(
                    viewModel = activityEditorViewModel(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<AppRoutes.InitialState> {
            FontScopeProvider(FontScope.EDITOR) {
                InitialStateRoute(
                    viewModel = activityEditorViewModel(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<AppRoutes.Subroutines> {
            FontScopeProvider(FontScope.EDITOR) {
                SubroutinesRoute(
                    viewModel = activityEditorViewModel(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<AppRoutes.Help> {
            FontScopeProvider(FontScope.EDITOR) {
                HelpRoute(onBack = { navController.popBackStack() })
            }
        }

        composable<AppRoutes.Validation> {
            FontScopeProvider(FontScope.EDITOR) {
                ValidationIssuesRoute(
                    viewModel = activityEditorViewModel(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<AppRoutes.Search> {
            FontScopeProvider(FontScope.EDITOR) {
                SearchRoute(
                    viewModel = activityEditorViewModel(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<AppRoutes.StoryStats> {
            FontScopeProvider(FontScope.EDITOR) {
                StoryStatsRoute(
                    viewModel = activityEditorViewModel(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<AppRoutes.Reader> { entry ->
            val route: AppRoutes.Reader = entry.toRoute()
            val previewViewModel = activityReaderPreviewViewModel()
            val preview = route.previewId?.let(previewViewModel::get)
            val arguments = remember(route, preview) {
                buildReaderArguments(route, preview)
            }
            ReaderRoute(
                viewModel = readerViewModel(),
                arguments = arguments,
                onExit = { navController.popBackStack() },
                onNavigateToMenu = { navController.navigate(AppRoutes.Menu) },
                onNavigateToSettings = { navController.navigate(AppRoutes.Settings) },
            )
        }
    }
}

// =====================================================================
// ViewModel owners
// =====================================================================

@Composable
private fun activityEditorViewModel(): AdventureEditorViewModel {
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity) {
        "AppNavHost requires a ComponentActivity host"
    }
    return viewModel(viewModelStoreOwner = activity)
}

@Composable
private fun activityReaderPreviewViewModel(): ReaderPreviewViewModel {
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity) {
        "AppNavHost requires a ComponentActivity host"
    }
    return viewModel(viewModelStoreOwner = activity)
}

/** NavBackStackEntry-scoped: disposed together with the reader entry. */
@Composable
private fun readerViewModel(): AdventureReaderViewModel = viewModel()

// =====================================================================
// Helpers
// =====================================================================

private fun buildReaderArguments(
    route: AppRoutes.Reader,
    preview: EditorPreviewRequest?,
): Bundle = Bundle().apply {
    if (route.previewId != null) {
        if (preview != null) {
            putString("adventure_json", preview.adventureJson)
            putString("preview_scene_id", preview.sceneId)
            putInt("preview_chapter_index", preview.chapterIndex)
            putBoolean("preview_mode", true)
        }
    } else if (route.fileUri != null) {
        putString("adventure_file_uri", route.fileUri)
    }
}

/**
 * Library/Statistics "Меню" button: pop when possible (normal path), or
 * replace the stack with the Menu when these screens are the start
 * destination (e.g. a future deep link).
 */
private fun navigateBackOrMenu(navController: NavHostController) {
    if (navController.popBackStack()) return
    navController.navigate(AppRoutes.Menu) {
        popUpTo(navController.graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
