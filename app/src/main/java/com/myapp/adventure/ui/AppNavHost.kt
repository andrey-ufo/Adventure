package com.myapp.adventure.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.myapp.adventure.R
import com.myapp.adventure.projects.EditorProjectSession
import com.myapp.adventure.projects.ProjectDraftManager
import com.myapp.adventure.projects.ProjectRef
import com.myapp.adventure.ui.editor.AdventureEditorViewModel
import com.myapp.adventure.ui.editor.EditMetaRoute
import com.myapp.adventure.ui.editor.applyDraft
import com.myapp.adventure.ui.editor.loadAdventureFromUri
import com.myapp.adventure.ui.editor.EditorDestination
import com.myapp.adventure.ui.editor.EditorPreviewRequest
import com.myapp.adventure.ui.editor.EditorRoute
import com.myapp.adventure.ui.editor.HelpRoute
import com.myapp.adventure.ui.editor.saveAdventureToFile
import com.myapp.adventure.ui.editor.InitialStateRoute
import com.myapp.adventure.ui.editor.VariablesRoute
import com.myapp.adventure.ui.editor.StoryStatsRoute
import com.myapp.adventure.ui.editor.SubroutinesRoute
import com.myapp.adventure.ui.editor.TemplateListScreen
import com.myapp.adventure.ui.editor.ValidationIssuesRoute
import com.myapp.adventure.ui.list.AdventureDetailsScreen
import com.myapp.adventure.ui.list.AdventureListScreen
import com.myapp.adventure.ui.logs.LogViewerScreen
import com.myapp.adventure.ui.menu.MenuDestination
import com.myapp.adventure.ui.menu.MenuScreen
import com.myapp.adventure.ui.projects.ProjectManagerScreen
import com.myapp.adventure.ui.reader.AdventureReaderViewModel
import com.myapp.adventure.ui.reader.ReaderRoute
import com.myapp.adventure.ui.settings.SettingsScreen
import com.myapp.adventure.ui.setup.SetupScreen
import com.myapp.adventure.ui.statistics.StatisticsScreen
import com.myapp.adventure.ui.theme.FontScopeProvider
import com.myapp.adventure.data.FileSaveStatus
import com.myapp.adventure.utils.FontScope
import com.myapp.adventure.utils.LastAdventureStore
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
    @Serializable @SerialName("projects") data object Projects
    @Serializable @SerialName("adventure_details") data class AdventureDetails(
        val fileUri: String,
    )
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
    @Serializable @SerialName("editor_stats") data object StoryStats
    @Serializable @SerialName("reader") data class Reader(
        val fileUri: String? = null,
        val previewId: String? = null,
        val openSlots: Boolean = false,
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
    val context = LocalContext.current
    val editorViewModel = activityEditorViewModel()
    var lastAdventureUri by remember {
        mutableStateOf(LastAdventureStore.get(context))
    }
    var pendingEditorNavigation by remember { mutableStateOf<AppSection?>(null) }
    var showEditorExitDialog by remember { mutableStateOf(false) }

    fun openReader(uri: Uri, openSlots: Boolean = false) {
        lastAdventureUri = uri
        LastAdventureStore.set(context, uri)
        navController.navigate(
            AppRoutes.Reader(fileUri = uri.toString(), openSlots = openSlots),
        )
    }

    fun performGlobalNavigation(section: AppSection) {
        when (section) {
            AppSection.Library -> navController.navigate(AppRoutes.Library) {
                launchSingleTop = true
            }
            AppSection.Editor -> navController.navigate(AppRoutes.Editor) {
                launchSingleTop = true
            }
            AppSection.Reader -> lastAdventureUri?.let(::openReader)
            AppSection.Statistics -> navController.navigate(AppRoutes.Statistics) {
                launchSingleTop = true
            }
            AppSection.Settings -> navController.navigate(AppRoutes.Settings) {
                launchSingleTop = true
            }
        }
    }

    fun openProject(project: ProjectRef) {
        val draft = ProjectDraftManager.load(context, project.id)
        if (draft != null) {
            applyDraft(context, editorViewModel, draft)
        } else {
            loadAdventureFromUri(
                context,
                editorViewModel,
                project.fileUri.toUri(),
                clearDraft = false,
            )
        }
        EditorProjectSession.setCurrent(context, project)
        navController.navigate(AppRoutes.Editor) {
            popUpTo<AppRoutes.Editor> { inclusive = true }
            launchSingleTop = true
        }
    }

    fun navigateGlobal(section: AppSection) {
        val route = navController.currentBackStackEntry?.destination?.route
        val inEditor = route?.startsWith("editor") == true
        if (inEditor && editorViewModel.uiState.value.fileStatus == FileSaveStatus.DIRTY) {
            pendingEditorNavigation = section
            showEditorExitDialog = true
        } else {
            performGlobalNavigation(section)
        }
    }

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
                        MenuDestination.Create -> {
                            EditorProjectSession.clear(context)
                            navController.navigate(AppRoutes.Editor)
                        }
                        MenuDestination.Projects -> navController.navigate(AppRoutes.Projects)
                        MenuDestination.Statistics -> navController.navigate(AppRoutes.Statistics)
                        MenuDestination.Settings -> navController.navigate(AppRoutes.Settings)
                    }
                },
            )
        }

        composable<AppRoutes.Library> {
            AdventureListScreen(
                onNavigateGlobal = ::navigateGlobal,
                canOpenReader = lastAdventureUri != null,
                onOpenAdventure = { uri ->
                    navController.navigate(
                        AppRoutes.AdventureDetails(fileUri = uri.toString()),
                    )
                },
            )
        }

        composable<AppRoutes.Projects> {
            ProjectManagerScreen(
                onBack = { navController.popBackStack() },
                onOpen = ::openProject,
                onDeleted = { project ->
                    ProjectDraftManager.clear(context, project.id)
                    if (EditorProjectSession.current(context)?.id == project.id) {
                        EditorProjectSession.clear(context)
                    }
                },
                onNavigateGlobal = ::navigateGlobal,
                canOpenReader = lastAdventureUri != null,
            )
        }

        composable<AppRoutes.AdventureDetails> { entry ->
            val route: AppRoutes.AdventureDetails = entry.toRoute()
            val uri = route.fileUri.toUri()
            AdventureDetailsScreen(
                fileUri = route.fileUri,
                onBack = { navController.popBackStack() },
                onRead = { openReader(uri) },
                onRestart = { openReader(uri) },
                onLoadSlot = { openReader(uri, openSlots = true) },
                onDeleted = {
                    if (lastAdventureUri == uri) {
                        lastAdventureUri = null
                        LastAdventureStore.set(context, null)
                    }
                    navController.navigate(AppRoutes.Library) {
                        popUpTo<AppRoutes.Library> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateGlobal = ::navigateGlobal,
                canOpenReader = lastAdventureUri != null,
            )
        }

        composable<AppRoutes.Statistics> {
            StatisticsScreen(
                onNavigateGlobal = ::navigateGlobal,
                canOpenReader = lastAdventureUri != null,
            )
        }

        composable<AppRoutes.Logs> {
            LogViewerScreen(
                onBack = { navigateBackOrMenu(navController) },
                onNavigateGlobal = ::navigateGlobal,
                canOpenReader = lastAdventureUri != null,
            )
        }

        composable<AppRoutes.Settings> {
            SettingsScreen(
                onBack = { navigateBackOrMenu(navController) },
                onNavigateGlobal = ::navigateGlobal,
                canOpenReader = lastAdventureUri != null,
                onOpenLogs = { navController.navigate(AppRoutes.Logs) },
            )
        }

        composable<AppRoutes.Editor> {
            FontScopeProvider(FontScope.EDITOR) {
                val editorViewModel = activityEditorViewModel()
                val previewViewModel = activityReaderPreviewViewModel()
                EditorRoute(
                    viewModel = editorViewModel,
                    onNavigate = { destination ->
                        when (destination) {
                            EditorDestination.Projects -> navController.navigate(AppRoutes.Projects)
                            EditorDestination.Variables -> navController.navigate(AppRoutes.Variables)
                            EditorDestination.InitialState -> navController.navigate(AppRoutes.InitialState)
                            EditorDestination.Templates -> navController.navigate(AppRoutes.Templates)
                            EditorDestination.Subroutines -> navController.navigate(AppRoutes.Subroutines)
                            EditorDestination.Help -> navController.navigate(AppRoutes.Help)
                            EditorDestination.Meta -> navController.navigate(AppRoutes.EditMeta)
                            EditorDestination.Validation -> navController.navigate(AppRoutes.Validation)
                            EditorDestination.Stats -> navController.navigate(AppRoutes.StoryStats)
                        }
                    },
                    onNavigateGlobal = ::navigateGlobal,
                    onExit = { navController.popBackStack() },
                    canOpenReader = lastAdventureUri != null,
                    onPreviewRequest = { request ->
                        val id = previewViewModel.put(request)
                        navController.navigate(AppRoutes.Reader(previewId = id))
                    },
                )
            }
        }

        composable<AppRoutes.EditMeta> {
            EditorChildScope(lastAdventureUri != null, ::navigateGlobal) {
                FontScopeProvider(FontScope.EDITOR) {
                    EditMetaRoute(
                        viewModel = activityEditorViewModel(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        composable<AppRoutes.Templates> {
            EditorChildScope(lastAdventureUri != null, ::navigateGlobal) {
                FontScopeProvider(FontScope.EDITOR) {
                    TemplateListScreen(
                        viewModel = activityEditorViewModel(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        composable<AppRoutes.Variables> {
            EditorChildScope(lastAdventureUri != null, ::navigateGlobal) {
                FontScopeProvider(FontScope.EDITOR) {
                    VariablesRoute(
                        viewModel = activityEditorViewModel(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        composable<AppRoutes.InitialState> {
            EditorChildScope(lastAdventureUri != null, ::navigateGlobal) {
                FontScopeProvider(FontScope.EDITOR) {
                    InitialStateRoute(
                        viewModel = activityEditorViewModel(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        composable<AppRoutes.Subroutines> {
            EditorChildScope(lastAdventureUri != null, ::navigateGlobal) {
                FontScopeProvider(FontScope.EDITOR) {
                    SubroutinesRoute(
                        viewModel = activityEditorViewModel(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        composable<AppRoutes.Help> {
            EditorChildScope(lastAdventureUri != null, ::navigateGlobal) {
                FontScopeProvider(FontScope.EDITOR) {
                    HelpRoute(onBack = { navController.popBackStack() })
                }
            }
        }

        composable<AppRoutes.Validation> {
            EditorChildScope(lastAdventureUri != null, ::navigateGlobal) {
                FontScopeProvider(FontScope.EDITOR) {
                    ValidationIssuesRoute(
                        viewModel = activityEditorViewModel(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        composable<AppRoutes.StoryStats> {
            EditorChildScope(lastAdventureUri != null, ::navigateGlobal) {
                FontScopeProvider(FontScope.EDITOR) {
                    StoryStatsRoute(
                        viewModel = activityEditorViewModel(),
                        onBack = { navController.popBackStack() },
                    )
                }
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
                openSlotsInitially = route.openSlots,
                onExit = { navController.popBackStack() },
                onNavigateToMenu = { navController.navigate(AppRoutes.Menu) },
                onNavigateGlobal = ::navigateGlobal,
                canOpenReader = lastAdventureUri != null,
            )
        }
    }

    if (showEditorExitDialog) {
        AlertDialog(
            onDismissRequest = {
                showEditorExitDialog = false
                pendingEditorNavigation = null
            },
            title = { Text(stringResource(R.string.editor_exit_dialog_title)) },
            text = { Text(stringResource(R.string.editor_exit_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saveAdventureToFile(context, editorViewModel)) {
                            showEditorExitDialog = false
                            val target = pendingEditorNavigation
                            pendingEditorNavigation = null
                            target?.let(::performGlobalNavigation)
                        }
                    },
                ) {
                    Text(stringResource(R.string.editor_exit_dialog_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditorExitDialog = false
                        val target = pendingEditorNavigation
                        pendingEditorNavigation = null
                        target?.let(::performGlobalNavigation)
                    },
                ) {
                    Text(stringResource(R.string.editor_exit_dialog_exit))
                }
            },
        )
    }
}

@Composable
private fun EditorChildScope(
    canOpenReader: Boolean,
    onNavigateGlobal: (AppSection) -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlobalMenu provides GlobalMenuController(
            current = AppSection.Editor,
            canOpenReader = canOpenReader,
            onNavigate = onNavigateGlobal,
        ),
        content = content,
    )
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
