package com.myapp.adventure.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.engine.analyze.LintIssue

/**
 * Phase 3.4-d: read-only warning list, a Compose overlay inside
 * [AdventureReaderScreen]. Replaces the View `showWarningInfoDialog`
 * (menu item "Предупреждения (N)").
 *
 * The startup blocking warning dialog is now a Compose `AlertDialog` owned
 * by [ReaderRoute] (Phase 4-a), not this list.
 */
@Composable
fun WarningInfoScreen(
    warnings: List<LintIssue>,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        ReaderOverlayHeader(
            title = stringResource(R.string.reader_menu_warnings_fmt, warnings.size),
            onBack = onBack,
        )

        if (warnings.isEmpty()) {
            Text(
                text = stringResource(R.string.label_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(warnings) { warning ->
                    Text(
                        text = "• ${warning.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
