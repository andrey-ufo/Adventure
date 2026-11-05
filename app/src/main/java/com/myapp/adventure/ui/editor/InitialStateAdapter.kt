package com.myapp.adventure.ui.editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.myapp.adventure.databinding.ItemInitialStateBinding

class InitialStateAdapter(
    private val viewModel: AdventureEditorViewModel,
    private val onDirty: (structural: Boolean) -> Unit
) : RecyclerView.Adapter<InitialStateAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemInitialStateBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInitialStateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding

        val expanded = viewModel.initialStateExpanded
        binding.initialBody.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.tvInitChevron.text = if (expanded) "▼" else "▶"

        if (viewModel.initialStateTags.isEmpty()) {
            binding.tvInitSummary.text = "(пусто)"
        } else {
            binding.tvInitSummary.text = "${viewModel.initialStateTags.size} меток"
        }

        binding.initialHeader.setOnClickListener {
            viewModel.initialStateExpanded = !viewModel.initialStateExpanded
            notifyItemChanged(0)
        }

        // Строки тегов
        binding.containerInitTags.removeAllViews()
        for ((idx, tag) in viewModel.initialStateTags.withIndex()) {
            renderTagRow(
                container = binding.containerInitTags,
                tag = tag,
                onChanged = { onDirty(false) },
                onDelete = {
                    viewModel.removeInitialStateTag(idx)
                    onDirty(true)
                    notifyDataSetChanged()
                }
            )
        }

        binding.btnAddInitTag.setOnClickListener {
            viewModel.addInitialStateTag()
            onDirty(true)
            notifyDataSetChanged()
        }
    }
}