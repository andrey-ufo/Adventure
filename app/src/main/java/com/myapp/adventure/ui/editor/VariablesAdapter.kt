package com.myapp.adventure.ui.editor

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Phase 3.1c-i (v2): variables section as a single RecyclerView item whose
 * content is Compose. Using `ComposeView` **inside** the RecyclerView (not
 * as a sibling of it) is what makes the section scroll together with the
 * initial-state header and the scenes.
 *
 * Phase 3.1c-iii will replace this adapter together with the rest of the
 * `ConcatAdapter` by a single `LazyColumn`.
 */
class VariablesAdapter(
    private val viewModel: AdventureEditorViewModel,
    private val onDirty: (structural: Boolean) -> Unit,
) : RecyclerView.Adapter<VariablesAdapter.VH>() {

    inner class VH(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)

    override fun getItemCount(): Int = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val composeView = ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        return VH(composeView)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.composeView.setContent {
            AdventureTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                VariablesSection(
                    state = state,
                    onToggleExpanded = { viewModel.toggleVariablesExpanded() },
                    onAddVariable = {
                        viewModel.addVariable()
                        onDirty(true)
                    },
                    onRemoveVariable = { index ->
                        viewModel.removeVariable(index)
                        onDirty(true)
                    },
                    onNameChanged = { index, name ->
                        viewModel.updateVariableName(index, name)
                        onDirty(false)
                    },
                    onKindChanged = { index, kind ->
                        viewModel.updateVariableKind(index, kind)
                        onDirty(false)
                    },
                    onMinChanged = { index, min ->
                        viewModel.updateVariableMin(index, min)
                        onDirty(false)
                    },
                    onMaxChanged = { index, max ->
                        viewModel.updateVariableMax(index, max)
                        onDirty(false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}