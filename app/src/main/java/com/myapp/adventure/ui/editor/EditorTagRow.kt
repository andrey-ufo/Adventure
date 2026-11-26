package com.myapp.adventure.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.TagCategory

/**
 * Phase 3.1c-ii: borderless tag editor row. Mirrors `item_editor_tag.xml`
 * (two compact rows: name/value/✕, then display/category/☑). Reused in
 * 3.1c-iii for scene tags and choice tags.
 */
@Composable
fun EditorTagRow(
    tag: EditorTag,
    onNameChanged: (String) -> Unit,
    onValueChanged: (Int) -> Unit,
    onDisplayChanged: (String) -> Unit,
    onCategoryChanged: (TagCategory) -> Unit,
    onVisibleChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the raw text while the author is typing. Without this, deleting
    // "1" immediately wrote 0 back into the field; typing 5 then produced
    // "05"/"50" and "-" could not be entered. Empty / "-" / "+" are valid
    // intermediate states; the parsed value (or 0 for an empty draft) is
    // committed on every keystroke.
    var rawValue by remember { mutableStateOf(formatTagValue(tag.value)) }
    LaunchedEffect(tag.value) {
        val parsed = rawValue.toIntOrNull()
        if (parsed != null && parsed != tag.value) {
            rawValue = formatTagValue(tag.value)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BareTextField(
                value = tag.name,
                onValueChange = onNameChanged,
                placeholder = stringResource(R.string.item_editor_tag_name_hint),
                modifier = Modifier.weight(1f),
            )
            BareTextField(
                value = rawValue,
                onValueChange = { text ->
                    if (!isNumericDraft(text)) return@BareTextField
                    rawValue = text
                    onValueChanged(text.toIntOrNull() ?: 0)
                },
                placeholder = stringResource(R.string.item_editor_tag_value_hint),
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = "✕",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BareTextField(
                value = tag.displayName,
                onValueChange = onDisplayChanged,
                placeholder = stringResource(R.string.item_editor_tag_display_name_hint),
                modifier = Modifier.weight(1f),
            )
            BareDropdown(
                current = tag.category,
                items = listOf(TagCategory.HEALTH, TagCategory.ITEM, TagCategory.JOURNAL),
                label = { stringResource(categoryLabelRes(it)) },
                onSelected = onCategoryChanged,
                modifier = Modifier.width(120.dp),
            )
            Checkbox(
                checked = tag.visible,
                onCheckedChange = onVisibleChanged,
            )
        }
    }
}

@Composable
private fun BareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun TagCategoryDropdown(
    current: TagCategory,
    onSelected: (TagCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = listOf(TagCategory.HEALTH, TagCategory.ITEM, TagCategory.JOURNAL)
    Box(modifier = modifier) {
        Text(
            text = stringResource(categoryLabelRes(current)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(stringResource(categoryLabelRes(item))) },
                    onClick = {
                        expanded = false
                        if (item != current) onSelected(item)
                    },
                )
            }
        }
    }
}

private fun categoryLabelRes(cat: TagCategory): Int = when (cat) {
    TagCategory.HEALTH -> R.string.editor_tag_category_health
    TagCategory.ITEM -> R.string.editor_tag_category_item
    TagCategory.JOURNAL -> R.string.editor_tag_category_journal
}

/** Positive stat changes show a leading `+`; negative values keep `-`. */
private fun formatTagValue(value: Int): String =
    if (value > 0) "+$value" else value.toString()

/**
 * Allows an empty field and a lone sign while the author is typing. A
 * complete number (including `+5` / `-1`) commits; an incomplete draft is
 * kept in the UI without being written back as a different number.
 */
private fun isNumericDraft(text: String): Boolean =
    text.isEmpty() || text == "-" || text == "+" || text.toIntOrNull() != null