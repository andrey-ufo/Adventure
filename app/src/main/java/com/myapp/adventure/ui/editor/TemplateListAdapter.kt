package com.myapp.adventure.ui.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorTemplate
import com.myapp.adventure.databinding.ItemEmptyTemplateHintBinding
import com.myapp.adventure.databinding.ItemTemplateBinding

/**
 * Ф4.3.2a: adapter for the template list screen.
 *
 * Two view types: a normal template row (compact: id mono / text bold /
 * summary like "N parameters · M effects", localized via
 * `<plurals name="word_parameters">` and `<plurals name="word_effects">`)
 * and an empty-state hint when `templates` is empty. Deliberately no
 * inline edit fields — the author opens the edit dialog (see
 * `TemplateListFragment.showEditDialogInternal`) via a row click; delete
 * via the `✕` button with confirmation.
 *
 * C2 decision: compact rows scale to many templates. `EditorSceneAdapter`
 * inlines fields because there are typically 5–20 scenes; template
 * counts are also low, but the fields per template are denser
 * (parameters + effects sub-lists), so a dialog is the better host.
 */
class TemplateListAdapter(
    private val templates: List<EditorTemplate>,
    private val onTemplateClick: (EditorTemplate) -> Unit,
    private val onTemplateDelete: (EditorTemplate) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_TEMPLATE = 0
        private const val TYPE_EMPTY = 1
    }

    inner class TemplateViewHolder(val binding: ItemTemplateBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class EmptyViewHolder(val binding: ItemEmptyTemplateHintBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = if (templates.isEmpty()) 1 else templates.size

    override fun getItemViewType(position: Int): Int =
        if (templates.isEmpty()) TYPE_EMPTY else TYPE_TEMPLATE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_EMPTY) {
            EmptyViewHolder(
                ItemEmptyTemplateHintBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        } else {
            TemplateViewHolder(
                ItemTemplateBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is TemplateViewHolder) return
        val template = templates.getOrNull(position) ?: return
        val binding = holder.binding
        val ctx = binding.root.context

        binding.tvTemplateId.text = if (template.id.isNotBlank()) {
            "ID: ${template.id}"
        } else {
            ctx.getString(R.string.template_no_id)
        }
        binding.tvTemplateText.text = template.text.ifBlank {
            ctx.getString(R.string.item_editor_choice_no_text)
        }

        val paramCount = template.parameters.size
        val effectCount = template.effects.size
        binding.tvTemplateSummary.text = buildString {
            append(pluralWithNumber(ctx, R.plurals.word_parameters, paramCount))
            append(" · ")
            append(pluralWithNumber(ctx, R.plurals.word_effects, effectCount))
        }

        binding.root.setOnClickListener { onTemplateClick(template) }
        binding.btnTemplateDelete.setOnClickListener { onTemplateDelete(template) }
    }
}