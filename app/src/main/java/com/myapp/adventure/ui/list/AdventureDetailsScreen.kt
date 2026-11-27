package com.myapp.adventure.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapp.adventure.R
import com.myapp.adventure.ui.AppSection
import com.myapp.adventure.ui.GlobalMenuButton

@Composable
fun AdventureDetailsScreen(
    onBack: () -> Unit,
    onRead: () -> Unit,
    onNavigateGlobal: (AppSection) -> Unit,
    canOpenReader: Boolean,
    modifier: Modifier = Modifier,
    viewModel: AdventureDetailsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                text = stringResource(R.string.details_header),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            GlobalMenuButton(
                current = null,
                canOpenReader = canOpenReader,
                onNavigate = onNavigateGlobal,
            )
        }

        when {
            state.isLoading -> Text(
                text = stringResource(R.string.details_loading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp),
            )

            state.error || state.adventure == null -> {
                Text(
                    text = stringResource(R.string.details_error),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                TextButton(onClick = viewModel::load) {
                    Text(stringResource(R.string.details_retry))
                }
            }

            else -> {
                val adventure = requireNotNull(state.adventure)
                Text(
                    text = adventure.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (adventure.author.isNotBlank()) {
                    Text(
                        text = adventure.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (adventure.description.isNotBlank()) {
                    Text(
                        text = adventure.description,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.details_meta_fmt,
                        adventure.chapters.size,
                        adventure.chapters.sumOf { it.scenes.size },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(
                    onClick = onRead,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.details_read))
                }
            }
        }
    }
}
