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
 * Phase 3.1c-ii: initial-state section as one RecyclerView item whose content
 * is Compose. Same `ComposeViewHolder` pattern as `VariablesAdapter`.
 */
class InitialStateAdapter(
    private val viewModel: AdventureEditorViewModel,
    private val onDirty: (structural: Boolean) -> Unit,
) : RecyclerView.Adapter<InitialStateAdapter.VH>() {

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
                InitialStateSection(
                    state = state,
                    onToggleExpanded = {
                        viewModel.updateInitialStateExpanded(!state.initialStateExpanded)
                    },
                    onAddTag = {
                        viewModel.addInitialStateTag()
                        onDirty(true)
                    },
                    onRemoveTag = { index ->
                        viewModel.removeInitialStateTag(index)
                        onDirty(true)
                    },
                    onTagNameChanged = { index, name ->
                        viewModel.updateInitialStateTagName(index, name)
                        onDirty(false)
                    },
                    onTagValueChanged = { index, value ->
                        viewModel.updateInitialStateTagValue(index, value)
                        onDirty(false)
                    },
                    onTagDisplayChanged = { index, display ->
                        viewModel.updateInitialStateTagDisplay(index, display)
                        onDirty(false)
                    },
                    onTagCategoryChanged = { index, category ->
                        viewModel.updateInitialStateTagCategory(index, category)
                        onDirty(false)
                    },
                    onTagVisibleChanged = { index, visible ->
                        viewModel.updateInitialStateTagVisible(index, visible)
                        onDirty(false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}