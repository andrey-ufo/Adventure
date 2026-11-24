package com.myapp.adventure.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R

/**
 * One row of a HUD list. `value == null` → label only (journal);
 * `value != null` → "label ×N" (health, inventory).
 */
data class HudListItem(val label: String, val value: Int?)

/**
 * Phase 3.4-c: read-only HUD list (health / inventory / journal) rendered
 * as a Compose overlay inside [AdventureReaderScreen]. Replaces the
 * View-based `dialog_item_list.xml` + `showListDialog` in the Fragment.
 *
 * The caller controls visibility; this composable is stateless.
 */
@Composable
fun HudListScreen(
    titleRes: Int,
    items: List<HudListItem>,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        ReaderOverlayHeader(
            title = stringResource(titleRes),
            onBack = onBack,
        )

        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.label_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
            ) {
                items(items) { item ->
                    HudListRow(item)
                }
            }
        }
    }
}

@Composable
private fun HudListRow(item: HudListItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (item.value != null) {
            Text(
                text = "×${item.value}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}