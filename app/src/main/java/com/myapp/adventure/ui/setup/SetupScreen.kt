package com.myapp.adventure.ui.setup

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapp.adventure.R

@Composable
fun SetupScreen(
    onNavigateToList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = viewModel(),
) {
    val context = LocalContext.current
    val step by viewModel.step.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> viewModel.onFolderSelected(uri) }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context, R.string.setup_toast_notifications_disabled, Toast.LENGTH_LONG
            ).show()
        }
        viewModel.onNotificationsResolved()
    }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context, R.string.setup_toast_activity_denied, Toast.LENGTH_LONG
            ).show()
        }
        viewModel.onActivityResolved(activityRecognitionGranted = granted)
    }

    LaunchedEffect(Unit) {
        viewModel.initialize()
        viewModel.events.collect { event ->
            when (event) {
                is SetupEvent.NavigateToList -> onNavigateToList()
                is SetupEvent.ShowToast ->
                    Toast.makeText(
                        context,
                        event.message,
                        if (event.longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            // Render nothing while checking the saved folder. On a cold start
            // the system splash screen still covers this brief period; on a
            // warm restart the gap is a few dozen milliseconds.
            SetupStep.LOADING -> Unit
            SetupStep.FOLDER -> FolderStep(
                onSelectFolder = { folderPicker.launch(null) },
            )
            SetupStep.NOTIFICATIONS -> NotificationsStep(
                onRequest = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onSkip = { viewModel.onNotificationsResolved() },
            )
            SetupStep.ACTIVITY -> ActivityStep(
                onRequest = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                onSkip = { viewModel.onActivityResolved(activityRecognitionGranted = false) },
            )
            SetupStep.DONE -> Unit // navigation already triggered; nothing to render
        }
    }
}

@Composable
private fun FolderStep(onSelectFolder: () -> Unit) {
    Text(
        text = stringResource(R.string.setup_welcome),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    Text(
        text = stringResource(R.string.setup_instruction),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 32.dp),
    )
    Button(onClick = onSelectFolder) {
        Text(stringResource(R.string.setup_select_folder))
    }
}

@Composable
private fun NotificationsStep(onRequest: () -> Unit, onSkip: () -> Unit) {
    Text(
        text = stringResource(R.string.setup_notifications_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Text(
        text = stringResource(R.string.setup_notifications_explanation),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 24.dp),
    )
    Button(onClick = onRequest) {
        Text(stringResource(R.string.setup_notifications_button))
    }
    Text(
        text = stringResource(R.string.setup_skip_for_now),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp),
    )
    Button(onClick = onSkip) {
        Text(stringResource(R.string.setup_skip_button))
    }
}

@Composable
private fun ActivityStep(
    onRequest: () -> Unit,
    onSkip: () -> Unit,
) {
    Text(
        text = stringResource(R.string.setup_activity_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Text(
        text = stringResource(R.string.setup_activity_explanation),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    Button(onClick = onRequest) {
        Text(stringResource(R.string.setup_activity_button))
    }
    Text(
        text = stringResource(R.string.setup_skip_for_now),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp),
    )
    Button(onClick = onSkip) {
        Text(stringResource(R.string.setup_skip_button))
    }
}