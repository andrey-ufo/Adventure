package com.myapp.adventure.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R

/**
 * Phase 3.1c-iv-a: "Edit adventure meta" as a full screen (previously a
 * `dialog_edit_meta.xml` dialog). Triggered from the editor toolbar
 * ("Название"), returns via `onSave`/`onBack`.
 *
 * Save responsibility: [onSave] receives the edited triple; the host
 * Fragment (`EditMetaFragment`) applies it to `viewModel`, commits a
 * snapshot (so undo restores the pre-edit state) and persists the draft.
 */
@Composable
fun EditMetaScreen(
    initialTitle: String,
    initialAuthor: String,
    initialDescription: String,
    onSave: (title: String, author: String, description: String) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf(initialAuthor) }
    var description by remember { mutableStateOf(initialDescription) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.editor_dialog_edit_meta_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                TextButton(
                    onClick = {
                        onSave(
                            title.trim(),
                            author.trim(),
                            description.trim(),
                        )
                    },
                ) {
                    Text(stringResource(R.string.dialog_apply))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionLabel(stringResource(R.string.dialog_edit_meta_title_hint))
            BareTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = stringResource(R.string.dialog_edit_meta_title_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.dialog_edit_meta_author_hint))
            BareTextField(
                value = author,
                onValueChange = { author = it },
                placeholder = stringResource(R.string.dialog_edit_meta_author_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.dialog_edit_meta_description_hint))
            BareTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = stringResource(R.string.dialog_edit_meta_description_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}