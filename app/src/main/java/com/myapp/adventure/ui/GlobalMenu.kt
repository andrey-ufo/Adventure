package com.myapp.adventure.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.myapp.adventure.R

enum class AppSection {
    Library,
    Editor,
    Reader,
    Statistics,
    Settings,
}

data class GlobalMenuController(
    val current: AppSection?,
    val canOpenReader: Boolean,
    val onNavigate: (AppSection) -> Unit,
)

val LocalGlobalMenu = staticCompositionLocalOf<GlobalMenuController?> { null }

/** Kebab supplied by the route scope; avoids threading callbacks through
 * every editor child signature. */
@Composable
fun GlobalMenuButton() {
    val controller = LocalGlobalMenu.current ?: return
    GlobalMenuButton(
        current = controller.current,
        canOpenReader = controller.canOpenReader,
        onNavigate = controller.onNavigate,
    )
}

/**
 * Flat global navigation. The current section is disabled instead of
 * hidden so the menu keeps a stable order on every screen.
 */
@Composable
fun GlobalMenuButton(
    current: AppSection?,
    canOpenReader: Boolean,
    onNavigate: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.action_global_menu)

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(
                text = stringResource(R.string.action_global_menu_symbol),
                fontSize = 24.sp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            GlobalMenuItem(
                label = stringResource(R.string.menu_library),
                enabled = current != AppSection.Library,
            ) {
                expanded = false
                onNavigate(AppSection.Library)
            }
            GlobalMenuItem(
                label = stringResource(R.string.menu_create),
                enabled = current != AppSection.Editor,
            ) {
                expanded = false
                onNavigate(AppSection.Editor)
            }
            GlobalMenuItem(
                label = stringResource(R.string.menu_reader_continue),
                enabled = canOpenReader && current != AppSection.Reader,
            ) {
                expanded = false
                onNavigate(AppSection.Reader)
            }
            GlobalMenuItem(
                label = stringResource(R.string.menu_statistics_steps),
                enabled = current != AppSection.Statistics,
            ) {
                expanded = false
                onNavigate(AppSection.Statistics)
            }
            GlobalMenuItem(
                label = stringResource(R.string.menu_settings),
                enabled = current != AppSection.Settings,
            ) {
                expanded = false
                onNavigate(AppSection.Settings)
            }
        }
    }
}

@Composable
private fun GlobalMenuItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        enabled = enabled,
    )
}
