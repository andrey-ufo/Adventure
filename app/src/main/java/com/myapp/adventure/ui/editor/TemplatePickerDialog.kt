package com.myapp.adventure.ui.editor

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorTemplate

/**
 * Ф4.3.2b: picker dialog — "which template to apply?".
 *
 * **B2 decision** — custom card layout (id mono + text bold + summary
 * "N параметров · M эффектов"). Not `AlertDialog.setItems` (label-only,
 * no visual hierarchy) and not a full `RecyclerView` (authorial template
 * count is < 10 in practice; a `ScrollView` + `LinearLayout` is lighter
 * and matches `ScenePickerDialog`'s structure).
 *
 * **Filtering.** The dialog does **not** filter by `template.nextSceneId`
 * vs the current scene. A template with `@self` targets the current
 * scene; a template with a fixed target (`"back"`) is scene-agnostic. Both
 * are legitimately applicable from any scene, so no "compatible only"
 * filter is applied. If a future requirement asks for "only templates
 * whose target exists from here", it becomes a filter predicate — one
 * parameter, ~10 lines.
 *
 * **Empty state.** The caller (adapter) is expected to check
 * `viewModel.templates.isEmpty()` before opening this dialog and show
 * `editor_template_no_templates_toast`. The `tv_template_picker_empty`
 * branch exists for the race case (template deleted between the
 * isEmpty() check and dialog show) — a graceful no-op instead of a
 * crash.
 */
object TemplatePickerDialog {

    /**
     * @param context used to build the dialog.
     * @param templates available templates. Order = creation order
     *   (matches `TemplateListFragment`).
     * @param onPicked invoked with the chosen template; the dialog
     *   closes first.
     */
    fun show(
        context: Context,
        templates: List<EditorTemplate>,
        onPicked: (EditorTemplate) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_template_picker, null)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tv_template_picker_empty)
        val container = dialogView.findViewById<LinearLayout>(R.id.container_template_picker)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.editor_template_pick_title)
            .setView(dialogView)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        if (templates.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            dialog.show()
            return
        }

        val inflater = LayoutInflater.from(context)
        for (template in templates) {
            val row = inflater.inflate(
                R.layout.item_template_picker_row,
                container,
                false,
            )
            val tvId = row.findViewById<TextView>(R.id.tv_picker_template_id)
            val tvText = row.findViewById<TextView>(R.id.tv_picker_template_text)
            val tvSummary = row.findViewById<TextView>(R.id.tv_picker_template_summary)

            tvId.text = if (template.id.isNotBlank()) {
                "ID: ${template.id}"
            } else {
                context.getString(R.string.template_no_id)
            }
            tvText.text = template.text.ifBlank {
                context.getString(R.string.editor_template_picker_no_text)
            }
            val paramCount = template.parameters.size
            val effectCount = template.effects.size
            tvSummary.text = buildString {
                append(pluralWithNumber(context, R.plurals.word_parameters, paramCount))
                append(" · ")
                append(pluralWithNumber(context, R.plurals.word_effects, effectCount))
            }

            row.setOnClickListener {
                dialog.dismiss()
                onPicked(template)
            }
            container.addView(row)
        }

        dialog.show()
    }
}