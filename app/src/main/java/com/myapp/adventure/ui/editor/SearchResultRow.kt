package com.myapp.adventure.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.SearchResult
import com.myapp.adventure.ui.theme.AdventureTheme

@Composable
internal fun SearchRow(result: SearchResult, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val detailText = if (result.detailArg != null) {
        stringResource(result.detailRes, result.detailArg)
    } else {
        stringResource(result.detailRes)
    }
    val moreText = if (result.totalMatchesInField > 1) {
        stringResource(R.string.editor_search_more_fmt, result.totalMatchesInField - 1)
    } else ""

    val titleLine = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
        ) {
            append(result.sceneTitle)
        }
        append("  ·  ")
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
            append(detailText)
        }
        if (moreText.isNotEmpty()) {
            withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                append(moreText)
            }
        }
    }

    val highlightBg = AdventureTheme.extendedColors.statusWarning
    val highlightFg = colorScheme.surface
    val contextLine = buildAnnotatedString {
        append("«…")
        append(result.contextBefore)
        withStyle(SpanStyle(background = highlightBg, color = highlightFg)) {
            append(result.match)
        }
        append(result.contextAfter)
        append("…»")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(
            text = result.fieldType.icon,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleLine,
                style = MaterialTheme.typography.bodyMedium,
            )
            Surface(
                color = colorScheme.surface,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = contextLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
