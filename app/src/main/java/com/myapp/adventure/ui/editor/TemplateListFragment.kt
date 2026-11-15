package com.myapp.adventure.ui.editor

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTemplate
import com.myapp.adventure.data.EditorVarDecl
import com.myapp.adventure.data.VarKind
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.databinding.FragmentTemplateListBinding

/**
 * Ф4.3.2a: template list screen.
 *
 * **A2 decision** — a separate `Fragment` (not an inline section in the
 * editor's `ConcatAdapter`). Templates have a shape similar to scenes
 * (text + conditions + parameters + effects + next-scene), but inlining
 * them into the editor makes the `ConcatAdapter` four sections deep and
 * pushes `jumpToScene`'s offset arithmetic further. A dedicated screen
 * mirrors `AdventureMapFragment` (a focused list with a back button) and
 * keeps the editor's scroll state untouched while the author edits
 * templates.
 *
 * **B1** — opened from the "📦 Шаблоны" toolbar button of
 * `AdventureEditorFragment`.
 *
 * **C2** — the list shows compact rows; click a row to open the edit
 * dialog. Template deletion asks for confirmation.
 *
 * **D2** — one edit dialog with all fields, parameters and effects as
 * sub-lists. Parameters reuse `item_editor_variable.xml` (same layout as
 * `VariablesAdapter`); effects reuse `renderTagRow` from `EditorHelpers`.
 *
 * **E2** — next-scene is picked via `ScenePickerDialog` (search + `@self`
 * + create-new-scene), not a `Spinner`. Chosen because a `Spinner` with
 * 500 scenes is unusable.
 *
 * **F2** — "Сохранить" is disabled until `id`, `text`, and `nextSceneId`
 * are all non-empty, **and** `id` matches `[a-z0-9_]+` (Ф4.3.2a-fix1,
 * problem 3).
 *
 * **Config-change survival (Ф4.3.2a-fix1, problem 1).** The working copy
 * of the open edit dialog lives in `viewModel.editingTemplate`.
 * `ViewModel` survives rotation; `AlertDialog` does not. In
 * `onViewCreated`, if `editingTemplate != null`, the dialog is
 * re-created with all unsaved fields intact. `editingTemplate` is
 * cleared on "Сохранить" / "Отмена" / system back — **not** on
 * config change (that is what makes the restore work).
 */
class TemplateListFragment : Fragment() {

    private var _binding: FragmentTemplateListBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdventureEditorViewModel
    private lateinit var adapter: TemplateListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(AdventureEditorViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTemplateListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val topInset = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars()
            ).top
            v.setPadding(0, topInset, 0, 0)
            insets
        }

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnAddTemplate.setOnClickListener {
            openEditDialogForNew()
        }

        binding.rvTemplates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTemplates.setItemViewCacheSize(10)

        refreshList()

        // Ф4.3.2a-fix1 (problem 1): if a dialog was open before a config
        // change, `viewModel.editingTemplate` still holds the working copy.
        // Re-create the dialog with all unsaved fields intact.
        if (viewModel.editingTemplate != null) {
            showEditDialogInternal()
        }
    }

    private fun refreshList() {
        if (_binding == null) return
        adapter = TemplateListAdapter(
            templates = viewModel.templates.toList(),
            onTemplateClick = { t -> openEditDialogForExisting(t) },
            onTemplateDelete = { t -> confirmDelete(t) },
        )
        binding.rvTemplates.adapter = adapter
    }

    private fun confirmDelete(template: EditorTemplate) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.template_delete_title)
            .setMessage(
                getString(
                    R.string.template_delete_message_fmt,
                    template.id.ifEmpty { getString(R.string.template_no_id) }
                )
            )
            .setPositiveButton(R.string.dialog_delete) { _, _ ->
                val idx = viewModel.templates.indexOfFirst { it.uid == template.uid }
                if (idx >= 0) {
                    viewModel.templates.removeAt(idx)
                    refreshList()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------- Dialog open paths ----------

    private fun openEditDialogForNew() {
        viewModel.editingTemplate = EditorTemplate()
        showEditDialogInternal()
    }

    private fun openEditDialogForExisting(existing: EditorTemplate) {
        viewModel.editingTemplate = existing.deepCopy()
        showEditDialogInternal()
    }

    /**
     * Builds and shows the edit dialog from `viewModel.editingTemplate`.
     *
     * Called from: (a) "new" / "edit" buttons, after `editingTemplate` is
     * set; (b) `onViewCreated` after a config change, with
     * `editingTemplate` already set (restore path).
     *
     * The dialog writes into `viewModel.editingTemplate` directly. This
     * is intentional: on rotation, the Activity is destroyed, the dialog
     * disappears, but `editingTemplate` (in `ViewModel`) survives — and
     * `onViewCreated` re-enters here.
     */
    private fun showEditDialogInternal() {
        val working = viewModel.editingTemplate ?: return
        val isEditMode = viewModel.templates.any { it.uid == working.uid }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_template, null)

        val etId = dialogView.findViewById<EditText>(R.id.et_template_id)
        val tvIdHint = dialogView.findViewById<TextView>(R.id.tv_template_id_hint)
        val etText = dialogView.findViewById<EditText>(R.id.et_template_text)
        val etVisibility = dialogView.findViewById<EditText>(R.id.et_template_visibility_condition)
        val etAvailability = dialogView.findViewById<EditText>(R.id.et_template_availability_condition)
        val tvNextScene = dialogView.findViewById<TextView>(R.id.tv_template_next_scene)
        val btnPickNextScene = dialogView.findViewById<Button>(R.id.btn_pick_next_scene)
        val containerParams = dialogView.findViewById<LinearLayout>(R.id.container_template_params)
        val btnAddParam = dialogView.findViewById<Button>(R.id.btn_add_template_param)
        val containerEffects = dialogView.findViewById<LinearLayout>(R.id.container_template_effects)
        val btnAddEffect = dialogView.findViewById<Button>(R.id.btn_add_template_effect)

        etId.setText(working.id)
        etText.setText(working.text)
        etVisibility.setText(working.visibilityCondition)
        etAvailability.setText(working.availabilityCondition)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (isEditMode) R.string.template_edit_title else R.string.template_new_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_save, null)
            .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                // Explicit cancel: user does not want this template.
                viewModel.editingTemplate = null
            }
            .create()

        val idRegex = Regex("[a-z0-9_]+")

        fun updateNextSceneLabel() {
            val id = working.nextSceneId
            tvNextScene.text = when {
                id.isEmpty() -> getString(R.string.template_next_scene_unset)
                id == "@self" -> getString(R.string.template_next_scene_self)
                else -> {
                    val scene = viewModel.scenes.firstOrNull { it.id == id }
                    if (scene != null) {
                        getString(
                            R.string.template_next_scene_resolved_fmt,
                            scene.title.ifBlank { getString(R.string.label_without_title) },
                            id
                        )
                    } else {
                        getString(R.string.template_next_scene_missing_fmt, id)
                    }
                }
            }
            tvNextScene.setTextColor(
                when {
                    id.isEmpty() -> 0xFFFF9800.toInt()
                    id == "@self" -> 0xFFFFC107.toInt()
                    viewModel.scenes.any { it.id == id } -> 0xFFFFFFFF.toInt()
                    else -> 0xFFF44336.toInt()
                }
            )
        }

        fun updateIdHint() {
            val id = working.id
            val ok = id.isNotEmpty() && idRegex.matches(id)
            if (id.isEmpty()) {
                tvIdHint.text = getString(R.string.template_id_hint_default)
                tvIdHint.setTextColor(0xFF9E9E9E.toInt())
            } else if (ok) {
                tvIdHint.text = "✓"
                tvIdHint.setTextColor(0xFF4CAF50.toInt())
            } else {
                tvIdHint.text = getString(R.string.template_id_hint_invalid)
                tvIdHint.setTextColor(0xFFF44336.toInt())
            }
        }

        fun refreshParams() {
            containerParams.removeAllViews()
            for ((idx, decl) in working.parameters.withIndex()) {
                renderParamRow(
                    container = containerParams,
                    decl = decl,
                    onChanged = { /* no-op; fields write directly into decl */ },
                    onDelete = {
                        working.parameters.removeAt(idx)
                        refreshParams()
                    },
                )
            }
        }

        fun refreshEffects() {
            containerEffects.removeAllViews()
            for ((idx, effect) in working.effects.withIndex()) {
                renderTagRow(
                    container = containerEffects,
                    tag = effect,
                    onChanged = { /* no-op; fields write directly into effect */ },
                    onDelete = {
                        working.effects.removeAt(idx)
                        refreshEffects()
                    },
                )
            }
        }

        fun validate(): Boolean {
            val idOk = working.id.isNotEmpty() && idRegex.matches(working.id)
            val textOk = working.text.trim().isNotEmpty()
            val nextOk = working.nextSceneId.isNotEmpty()
            return idOk && textOk && nextOk
        }

        fun bindFieldsToWorkingCopy() {
            working.id = etId.text.toString().trim()
            working.text = etText.text.toString()
            working.visibilityCondition = etVisibility.text.toString().trim()
            working.availabilityCondition = etAvailability.text.toString().trim()
        }

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                bindFieldsToWorkingCopy()
                updateIdHint()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validate()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }
        etId.addTextChangedListener(watcher)
        etText.addTextChangedListener(watcher)
        etVisibility.addTextChangedListener(watcher)
        etAvailability.addTextChangedListener(watcher)

        btnPickNextScene.setOnClickListener {
            bindFieldsToWorkingCopy()
            ScenePickerDialog.show(
                context = requireContext(),
                scenes = viewModel.scenes,
                initialSceneId = working.nextSceneId,
                allowSelf = true,
                onPicked = { picked ->
                    working.nextSceneId = picked
                    updateNextSceneLabel()
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validate()
                },
                onCreateNew = { title ->
                    val finalTitle = title.ifBlank {
                        getString(R.string.editor_new_scene_default_title_fmt, viewModel.scenes.size + 1)
                    }
                    val newScene: EditorScene = viewModel.addSceneWithTitle(finalTitle)
                    working.nextSceneId = newScene.id
                    updateNextSceneLabel()
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validate()
                },
            )
        }

        btnAddParam.setOnClickListener {
            working.parameters.add(EditorVarDecl())
            refreshParams()
        }

        btnAddEffect.setOnClickListener {
            working.effects.add(
                com.myapp.adventure.data.EditorTag(
                    category = com.myapp.adventure.data.TagCategory.ITEM,
                    value = 1,
                )
            )
            refreshEffects()
        }

        updateNextSceneLabel()
        updateIdHint()
        refreshParams()
        refreshEffects()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = validate()
            positive.setOnClickListener {
                bindFieldsToWorkingCopy()
                if (!validate()) return@setOnClickListener
                val idx = viewModel.templates.indexOfFirst { it.uid == working.uid }
                if (idx >= 0) {
                    viewModel.templates[idx] = working
                } else {
                    viewModel.templates.add(working)
                }
                viewModel.editingTemplate = null
                refreshList()
                Toast.makeText(requireContext(), R.string.template_saved_toast, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        // System back = cancel: user abandons the template.
        dialog.setOnCancelListener {
            viewModel.editingTemplate = null
        }

        dialog.show()
    }

    /**
     * Renders one parameter row using `item_editor_variable.xml`.
     *
     * The row has: name (EditText), type (Spinner with all `VarKind`s),
     * min/max (EditTexts, visible only when the chosen kind requires
     * them), delete button. Values are written directly into `decl`
     * (which is part of the working copy of the dialog); `onChanged` is
     * currently a no-op because the dialog's save-button validation does
     * not depend on parameter contents (a template with zero parameters
     * is valid; a parameter with an empty name will be skipped by
     * `buildChoiceTemplatesDto`).
     *
     * Not in `EditorHelpers.kt` because the binding logic needs
     * `VarKind` + `needsMin` / `needsMax`, which are editor-model
     * concepts used only here and in `VariablesAdapter`; moving it would
     * duplicate `bindVariable`'s body rather than share it.
     */
    private fun renderParamRow(
        container: LinearLayout,
        decl: EditorVarDecl,
        onChanged: () -> Unit,
        onDelete: () -> Unit,
    ) {
        val context = container.context
        val rowView = LayoutInflater.from(context)
            .inflate(R.layout.item_editor_variable, container, false)
        val etVarName = rowView.findViewById<EditText>(R.id.et_var_name)
        val btnDelete = rowView.findViewById<View>(R.id.btn_delete_variable)
        val spinnerVarKind = rowView.findViewById<android.widget.Spinner>(R.id.spinner_var_kind)
        val etVarMin = rowView.findViewById<EditText>(R.id.et_var_min)
        val etVarMax = rowView.findViewById<EditText>(R.id.et_var_max)

        etVarName.setTextIfDifferentSilently(decl.name)
        etVarName.bindAfterTextChanged { newName ->
            decl.name = newName
            onChanged()
        }

        val kinds = VarKind.values().toList()
        val display = kinds.map { context.getString(it.displayNameRes) }
        val kindAdapter = android.widget.ArrayAdapter(
            context, android.R.layout.simple_spinner_item, display
        )
        kindAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerVarKind.onItemSelectedListener = null
        spinnerVarKind.adapter = kindAdapter
        val currentIdx = kinds.indexOf(decl.kind).coerceAtLeast(0)
        spinnerVarKind.setSelection(currentIdx)

        fun applyVisibility(kind: VarKind) {
            etVarMin.visibility = if (kind.needsMin) View.VISIBLE else View.GONE
            etVarMax.visibility = if (kind.needsMax) View.VISIBLE else View.GONE
        }
        applyVisibility(decl.kind)

        spinnerVarKind.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val newKind = kinds.getOrNull(position) ?: return
                    if (decl.kind != newKind) {
                        decl.kind = newKind
                        applyVisibility(newKind)
                        onChanged()
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        etVarMin.setTextIfDifferentSilently(decl.min?.toString() ?: "")
        etVarMax.setTextIfDifferentSilently(decl.max?.toString() ?: "")
        etVarMin.bindAfterTextChanged { s ->
            decl.min = s.trim().toIntOrNull()
            onChanged()
        }
        etVarMax.bindAfterTextChanged { s ->
            decl.max = s.trim().toIntOrNull()
            onChanged()
        }

        btnDelete.setOnClickListener { onDelete() }

        container.addView(rowView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}