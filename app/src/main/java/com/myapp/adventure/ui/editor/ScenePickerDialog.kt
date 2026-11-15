package com.myapp.adventure.ui.editor

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene

/**
 * Ф4.3.2a: reusable scene picker dialog.
 *
 * **Why not a giant `Spinner`.** With 50+ scenes a `Spinner` (used for
 * `EditorChoice.nextSceneId` in `EditorSceneAdapter`) becomes a
 * scroll-torture list with no search. This dialog replaces that pattern
 * for the template editor: search by `id` or `title` (case-insensitive,
 * debounced), a compact two-line row per scene (title bold, id mono
 * grey), and **special rows** at the top for template-only cases:
 * - `@self` — "the scene where this template is applied" (only when
 *   `allowSelf = true`, i.e. template editor, not plain choice picker);
 * - "create new scene" — opens a small dialog, calls
 *   `onCreateNew(title)`, expects the caller to create the scene and
 *   return its id via the `onPicked` callback chain.
 *
 * **Empty query → empty result list.** A 500-scene adventure never
 * renders 500 rows. The author types two characters and sees the
 * filtered subset; special rows are always visible.
 *
 * **Reusability.** The dialog has no dependency on the editor
 * `Fragment`/`ViewModel`; caller passes scenes and callbacks. Future
 * refactor of `EditorChoice.nextSceneId` (separate batch) can reuse
 * this dialog unchanged.
 */
object ScenePickerDialog {

    private const val DEBOUNCE_MS = 200L
    private const val MAX_RESULTS = 50

    /**
     * @param context used to build the dialog.
     * @param scenes available scenes; caller passes
     *   `viewModel.scenes` (already sorted by creation order).
     * @param initialSceneId current value; `""` means "not picked yet",
     *   `"@self"` shows the `@self` special row as selected (visual cue
     *   only — the list itself does not pre-highlight).
     * @param allowSelf whether the `@self` special row is shown. `true`
     *   for template editor, `false` for plain-choice picker.
     * @param onPicked called with the chosen scene id (or `"@self"`).
     * @param onCreateNew called with the title the author typed for a
     *   new scene. The caller is expected to create the scene
     *   (`viewModel.addSceneWithTitle(title)`) and to call `onPicked` on
     *   the created scene's id. The dialog closes before this call; the
     *   caller opens any follow-up UI (e.g. re-shows the picker).
     */
    fun show(
        context: Context,
        scenes: List<EditorScene>,
        initialSceneId: String,
        allowSelf: Boolean,
        onPicked: (String) -> Unit,
        onCreateNew: (String) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_scene_picker, null)
        val etQuery = dialogView.findViewById<EditText>(R.id.et_scene_query)
        val tvHint = dialogView.findViewById<TextView>(R.id.tv_scene_picker_hint)
        val tvPickSelf = dialogView.findViewById<TextView>(R.id.tv_pick_self)
        val tvPickCreateNew = dialogView.findViewById<TextView>(R.id.tv_pick_create_new)
        val scrollResults = dialogView.findViewById<View>(R.id.scroll_results)
        val containerResults = dialogView.findViewById<LinearLayout>(R.id.container_results)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.scene_picker_title)
            .setView(dialogView)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        tvPickSelf.visibility = if (allowSelf) View.VISIBLE else View.GONE

        val handler = Handler(Looper.getMainLooper())
        var pendingSearch: Runnable? = null

        fun renderResults(query: String) {
            containerResults.removeAllViews()
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                scrollResults.visibility = View.GONE
                tvHint.visibility = View.VISIBLE
                return
            }
            val lower = trimmed.lowercase()
            val filtered = scenes
                .filter { scene ->
                    scene.id.lowercase().contains(lower) ||
                            scene.title.lowercase().contains(lower)
                }
                .take(MAX_RESULTS)
            if (filtered.isEmpty()) {
                scrollResults.visibility = View.GONE
                tvHint.text = context.getString(R.string.scene_picker_nothing_found)
                tvHint.visibility = View.VISIBLE
                return
            }
            tvHint.visibility = View.GONE
            scrollResults.visibility = View.VISIBLE
            val inflater = LayoutInflater.from(context)
            for (scene in filtered) {
                val row = inflater.inflate(
                    R.layout.item_scene_picker_row,
                    containerResults,
                    false,
                )
                val tvTitle = row.findViewById<TextView>(R.id.tv_row_title)
                val tvId = row.findViewById<TextView>(R.id.tv_row_id)
                tvTitle.text = scene.title.ifBlank { context.getString(R.string.label_without_title) }
                tvId.text = scene.id.ifEmpty { context.getString(R.string.scene_no_id) }
                row.setOnClickListener {
                    onPicked(scene.id)
                    dialog.dismiss()
                }
                containerResults.addView(row)
            }
        }

        etQuery.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                pendingSearch?.let { handler.removeCallbacks(it) }
                val q = s?.toString().orEmpty()
                val r = Runnable { renderResults(q) }
                pendingSearch = r
                handler.postDelayed(r, DEBOUNCE_MS)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        tvPickSelf.setOnClickListener {
            onPicked("@self")
            dialog.dismiss()
        }

        tvPickCreateNew.setOnClickListener {
            dialog.dismiss()
            showCreateNewSceneDialog(context, onCreateNew)
        }

        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
        dialog.show()
        etQuery.requestFocus()

        // Pre-fill the query with the id part of the current scene
        // (helper: authors often refine an existing pick, not replace it).
        if (initialSceneId.isNotEmpty() && initialSceneId != "@self") {
            val match = scenes.firstOrNull { it.id == initialSceneId }
            if (match != null) {
                etQuery.setText(match.id)
                etQuery.setSelection(etQuery.text.length)
            }
        }
    }

    /**
     * Local "new scene" prompt. Not reusing
     * `EditorSceneAdapter.showNewSceneDialog` because that one is tied to
     * a `ViewHolder` and to `holder.bindingAdapterPosition`; here we only
     * need the title and a callback.
     */
    private fun showCreateNewSceneDialog(
        context: Context,
        onCreateNew: (String) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_new_scene, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_new_scene_title)
        AlertDialog.Builder(context)
            .setTitle(R.string.editor_new_scene_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val title = etTitle.text.toString().trim()
                onCreateNew(title)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}