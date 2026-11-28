package com.myapp.adventure.ui.projects

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapp.adventure.R
import com.myapp.adventure.projects.ProjectRef
import com.myapp.adventure.ui.AppSection
import com.myapp.adventure.ui.GlobalMenuButton
import java.text.DateFormat
import java.util.Date

@Composable
fun ProjectManagerScreen(
    onBack: () -> Unit,
    onOpen: (ProjectRef) -> Unit,
    onDeleted: (ProjectRef) -> Unit,
    onNavigateGlobal: (AppSection) -> Unit,
    canOpenReader: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ProjectManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<ProjectRef?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectManagerEvent.Open -> onOpen(event.project)
                is ProjectManagerEvent.Deleted -> onDeleted(event.project)
                is ProjectManagerEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.projects_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            GlobalMenuButton(
                current = AppSection.Editor,
                canOpenReader = canOpenReader,
                onNavigate = onNavigateGlobal,
            )
        }

        Button(
            onClick = viewModel::createProject,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.projects_create))
        }

        if (state.isLoading) {
            Text(
                text = stringResource(R.string.projects_loading),
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else if (state.projects.isEmpty()) {
            Text(
                text = stringResource(R.string.projects_empty),
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.projects, key = { it.id }) { project ->
                    ProjectRow(
                        project = project,
                        onOpen = { onOpen(project) },
                        onDuplicate = { viewModel.duplicate(project) },
                        onDelete = { deleteTarget = project },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.projects_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.projects_delete_message,
                        target.title,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.delete(target)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.projects_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProjectRow(
    project: ProjectRef,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = project.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (project.author.isNotBlank()) {
            Text(
                text = project.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = project.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = DateFormat.getDateTimeInstance().format(Date(project.modifiedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDuplicate) {
                Text(stringResource(R.string.projects_duplicate))
            }
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.projects_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
