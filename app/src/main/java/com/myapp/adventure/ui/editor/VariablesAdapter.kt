package com.myapp.adventure.ui.editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.myapp.adventure.R
import com.myapp.adventure.data.VarKind
import com.myapp.adventure.databinding.ItemEditorVariableBinding
import com.myapp.adventure.databinding.ItemVariablesHeaderBinding

/**
 * Ф2.7 — адаптер для секции «📋 Переменные» в редакторе.
 *
 * Структура как у [InitialStateAdapter]: **один** header-элемент
 * (свернутый/развернутый аккордеон) + N строк переменных, если
 * `viewModel.variablesExpanded`. Позиция 0 — header, позиции 1..N —
 * переменные.
 *
 * **Сверху над InitialStateAdapter и сценами.** Семантика:
 * «сначала объяви тип, потом используй» (per Ф2.7 решение C1).
 *
 * **Что делает.** Отрисовывает [com.myapp.adventure.data.EditorVarDecl]
 * строки: имя, тип (spinner из 6 опций [VarKind]), опционально
 * `min`/`max` (видимы только когда `VarKind.needsMin` /
 * `VarKind.needsMax`), удаление. `onDirty(false)` для изменений
 * полей (не structural, не пушит undo), `onDirty(true)` для
 * add/remove (structural).
 *
 * **Что НЕ делает.**
 * - Не конвертирует в IR — это делает `AdventureEditorViewModel.buildIrAdventure()`
 *   через `EditorVarDecl.toVarType()`.
 * - Не валидирует — `StoryValidator` (Ф2.6) + fallback-warning в
 *   `showValidationDialog` (Ф2.7) это делают на следующем шаге.
 */
class VariablesAdapter(
    private val viewModel: AdventureEditorViewModel,
    private val onDirty: (structural: Boolean) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_VAR = 1
    }

    inner class HeaderViewHolder(val binding: ItemVariablesHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class VarViewHolder(val binding: ItemEditorVariableBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int =
        if (viewModel.variablesExpanded) 1 + viewModel.variables.size else 1

    override fun getItemViewType(position: Int): Int =
        if (position == 0) TYPE_HEADER else TYPE_VAR

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemVariablesHeaderBinding.inflate(inflater, parent, false))
        } else {
            VarViewHolder(ItemEditorVariableBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> bindHeader(holder)
            is VarViewHolder -> bindVariable(holder, position - 1)
        }
    }

    private fun bindHeader(holder: HeaderViewHolder) {
        val binding = holder.binding
        val context = binding.root.context
        val expanded = viewModel.variablesExpanded
        binding.varBody.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.tvVarChevron.text = if (expanded) "▼" else "▶"
        binding.tvVarSummary.text = if (viewModel.variables.isEmpty()) {
            context.getString(R.string.label_empty)
        } else {
            pluralWithNumber(context, R.plurals.word_variables, viewModel.variables.size)
        }

        binding.varHeader.setOnClickListener {
            viewModel.variablesExpanded = !viewModel.variablesExpanded
            notifyDataSetChanged()
        }

        binding.btnAddVariable.setOnClickListener {
            viewModel.addVariable()
            onDirty(true)
            notifyDataSetChanged()
        }
    }

    private fun bindVariable(holder: VarViewHolder, index: Int) {
        val decl = viewModel.variables.getOrNull(index) ?: return
        val binding = holder.binding
        val context = binding.root.context

        binding.etVarName.setTextIfDifferentSilently(decl.name)
        binding.etVarName.bindAfterTextChanged { newName ->
            decl.name = newName
            onDirty(false)
        }

        // Spinner: 6 видов VarKind
        val kinds = VarKind.values().toList()
        val displayList = kinds.map { context.getString(it.displayNameRes) }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, displayList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerVarKind.onItemSelectedListener = null
        binding.spinnerVarKind.adapter = adapter
        val currentIdx = kinds.indexOf(decl.kind).coerceAtLeast(0)
        binding.spinnerVarKind.setSelection(currentIdx)

        // min/max видимы только когда нужны
        fun applyVisibility(kind: VarKind) {
            binding.etVarMin.visibility = if (kind.needsMin) View.VISIBLE else View.GONE
            binding.etVarMax.visibility = if (kind.needsMax) View.VISIBLE else View.GONE
        }
        applyVisibility(decl.kind)

        binding.spinnerVarKind.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long,
                ) {
                    val newKind = kinds.getOrNull(position) ?: return
                    if (decl.kind != newKind) {
                        decl.kind = newKind
                        applyVisibility(newKind)
                        onDirty(false)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        // min/max значения
        binding.etVarMin.setTextIfDifferentSilently(decl.min?.toString() ?: "")
        binding.etVarMax.setTextIfDifferentSilently(decl.max?.toString() ?: "")
        binding.etVarMin.bindAfterTextChanged { s ->
            decl.min = s.trim().toIntOrNull()
            onDirty(false)
        }
        binding.etVarMax.bindAfterTextChanged { s ->
            decl.max = s.trim().toIntOrNull()
            onDirty(false)
        }

        binding.btnDeleteVariable.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val idx = pos - 1  // header is at position 0
            if (idx in viewModel.variables.indices) {
                viewModel.removeVariable(idx)
                onDirty(true)
                notifyDataSetChanged()
            }
        }
    }

    // Ф4-infra (3b-6c): plural forms live in `strings.xml`
    // (`<plurals name="word_variables">`); the local `when`-based
    // helper is deleted. `pluralWithNumber` in `EditorHelpers.kt`
    // composes "N переменных" (Russian for "N variables") consistently.
}