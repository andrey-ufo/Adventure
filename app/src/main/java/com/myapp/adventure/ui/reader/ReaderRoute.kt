package com.myapp.adventure.ui.reader

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myapp.adventure.R
import com.myapp.adventure.engine.analyze.LintIssue
import com.myapp.adventure.service.StepCounterConstants
import com.myapp.adventure.utils.Logger
import kotlinx.coroutines.launch

private const val MAX_ITEMS_SHOWN = 5

/**
 * Phase 4-a: the reader as a self-contained route (host logic included).
 *
 * Everything that used to live in the old reader Fragment now happens
 * here, so `AppNavHost` can call this composable directly from the reader
 * destination:
 *  - one-shot `viewModel.init(arguments)` (the VM is supplied by
 *    `AppNavHost` and scoped to the reader NavBackStackEntry);
 *  - blocking init-error dialogs and error toasts/pop;
 *  - startup warning dialog ("Продолжить игру" / "Выйти из игры");
 *  - step-counter receiver registration for the route's lifetime;
 *  - progress save on pause and flush on stop;
 *  - permission checks and `tryCatchUpGate()`.
 *
 * **Init is synchronous** (same as the old `Fragment.onCreate`): it runs
 * inside `remember` before the first state collection so the reader never
 * renders an empty frame. `state.adventure == null` skips `init` when the
 * route is recreated (rotation, return from Menu) with the same VM.
 */
@Composable
fun ReaderRoute(
    viewModel: AdventureReaderViewModel,
    arguments: Bundle?,
    onExit: () -> Unit,
    onNavigateToMenu: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val context = LocalContext.current

    val initResult = remember(viewModel) {
        if (viewModel.state.value.adventure == null) {
            viewModel.init(arguments)
        } else {
            null
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Evaluated once, right after init, so the warning dialog appears on the
    // first frame (the old View dialog was shown synchronously in
    // onViewCreated). rememberSaveable still wins on rotation.
    val shouldWarnNow = remember(viewModel) {
        viewModel.state.value.adventure != null && viewModel.shouldShowWarningDialog()
    }
    var showWarning by rememberSaveable { mutableStateOf(shouldWarnNow) }

    // One-shot startup work: permissions, gate catch-up, warning dialog.
    LaunchedEffect(viewModel) {
        if (viewModel.state.value.adventure == null) return@LaunchedEffect
        try {
            if (!viewModel.state.value.previewMode) {
                checkReaderPermissions(context)
            }
            viewModel.tryCatchUpGate()
            if (viewModel.shouldShowWarningDialog()) {
                showWarning = true
            }
        } catch (e: Exception) {
            Logger.log(context, "Error starting reader: ${e.message}")
            toastLong(context, R.string.reader_error_launch_fmt, e.message)
            onExit()
        }
    }

    // Init failures that cannot continue: toast + pop (dialogs are below).
    LaunchedEffect(initResult) {
        when (val result = initResult) {
            ReaderInitResult.NoAdventureData -> {
                Toast.makeText(
                    context,
                    R.string.reader_error_no_adventure_data,
                    Toast.LENGTH_SHORT,
                ).show()
                onExit()
            }
            is ReaderInitResult.ParseError -> {
                Logger.log(context, "Parse error: ${result.message}")
                toastLong(context, R.string.reader_error_parse_fmt, result.message)
                onExit()
            }
            is ReaderInitResult.CompileError -> {
                Logger.log(context, "Compile error: ${result.message}")
                toastLong(context, R.string.reader_error_compile_fmt, result.message)
                onExit()
            }
            is ReaderInitResult.TypeErrors -> {
                Logger.log(
                    context,
                    "Type error: ${result.errors.firstOrNull()?.message} " +
                        "(${result.errors.size} total)",
                )
            }
            else -> Unit
        }
    }

    // Step receiver: registered while the route is active, like
    // onViewCreated/onDestroyView. Preview mode never registers it.
    DisposableEffect(viewModel, state.previewMode, state.adventure != null) {
        if (state.previewMode || state.adventure == null) {
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val absoluteSteps = intent?.getIntExtra(
                        StepCounterConstants.EXTRA_ABSOLUTE_STEPS, 0
                    ) ?: 0
                    viewModel.onStepsUpdate(absoluteSteps)
                }
            }
            val registered = try {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(StepCounterConstants.ACTION_UPDATE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                true
            } catch (e: Exception) {
                Logger.log(context, "Error registering step receiver: ${e.message}")
                toastLong(context, R.string.reader_error_launch_fmt, e.message)
                onExit()
                false
            }
            onDispose {
                if (registered) {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                        // Receiver not registered — ignore.
                    }
                }
            }
        }
    }

    // Progress persistence: mirrors the old onPause/onStop. The flush uses
    // the ViewModel scope so it survives view destruction (the old code
    // used the Fragment's lifecycleScope).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.saveProgress()
                Lifecycle.Event.ON_STOP -> {
                    val s = viewModel.state.value
                    if (!s.previewMode && s.adventureFileUri != null) {
                        viewModel.viewModelScope.launch { viewModel.flushProgress() }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Blocking init-error dialogs (replaces the old View AlertDialogs).
    when (initResult) {
        is ReaderInitResult.TypeErrors -> BlockingErrorsDialog(
            titleRes = R.string.reader_broken_dialog_title,
            introRes = R.string.reader_type_errors_dialog_intro_fmt,
            messages = initResult.errors.map { it.message },
            onClose = onExit,
        )
        is ReaderInitResult.BrokenAdventure -> BlockingErrorsDialog(
            titleRes = R.string.reader_broken_dialog_title,
            introRes = R.string.reader_broken_dialog_intro_fmt,
            messages = initResult.errors.map { it.message },
            onClose = onExit,
        )
        else -> Unit
    }

    if (showWarning) {
        StartupWarningDialog(
            warnings = viewModel.getCachedWarnings(),
            onContinue = {
                viewModel.acknowledgeWarnings()
                showWarning = false
            },
            onExit = {
                showWarning = false
                onExit()
            },
        )
    }

    if (state.adventure == null) {
        // Init failed: a toast/pop or a blocking dialog is in flight.
        return
    }

    AdventureReaderScreen(
        viewModel = viewModel,
        onPreviewExit = onExit,
        onNavigateToMenu = onNavigateToMenu,
        onNavigateToSettings = onNavigateToSettings,
        onChoiceClick = { choice -> viewModel.onChoicePicked(choice) },
        onStepBackClick = { viewModel.onStepBack() },
        onChapterRestartClick = { viewModel.onChapterRestart() },
        onAdventureRestartClick = { viewModel.onAdventureRestart() },
        onMenuRestartClick = { viewModel.onRestartAdventure() },
        onEndRestartClick = {
            if (viewModel.state.value.previewMode) {
                onExit()
            } else {
                viewModel.onRestartAdventure()
            }
        },
    )
}

// =====================================================================
// Blocking / startup dialogs (were View AlertDialogs until Phase 4-a)
// =====================================================================

@Composable
private fun BlockingErrorsDialog(
    @StringRes titleRes: Int,
    @StringRes introRes: Int,
    messages: List<String>,
    onClose: () -> Unit,
) {
    val intro = stringResource(introRes, messages.size)
    val more = if (messages.size > MAX_ITEMS_SHOWN) {
        stringResource(R.string.reader_dialog_more_fmt, messages.size - MAX_ITEMS_SHOWN)
    } else {
        ""
    }
    val body = buildString {
        append(intro)
        messages.take(MAX_ITEMS_SHOWN).forEach { append("• $it\n") }
        append(more)
    }

    AlertDialog(
        onDismissRequest = { /* Blocking: same as setCancelable(false). */ },
        title = { Text(stringResource(titleRes)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
    )
}

@Composable
private fun StartupWarningDialog(
    warnings: List<LintIssue>,
    onContinue: () -> Unit,
    onExit: () -> Unit,
) {
    val intro = stringResource(R.string.reader_warning_dialog_intro_fmt, warnings.size)
    val more = if (warnings.size > MAX_ITEMS_SHOWN) {
        stringResource(R.string.reader_dialog_more_fmt, warnings.size - MAX_ITEMS_SHOWN)
    } else {
        ""
    }
    val prompt = stringResource(R.string.reader_warning_dialog_prompt)
    val body = buildString {
        append(intro)
        warnings.take(MAX_ITEMS_SHOWN).forEach { append("• ${it.message}\n") }
        append(more)
        append(prompt)
    }

    AlertDialog(
        onDismissRequest = onExit,
        title = { Text(stringResource(R.string.reader_warning_dialog_title)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.reader_warning_dialog_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text(stringResource(R.string.reader_warning_dialog_exit))
            }
        },
    )
}

// =====================================================================
// Non-composable helpers
// =====================================================================

/**
 * `getString` is deliberately kept out of the composable body: the Compose
 * lint check `LocalContextGetResourceValueCall` treats `LocalContext.current`
 * resource reads as configuration-unaware. Toast errors are one-shot, so a
 * non-composable helper is the simplest correct boundary.
 */
private fun toastLong(context: Context, @StringRes resId: Int, vararg args: Any?) {
    Toast.makeText(context, context.getString(resId, *args), Toast.LENGTH_LONG).show()
}

// =====================================================================
// Permissions
// =====================================================================

private fun checkReaderPermissions(context: Context) {
    val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    val activityGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    if (!notificationGranted) {
        Toast.makeText(context, R.string.reader_perm_no_notifications, Toast.LENGTH_LONG).show()
    }
    if (!activityGranted) {
        Toast.makeText(
            context,
            R.string.reader_perm_no_activity_recognition,
            Toast.LENGTH_LONG,
        ).show()
    }
}
