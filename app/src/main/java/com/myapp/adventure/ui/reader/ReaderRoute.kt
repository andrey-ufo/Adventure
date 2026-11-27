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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myapp.adventure.R
import com.myapp.adventure.engine.analyze.LintIssue
import com.myapp.adventure.service.StepCounterConstants
import com.myapp.adventure.ui.AppSection
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
    onNavigateGlobal: (AppSection) -> Unit,
    canOpenReader: Boolean,
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

    // Init failures that cannot continue: log here; the user sees a
    // blocking, scrollable dialog below (not a one-shot Toast that clips
    // long subroutine messages and disappears before it can be read).
    LaunchedEffect(initResult) {
        when (val result = initResult) {
            ReaderInitResult.NoAdventureData ->
                Logger.log(context, "Reader init: no adventure data")
            is ReaderInitResult.ParseError ->
                Logger.log(context, "Parse error: ${result.messages.joinToString("; ")}")
            is ReaderInitResult.CompileError ->
                Logger.log(context, "Compile error: ${result.messages.joinToString("; ")}")
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
            val s = viewModel.state.value
            ReaderLifecycle.handle(
                event = event,
                shouldFlush = !s.previewMode && s.adventureFileUri != null,
                save = viewModel::saveProgress,
                flush = {
                    viewModel.viewModelScope.launch { viewModel.flushProgress() }
                },
            )
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Blocking init-error dialogs. They stay on screen until the author
    // closes them, so a long subroutine/compile message is fully readable.
    when (initResult) {
        ReaderInitResult.NoAdventureData -> BlockingErrorsDialog(
            titleRes = R.string.reader_broken_dialog_title,
            intro = stringResource(R.string.reader_no_data_dialog_intro),
            messages = emptyList(),
            onClose = onExit,
        )
        is ReaderInitResult.ParseError -> BlockingErrorsDialog(
            titleRes = R.string.reader_broken_dialog_title,
            intro = stringResource(
                R.string.reader_parse_error_dialog_intro_fmt,
                initResult.messages.size,
            ),
            messages = initResult.messages,
            maxItems = initResult.messages.size,
            onClose = onExit,
        )
        is ReaderInitResult.CompileError -> BlockingErrorsDialog(
            titleRes = R.string.reader_broken_dialog_title,
            intro = stringResource(
                R.string.reader_compile_error_dialog_intro_fmt,
                initResult.messages.size,
            ),
            messages = initResult.messages,
            maxItems = initResult.messages.size,
            onClose = onExit,
        )
        is ReaderInitResult.ProgressError -> BlockingErrorsDialog(
            titleRes = R.string.reader_progress_error_title,
            intro = stringResource(R.string.reader_progress_error_intro),
            messages = listOf(initResult.message),
            onClose = onExit,
        )
        is ReaderInitResult.TypeErrors -> BlockingErrorsDialog(
            titleRes = R.string.reader_broken_dialog_title,
            intro = stringResource(
                R.string.reader_type_errors_dialog_intro_fmt,
                initResult.errors.size,
            ),
            messages = initResult.errors.map { it.message },
            onClose = onExit,
        )
        is ReaderInitResult.BrokenAdventure -> BlockingErrorsDialog(
            titleRes = R.string.reader_broken_dialog_title,
            intro = stringResource(
                R.string.reader_broken_dialog_intro_fmt,
                initResult.errors.size,
            ),
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
        onNavigateGlobal = onNavigateGlobal,
        canOpenReader = canOpenReader,
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
    intro: String,
    messages: List<String>,
    onClose: () -> Unit,
    maxItems: Int = MAX_ITEMS_SHOWN,
) {
    val more = if (messages.size > maxItems) {
        stringResource(R.string.reader_dialog_more_fmt, messages.size - maxItems)
    } else {
        ""
    }
    val body = buildString {
        append(intro)
        messages.take(maxItems).forEach { append("• $it\n\n") }
        append(more)
    }

    AlertDialog(
        onDismissRequest = { /* Blocking: same as setCancelable(false). */ },
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(body)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.dialog_close))
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
