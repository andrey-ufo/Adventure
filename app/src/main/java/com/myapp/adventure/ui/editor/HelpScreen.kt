package com.myapp.adventure.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R

/**
 * Ф4.8a: in-editor help written for authors, not programmers.
 *
 * The "Подпрограммы" section is intentionally example-driven: it explains
 * the concept as a reusable recipe, lists concrete gamebook mechanics it is
 * good for, shows the potion workflow step by step, and names the common
 * mistakes. No engine vocabulary (IR, scope, stack, parser) leaks into the
 * text.
 */
@Composable
fun HelpRoute(onBack: () -> Unit) {
    HelpScreen(onBack = onBack)
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.help_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { HelpSection(R.string.help_intro_title, R.string.help_intro_body) }
            item { HelpSection(R.string.help_scenes_title, R.string.help_scenes_body) }
            item { HelpSection(R.string.help_variables_title, R.string.help_variables_body) }
            item { HelpSection(R.string.help_templates_title, R.string.help_templates_body) }
            item { HelpSection(R.string.help_merge_title, R.string.help_merge_body) }

            item {
                Text(
                    text = stringResource(R.string.help_subroutines_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                )
                Text(
                    text = stringResource(R.string.help_subroutines_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item {
                HelpSection(
                    R.string.help_subroutines_when_title,
                    R.string.help_subroutines_when_body,
                )
            }
            item {
                HelpSection(
                    R.string.help_subroutines_how_title,
                    R.string.help_subroutines_how_body,
                )
            }
            item {
                HelpSection(
                    R.string.help_subroutines_example_title,
                    R.string.help_subroutines_example_body,
                )
            }
            item {
                HelpSection(
                    R.string.help_subroutines_limits_title,
                    R.string.help_subroutines_limits_body,
                )
            }
            item {
                HelpSection(
                    R.string.help_subroutines_errors_title,
                    R.string.help_subroutines_errors_body,
                )
            }
            item {
                HelpSection(
                    R.string.help_tip_title,
                    R.string.help_tip_body,
                )
            }
        }
    }
}

@Composable
private fun HelpSection(titleRes: Int, bodyRes: Int) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
