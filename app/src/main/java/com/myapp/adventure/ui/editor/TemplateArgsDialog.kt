package com.myapp.adventure.ui.editor

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorTemplate
import com.myapp.adventure.data.VarKind

/**
 * Ф4.3.2b: argument-entry dialog — "fill in `{paramName}` values".
 *
 * **C2 decision** — one `EditText` per parameter, with a type-driven
 * hint under the field. `ItemCount` and `Bool` get example names
 * (`potion`, `has_sword`); `Integer` and the numeric ranges say "name or
 * number" (`addStat` templates accept both). Not one big text field
 * with `key=value` pairs — the author would typo the parameter name and
 * get `applyTemplate`'s `IllegalArgumentException` on apply, with no
 * actionable message.
 *
 * **Validation.** The positive button ("Сохранить") is **disabled** until
 * every field is non-blank. `args` is a `Map<String, String>` (B1 —
 * names only, no int-substitution). Empty / whitespace fields are
 * rejected before `onApply` runs; the caller never sees a partial args
 * map.
 *
 * **No preview.** D1 — `applyTemplate` runs and inserts the choice; the
 * author sees the expanded text / condition / effects in the scene
 * card. A preview dialog (showing "Use potion" / "Use sword" side by
 * side) is deferred to a future phase if authors ask; it would be
 * cheap to add as a `TextView` above the fields (the same `applyTemplate`
 * already runs on each keystroke for validation).
 *
 * **Layout.** `item_template_arg_row.xml` — dedicated row (label +
 * field), not a reuse of `item_editor_tag` (that layout carries tag
 * semantics: name + value + category spinner + visibility checkbox;
 * extracting a subset via `removeAllViews()` was a false economy).
 */
object TemplateArgsDialog {

    /**
     * @param context used to build the dialog.
     * @param template the template being applied; `template.parameters`
     *   drives the form.
     * @param onApply invoked with the complete `Map<String, String>`
     *   (parameter name → value). The dialog closes first.
     */
    fun show(
        context: Context,
        template: EditorTemplate,
        onApply: (Map<String, String>) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_template_args, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.container_template_args)

        val fields = mutableListOf<Pair<String, EditText>>()
        val inflater = LayoutInflater.from(context)

        for (param in template.parameters) {
            val row = inflater.inflate(
                R.layout.item_template_arg_row,
                container,
                false,
            ) as LinearLayout
            val label = row.findViewById<TextView>(R.id.tv_arg_label)
            val edit = row.findViewById<EditText>(R.id.et_arg_value)
            label.text = context.getString(
                R.string.editor_template_args_label_fmt,
                param.name,
                // Ф4.3.2b: `VarKind.displayNameRes` is @StringRes (3b-6g
                // moved the display strings to `strings.xml`, e.g.
                // `var_kind_bool`). `getString` resolves them.
                context.getString(param.kind.displayNameRes),
            )
            edit.hint = hintFor(context, param.kind)
            container.addView(row)
            fields.add(param.name to edit)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(
                context.getString(
                    R.string.editor_template_args_title_fmt,
                    template.text.ifBlank {
                        context.getString(R.string.editor_template_picker_no_text)
                    },
                )
            )
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_save, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = allFilled(fields)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }
        fields.forEach { (_, edit) -> edit.addTextChangedListener(watcher) }

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = allFilled(fields)
            positive.setOnClickListener {
                if (!allFilled(fields)) {
                    Toast.makeText(context, R.string.editor_template_args_missing, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val args = fields.associate { (name, edit) -> name to edit.text.toString().trim() }
                dialog.dismiss()
                onApply(args)
            }
        }

        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        dialog.show()

        fields.firstOrNull()?.second?.requestFocus()
    }

    private fun allFilled(fields: List<Pair<String, EditText>>): Boolean =
        fields.all { (_, edit) -> edit.text.toString().trim().isNotEmpty() }

    private fun hintFor(context: Context, kind: VarKind): String = context.getString(
        when (kind) {
            VarKind.ITEM_COUNT -> R.string.editor_template_args_hint_item_count
            VarKind.BOOL -> R.string.editor_template_args_hint_bool
            VarKind.INTEGER, VarKind.INT_MIN, VarKind.INT_RANGE ->
                R.string.editor_template_args_hint_integer
            VarKind.ITEM_LIST -> R.string.editor_template_args_hint_default
        }
    )
}