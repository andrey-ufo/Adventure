package com.myapp.adventure.ui.editor

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.TagCategory
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.databinding.ItemEditorSceneBinding
import com.myapp.adventure.databinding.ItemEmptyHintBinding

class EditorSceneAdapter(
    private val viewModel: AdventureEditorViewModel,
    private val onDirty: (structural: Boolean) -> Unit,
    private val onPreview: (sceneUid: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SCENE = 0
        private const val TYPE_EMPTY = 1
    }

    private var snapshot: List<EditorScene> = emptyList()
    private var highlightedUid: String? = null

    init { setHasStableIds(true) }

    fun submitList() {
        val newSnapshot = viewModel.scenes.map { it.deepCopy() }

        // Переход между пустым и непустым списком — DiffUtil не подходит,
        // потому что меняется item-type (empty-hint vs scene). Сбрасываем полностью.
        if (snapshot.isEmpty() != newSnapshot.isEmpty()) {
            snapshot = newSnapshot
            notifyDataSetChanged()
            return
        }

        if (newSnapshot.isEmpty()) {
            return
        }

        val diff = DiffUtil.calculateDiff(SceneDiffCallback(snapshot, newSnapshot))
        snapshot = newSnapshot
        diff.dispatchUpdatesTo(this)
    }

    /** Подсвечивает указанную сцену (жёлтая рамка). null — снять подсветку. */
    fun setHighlighted(uid: String?) {
        if (highlightedUid == uid) return
        val oldUid = highlightedUid
        highlightedUid = uid
        val oldPos = viewModel.scenes.indexOfFirst { it.uid == oldUid }
        if (oldPos >= 0) notifyItemChanged(oldPos)
        val newPos = viewModel.scenes.indexOfFirst { it.uid == uid }
        if (newPos >= 0) notifyItemChanged(newPos)
    }

    override fun getItemCount(): Int =
        if (viewModel.scenes.isEmpty()) 1 else viewModel.scenes.size

    override fun getItemViewType(position: Int): Int =
        if (viewModel.scenes.isEmpty()) TYPE_EMPTY else TYPE_SCENE

    override fun getItemId(position: Int): Long =
        if (viewModel.scenes.isEmpty()) -1L
        else snapshot.getOrNull(position)?.uid?.hashCode()?.toLong() ?: RecyclerView.NO_ID

    inner class ViewHolder(val binding: ItemEditorSceneBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class EmptyViewHolder(val binding: ItemEmptyHintBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_EMPTY) {
            val binding = ItemEmptyHintBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            EmptyViewHolder(binding)
        } else {
            val binding = ItemEditorSceneBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            ViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is ViewHolder) return

        val scene = viewModel.scenes.getOrNull(position) ?: return
        val binding = holder.binding
        val context = binding.root.context

        binding.root.setBackgroundResource(
            if (scene.uid == highlightedUid) R.drawable.scene_background_highlighted
            else R.drawable.scene_background
        )

        // ---------- Шапка: удаление ----------
        binding.btnDeleteScene.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val target = viewModel.scenes.getOrNull(pos) ?: return@setOnClickListener
            val sceneUid = target.uid
            val sceneName = target.title.ifBlank { "(без названия)" }

            AlertDialog.Builder(context)
                .setTitle("Удалить сцену?")
                .setMessage(
                    "Сцена «$sceneName» будет удалена без возможности отмены.\n\n" +
                            "Выборы в других сценах, которые вели на неё, станут битыми ссылками."
                )
                .setPositiveButton("Удалить") { _, _ ->
                    val currentPos = viewModel.scenes.indexOfFirst { it.uid == sceneUid }
                    if (currentPos != -1) {
                        viewModel.removeScene(currentPos)
                        onDirty(true)
                        submitList()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        // ---------- Аккордеон ----------
        val isExpanded = viewModel.isSceneExpanded(scene.uid)
        applyExpandedState(binding, isExpanded)

        val toggleAction = View.OnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@OnClickListener
            val currentScene = viewModel.scenes.getOrNull(pos) ?: return@OnClickListener
            val nowExpanded = viewModel.toggleSceneExpanded(currentScene.uid)
            applyExpandedState(binding, nowExpanded)
        }
        binding.tvChevron.setOnClickListener(toggleAction)
        binding.infoRow.setOnClickListener(toggleAction)

        // ---------- ID, счётчик, бейдж главы ----------
        binding.tvSceneId.text = formatSceneIdLabel(scene)
        binding.tvChoicesCount.text = formatChoicesCount(scene.choices.size)
        binding.tvChapterEndBadge.visibility = if (scene.isChapterEnd) View.VISIBLE else View.GONE
        binding.tvDeathBadge.visibility = if (scene.isDeathScene) View.VISIBLE else View.GONE

        // ---------- Сцена смерти ----------
        binding.cbDeathScene.setOnCheckedChangeListener(null)
        binding.cbDeathScene.isChecked = scene.isDeathScene
        binding.layoutDeathOptions.visibility = if (scene.isDeathScene) View.VISIBLE else View.GONE

        val deathListener = object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                val self = this
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val s = viewModel.scenes.getOrNull(pos) ?: return

                if (isChecked) {
                    val previousDeath = viewModel.scenes.firstOrNull { it.isDeathScene && it.uid != s.uid }
                    if (previousDeath != null) {
                        AlertDialog.Builder(context)
                            .setTitle("Заменить сцену смерти?")
                            .setMessage("В приключении может быть только одна сцена смерти. Заменить «${previousDeath.title.ifBlank { "(без названия)" }}» на «${s.title.ifBlank { "(без названия)" }}»?")
                            .setPositiveButton("Заменить") { _, _ ->
                                viewModel.setDeathScene(s.uid, true)
                                onDirty(true)
                                submitList()
                            }
                            .setNegativeButton("Отмена", null)
                            .setOnCancelListener {
                                binding.cbDeathScene.setOnCheckedChangeListener(null)
                                binding.cbDeathScene.isChecked = false
                                binding.cbDeathScene.setOnCheckedChangeListener(self)
                            }
                            .show()
                    } else {
                        viewModel.setDeathScene(s.uid, true)
                        binding.layoutDeathOptions.visibility = View.VISIBLE
                        binding.tvDeathBadge.visibility = View.VISIBLE
                        onDirty(true)
                    }
                } else {
                    viewModel.setDeathScene(s.uid, false)
                    binding.layoutDeathOptions.visibility = View.GONE
                    binding.tvDeathBadge.visibility = View.GONE
                    onDirty(true)
                }
            }
        }
        binding.cbDeathScene.setOnCheckedChangeListener(deathListener)

        binding.cbDeathAllowStepBack.setOnCheckedChangeListener(null)
        binding.cbDeathAllowStepBack.isChecked = scene.deathAllowStepBack
        binding.cbDeathAllowStepBack.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
            viewModel.scenes.getOrNull(pos)?.deathAllowStepBack = isChecked
            onDirty(false)
        }

        binding.cbDeathAllowChapterRestart.setOnCheckedChangeListener(null)
        binding.cbDeathAllowChapterRestart.isChecked = scene.deathAllowChapterRestart
        binding.cbDeathAllowChapterRestart.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
            viewModel.scenes.getOrNull(pos)?.deathAllowChapterRestart = isChecked
            onDirty(false)
        }

        binding.cbDeathAllowAdventureRestart.setOnCheckedChangeListener(null)
        binding.cbDeathAllowAdventureRestart.isChecked = scene.deathAllowAdventureRestart
        binding.cbDeathAllowAdventureRestart.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
            viewModel.scenes.getOrNull(pos)?.deathAllowAdventureRestart = isChecked
            onDirty(false)
        }

        // ---------- Кнопка «Пересобрать ID» ----------
        val showRebuild = scene.title.isNotBlank() && scene.id.isNotBlank()
        binding.btnRebuildId.visibility = if (showRebuild) View.VISIBLE else View.GONE
        binding.btnRebuildId.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val current = viewModel.scenes.getOrNull(pos) ?: return@setOnClickListener
            showRebuildIdDialog(context, current)
        }

        // ---------- Кнопка «▶ Предпросмотр» ----------
        binding.btnPreviewScene.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val current = viewModel.scenes.getOrNull(pos) ?: return@setOnClickListener
            onPreview(current.uid)
        }

        // ---------- Название ----------
        binding.etSceneTitle.setTextIfDifferentSilently(scene.title)
        binding.etSceneTitle.bindAfterTextChanged { newTitle ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@bindAfterTextChanged
            val s = viewModel.scenes.getOrNull(pos) ?: return@bindAfterTextChanged
            s.title = newTitle
            onDirty(false)
        }

        // ---------- Заморозка ID при blur ----------
        binding.etSceneTitle.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@OnFocusChangeListener

            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@OnFocusChangeListener
            val s = viewModel.scenes.getOrNull(pos) ?: return@OnFocusChangeListener

            if (s.idIsAuto && s.title.isNotBlank()) {
                val changed = viewModel.freezeSceneId(s.uid)
                if (changed) {
                    onDirty(true)
                    submitList()
                } else {
                    binding.tvSceneId.text = formatSceneIdLabel(s)
                }
            }
        }

        // ---------- Текст сцены ----------
        binding.etSceneText.setTextIfDifferentSilently(scene.text)
        binding.etSceneText.bindAfterTextChanged { newText ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@bindAfterTextChanged
            viewModel.scenes.getOrNull(pos)?.text = newText
            onDirty(false)
        }

        // ---------- Метки сцены ----------
        binding.containerTags.removeAllViews()
        for ((tagIdx, tag) in scene.tags.withIndex()) {
            renderTagRow(
                container = binding.containerTags,
                tag = tag,
                onChanged = { onDirty(false) },
                onDelete = {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        viewModel.removeTag(pos, tagIdx)
                        onDirty(true)
                        submitList()
                    }
                }
            )
        }

        binding.btnAddTag.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                viewModel.addTag(pos)
                onDirty(true)
                submitList()
            }
        }

        // ---------- Настройки сцены (2d-iii) ----------
        // Collapsible sub-block: chapter end / step gate / ending /
        // death scene + their sub-options. Collapsed by default — a
        // scene with all four flags would otherwise be 6 checkboxes
        // tall and push choices off-screen. State is per-scene (per
        // uid), survives scrolling; pruned on scene deletion.
        //
        // No text indicator (`▸`/`▾`): the label stays
        // "Настройки сцены"; the expanded/collapsed state is visible
        // from whether `layout_scene_settings` is rendered. Removing
        // the triangle keeps the UI free of decorative glyphs, per
        // user preference (2026-11-09).
        val settingsExpanded = viewModel.isSceneSettingsExpanded(scene.uid)
        binding.layoutSceneSettings.visibility =
            if (settingsExpanded) View.VISIBLE else View.GONE
        binding.tvSceneSettingsToggle.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val s = viewModel.scenes.getOrNull(pos) ?: return@setOnClickListener
            val nowExpanded = viewModel.toggleSceneSettings(s.uid)
            binding.layoutSceneSettings.visibility =
                if (nowExpanded) View.VISIBLE else View.GONE
        }

        // ---------- Конец главы (нарративная граница) ----------
        // `cb_chapter_end` is purely narrative — it marks the scene
        // as the last of a chapter for grouping / stats / the map
        // view. It does not touch the gate (that is `cb_scene_gate`).
        binding.cbChapterEnd.setOnCheckedChangeListener(null)
        binding.cbChapterEnd.isChecked = scene.isChapterEnd
        binding.cbChapterEnd.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
            val s = viewModel.scenes.getOrNull(pos) ?: return@setOnCheckedChangeListener
            s.isChapterEnd = isChecked
            binding.tvChapterEndBadge.visibility = if (isChecked) View.VISIBLE else View.GONE
            onDirty(true)
        }

        // ---------- Шаговый гейт (per-scene, b2) ----------
        // `cb_scene_gate` is independent of `cb_chapter_end`: a scene
        // can be a chapter boundary, a gate, both, or neither. This
        // matches `Gate` in IR (Ф1.7.0.4) — a gate is a scene
        // property, not a chapter property.
        //
        // The "repeatable" toggle lives inside the steps dialog
        // (2d-i): it is a property of the gate, not a separate scene
        // checkbox. The dialog returns `(steps, repeatable)`.
        binding.cbSceneGate.setOnCheckedChangeListener(null)
        binding.cbSceneGate.isChecked = scene.gateRequiredSteps > 0

        val sceneGateListener = object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                val self = this
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val s = viewModel.scenes.getOrNull(pos) ?: return

                if (isChecked && s.gateRequiredSteps == 0) {
                    // First enable: ask for steps + repeatable. If the
                    // author cancels the dialog, un-check the box (the
                    // gate would otherwise be "on" with 0 steps, which
                    // the parser rejects as `gate <= 0`).
                    showChapterStepsDialog(
                        context = context,
                        initialSteps = 1000,
                        initialRepeatable = false,
                        onApply = { newSteps, newRepeatable ->
                            s.gateRequiredSteps = newSteps
                            s.gateRepeatable = newRepeatable
                            binding.layoutSteps.visibility = View.VISIBLE
                            binding.tvStepsDisplay.text =
                                formatStepsDisplay(newSteps, newRepeatable)
                            onDirty(true)
                        },
                        onCancel = {
                            binding.cbSceneGate.setOnCheckedChangeListener(null)
                            binding.cbSceneGate.isChecked = false
                            binding.cbSceneGate.setOnCheckedChangeListener(self)
                        }
                    )
                } else if (!isChecked) {
                    s.gateRequiredSteps = 0
                    s.gateRepeatable = false
                    binding.layoutSteps.visibility = View.GONE
                    onDirty(true)
                }
            }
        }
        binding.cbSceneGate.setOnCheckedChangeListener(sceneGateListener)

        binding.layoutSteps.visibility =
            if (scene.gateRequiredSteps > 0) View.VISIBLE else View.GONE
        if (scene.gateRequiredSteps > 0) {
            binding.tvStepsDisplay.text =
                formatStepsDisplay(scene.gateRequiredSteps, scene.gateRepeatable)
        }

        binding.layoutSteps.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val s = viewModel.scenes.getOrNull(pos) ?: return@setOnClickListener
            showChapterStepsDialog(
                context = context,
                initialSteps = s.gateRequiredSteps,
                initialRepeatable = s.gateRepeatable,
                onApply = { newSteps, newRepeatable ->
                    s.gateRequiredSteps = newSteps
                    s.gateRepeatable = newRepeatable
                    binding.tvStepsDisplay.text =
                        formatStepsDisplay(newSteps, newRepeatable)
                    onDirty(true)
                },
                onCancel = { /* ничего не меняем */ }
            )
        }

        // ---------- Ф1.7.5: is_ending ----------

        // "🎉 Конец игры" — scene ends the adventure, not just a
        // chapter. Distinct from `cbChapterEnd` above (which writes
        // `chapter.required_steps` on the next chapter — a step gate,
        // not an ending). Distinct from `cbDeathScene` below (which
        // shows a special death screen instead of the normal ending).
        //
        // No dialogue, no mutual-exclusion check: `isEnding` and
        // `isChapterEnd` and `isDeathScene` are independent flags. An
        // author who ticks both `isEnding` and `isDeathScene` will get
        // the death screen for that scene (the reader prioritises
        // death over ending); an author who ticks `isEnding` and adds
        // choices will get `EndingSceneWithChoices` ERROR from the
        // analyzer. Both are author errors the analyzer catches — the
        // editor UI should not duplicate those checks.
        binding.cbEnding.setOnCheckedChangeListener(null)
        binding.cbEnding.isChecked = scene.isEnding
        binding.cbEnding.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
            val s = viewModel.scenes.getOrNull(pos) ?: return@setOnCheckedChangeListener
            s.isEnding = isChecked
            // `onDirty(false)`: this is a simple flag, not a structural
            // change (no scene added / removed, no dialogue opened).
            // Matches the pattern of `cbDeathAllowStepBack` and its
            // siblings — flag toggles don't push history entries, so
            // rapid experiment-by-clicking does not fill the undo
            // stack.
            onDirty(false)
        }

        // ---------- Выборы ----------
        binding.containerChoices.removeAllViews()
        for ((choiceIdx, choice) in scene.choices.withIndex()) {
            val choiceView = LayoutInflater.from(context)
                .inflate(R.layout.item_editor_choice, binding.containerChoices, false)
            val choiceHeader = choiceView.findViewById<View>(R.id.choice_header)
            val choiceBody = choiceView.findViewById<View>(R.id.choice_body)
            val tvChevron = choiceView.findViewById<TextView>(R.id.tv_choice_chevron)
            val tvSummary = choiceView.findViewById<TextView>(R.id.tv_choice_summary)
            val tvBadges = choiceView.findViewById<TextView>(R.id.tv_choice_badges)
            val etChoiceText = choiceView.findViewById<EditText>(R.id.et_choice_text)
            val spinnerScene = choiceView.findViewById<Spinner>(R.id.spinner_next_scene)
            val etCondition = choiceView.findViewById<EditText>(R.id.et_choice_condition)
            val btnChoiceEffects = choiceView.findViewById<View>(R.id.btn_choice_effects)
            val btnDeleteChoice = choiceView.findViewById<View>(R.id.btn_delete_choice)

            fun applyChoiceState() {
                val expanded = viewModel.isChoiceExpanded(choice.uid)
                choiceBody.visibility = if (expanded) View.VISIBLE else View.GONE
                tvChevron.text = if (expanded) "▼" else "▶"

                if (expanded) {
                    tvSummary.text = ""
                } else {
                    tvSummary.text = choice.text.ifBlank { "(без текста)" }
                }

                val badges = mutableListOf<String>()
                if (choice.tags.isNotEmpty()) {
                    badges.add("⚙ ${choice.tags.size}")
                }
                if (choice.nextSceneId.isNotEmpty()) {
                    badges.add("🔗")
                }
                if (choice.conditionTag.isNotEmpty()) {
                    badges.add("?")
                }
                tvBadges.text = badges.joinToString(" ")
            }
            applyChoiceState()

            choiceHeader.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val s = viewModel.scenes.getOrNull(pos) ?: return@setOnClickListener
                val currentChoice = s.choices.getOrNull(choiceIdx) ?: return@setOnClickListener
                viewModel.toggleChoiceExpanded(currentChoice.uid)
                applyChoiceState()
            }

            btnChoiceEffects.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val currentScene = viewModel.scenes.getOrNull(pos) ?: return@setOnClickListener
                val currentChoice = currentScene.choices.getOrNull(choiceIdx) ?: return@setOnClickListener
                showChoiceEffectsDialog(context, pos, choiceIdx, currentChoice) {
                    applyChoiceState()
                }
            }

            btnDeleteChoice.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val currentChoice = viewModel.scenes.getOrNull(pos)?.choices?.getOrNull(choiceIdx)
                val previewText = currentChoice?.text?.ifBlank { "(без текста)" }

                AlertDialog.Builder(context)
                    .setTitle("Удалить вариант действия?")
                    .setMessage("Вариант «$previewText» будет удалён без возможности отмены.")
                    .setPositiveButton("Удалить") { _, _ ->
                        val currentPos = holder.bindingAdapterPosition
                        if (currentPos != RecyclerView.NO_POSITION) {
                            viewModel.removeChoice(currentPos, choiceIdx)
                            onDirty(true)
                            submitList()
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }

            etChoiceText.setTextIfDifferentSilently(choice.text)
            etCondition.setTextIfDifferentSilently(choice.conditionTag)
            etChoiceText.bindAfterTextChanged { newText ->
                choice.text = newText
                if (!viewModel.isChoiceExpanded(choice.uid)) {
                    tvSummary.text = newText.ifBlank { "(без текста)" }
                }
                onDirty(false)
            }
            etCondition.bindAfterTextChanged { newText ->
                choice.conditionTag = newText
                applyChoiceState()
                onDirty(false)
            }

            val options = viewModel.getSceneOptions()
            val displayList = mutableListOf(
                "➕ Создать новую сцену…",
                "(выберите сцену)"
            )
            displayList.addAll(options.map { it.first })

            val spinnerAdapter = ArrayAdapter(
                context, android.R.layout.simple_spinner_item, displayList
            )
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spinnerScene.onItemSelectedListener = null
            spinnerScene.adapter = spinnerAdapter

            val currentIndex = options.indexOfFirst { it.second == choice.nextSceneId }
            val initialSelection = if (currentIndex >= 0) currentIndex + 2 else 1
            spinnerScene.setSelection(initialSelection)

            spinnerScene.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?, selectedPos: Int, id: Long
                ) {
                    if (selectedPos == 0) {
                        val pos = holder.bindingAdapterPosition
                        if (pos == RecyclerView.NO_POSITION) return
                        showNewSceneDialog(context, holder, choiceIdx)
                    } else if (selectedPos == 1) {
                        if (choice.nextSceneId != "") {
                            choice.nextSceneId = ""
                            applyChoiceState()
                            onDirty(true)
                        }
                    } else {
                        val newNext = options.getOrNull(selectedPos - 2)?.second ?: ""
                        if (choice.nextSceneId != newNext) {
                            choice.nextSceneId = newNext
                            applyChoiceState()
                            onDirty(true)
                        }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            binding.containerChoices.addView(choiceView)
        }

        binding.btnAddChoice.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                viewModel.addChoice(pos)
                onDirty(true)
                submitList()
            }
        }
    }

    // ---------- Диалог эффектов выбора ----------

    private fun showChoiceEffectsDialog(
        context: Context,
        sceneIndex: Int,
        choiceIndex: Int,
        choice: EditorChoice,
        onChanged: () -> Unit = {}
    ) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_edit_choice_tags, null)
        val tvInfo = dialogView.findViewById<TextView>(R.id.tv_choice_effects_info)
        val container = dialogView.findViewById<LinearLayout>(R.id.container_choice_tags)
        val btnAddEffect = dialogView.findViewById<Button>(R.id.btn_add_choice_effect)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Эффекты выбора")
            .setView(dialogView)
            .setPositiveButton("Готово") { _, _ ->
                onDirty(false)
                onChanged()
            }
            .create()

        fun refresh() {
            val choiceText = choice.text.ifBlank { "(без текста)" }
            tvInfo.text = "Эффекты, применяемые при клике на «$choiceText»:"

            container.removeAllViews()
            for ((tagIdx, tag) in choice.tags.withIndex()) {
                renderTagRow(
                    container = container,
                    tag = tag,
                    onChanged = { onDirty(false) },
                    onDelete = {
                        if (tagIdx in choice.tags.indices) {
                            choice.tags.removeAt(tagIdx)
                            onDirty(false)
                            refresh()
                        }
                    }
                )
            }

            if (choice.tags.isEmpty()) {
                val tvEmpty = TextView(context).apply {
                    text = "(нет эффектов)"
                    setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                    textSize = 12f
                }
                container.addView(tvEmpty)
            }
        }

        btnAddEffect.setOnClickListener {
            choice.tags.add(EditorTag(category = TagCategory.ITEM, value = 1))
            onDirty(false)
            refresh()
        }

        refresh()
        dialog.show()
    }

    // ---------- Диалог шагового шлюза ----------

    /**
     * Steps-gate picker dialog. Returns `(steps, repeatable)`.
     *
     * The `repeatable` toggle lives inside the dialog (2d-i): it is a
     * property of the gate itself, not a separate scene-level
     * checkbox. This keeps the scene card compact (the scene card
     * would otherwise carry two checkboxes plus the steps line), and
     * puts the two gate settings next to each other — the author
     * sees both at the moment they set the step count.
     *
     * @param initialSteps current step count (0 = dialog opened from
     *   a not-yet-enabled gate; empty input field).
     * @param initialRepeatable current repeatable flag (false on
     *   first enable).
     * @param onApply called with the chosen `(steps, repeatable)`
     *   when the author taps "Применить" or a preset. The `steps`
     *   value is guaranteed to be `> 0`.
     * @param onCancel called when the dialog is dismissed without a
     *   valid apply (back button, "Отмена", or a preset that failed
     *   validation is not a cancel — only dismiss-before-apply).
     */
    private fun showChapterStepsDialog(
        context: Context,
        initialSteps: Int,
        initialRepeatable: Boolean,
        onApply: (steps: Int, repeatable: Boolean) -> Unit,
        onCancel: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_chapter_steps, null)

        val etCustom = dialogView.findViewById<EditText>(R.id.et_custom_steps)
        val cbRepeatable = dialogView.findViewById<CheckBox>(R.id.cb_repeatable)
        val btn500 = dialogView.findViewById<Button>(R.id.btn_steps_500)
        val btn1000 = dialogView.findViewById<Button>(R.id.btn_steps_1000)
        val btn4000 = dialogView.findViewById<Button>(R.id.btn_steps_4000)
        val btn8000 = dialogView.findViewById<Button>(R.id.btn_steps_8000)

        if (initialSteps > 0) {
            etCustom.setText(initialSteps.toString())
            etCustom.setSelection(etCustom.text.length)
        }
        cbRepeatable.isChecked = initialRepeatable

        var applied = false

        val dialog = AlertDialog.Builder(context)
            .setTitle("Шаговый гейт")
            .setView(dialogView)
            .setPositiveButton("Применить", null)
            .setNegativeButton("Отмена", null)
            .create()

        fun applyAndClose(value: Int) {
            if (value <= 0) {
                android.widget.Toast.makeText(context, "Значение должно быть больше 0", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            applied = true
            onApply(value, cbRepeatable.isChecked)
            dialog.dismiss()
        }

        btn500.setOnClickListener { applyAndClose(500) }
        btn1000.setOnClickListener { applyAndClose(1000) }
        btn4000.setOnClickListener { applyAndClose(4000) }
        btn8000.setOnClickListener { applyAndClose(8000) }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = etCustom.text.toString().toIntOrNull()
                if (value == null || value <= 0) {
                    android.widget.Toast.makeText(context, "Введите целое число больше 0", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    applyAndClose(value)
                }
            }
        }

        dialog.setOnDismissListener {
            if (!applied) onCancel()
        }

        dialog.show()
    }

    private fun formatStepsDisplay(steps: Int, repeatable: Boolean = false): String {
        if (steps <= 0) return "Шаговый гейт не задан · нажмите, чтобы изменить"
        val minutes = ((steps + 99) / 100).coerceAtLeast(1)
        val timeText = when {
            minutes < 60 -> "≈ $minutes мин"
            else -> {
                val hours = minutes / 60
                val mins = minutes % 60
                if (mins == 0) "≈ $hours ч" else "≈ $hours ч $mins мин"
            }
        }
        val formatted = String.format(java.util.Locale.getDefault(), "%,d", steps).replace(',', ' ')
        val mode = if (repeatable) " · каждый раз" else ""
        return "$formatted шагов ($timeText)$mode · нажмите, чтобы изменить"
    }

    // ---------- Диалоги ----------

    private fun showNewSceneDialog(
        context: Context,
        holder: ViewHolder,
        choiceIdx: Int
    ) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_new_scene, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_new_scene_title)

        AlertDialog.Builder(context)
            .setTitle("Новая сцена")
            .setView(dialogView)
            .setPositiveButton("Создать") { _, _ ->
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setPositiveButton
                val currentScene = viewModel.scenes.getOrNull(pos) ?: return@setPositiveButton
                val currentChoice = currentScene.choices.getOrNull(choiceIdx) ?: return@setPositiveButton

                val userTitle = etTitle.text.toString().trim()
                val finalTitle = userTitle.ifBlank { "Новая сцена ${viewModel.scenes.size + 1}" }

                val newScene = viewModel.addSceneWithTitle(finalTitle)
                currentChoice.nextSceneId = newScene.id
                onDirty(true)
                submitList()
            }
            .setNegativeButton("Отмена") { _, _ ->
                submitList()
            }
            .show()
    }

    private fun showRebuildIdDialog(
        context: Context,
        scene: EditorScene
    ) {
        val previewId = viewModel.previewRebuiltId(scene.uid)
        if (previewId == null) {
            android.widget.Toast.makeText(
                context, "Сначала задайте название сцены", android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (previewId == scene.id) {
            android.widget.Toast.makeText(
                context, "ID уже соответствует названию", android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("Пересобрать ID?")
            .setMessage(
                "ID сцены «${scene.title}» будет изменён:\n\n" +
                        "Было:  ${scene.id.ifEmpty { "(пусто)" }}\n" +
                        "Станет: $previewId\n\n" +
                        "Все выборы в других сценах, которые ссылаются на «${scene.id}», " +
                        "будут автоматически обновлены на новый ID."
            )
            .setPositiveButton("Пересобрать") { _, _ ->
                val result = viewModel.rebuildSceneId(scene.uid)
                if (result != null) {
                    onDirty(true)
                    submitList()

                    val plural = when {
                        result.updatedReferences % 100 in 11..14 -> "ссылок"
                        result.updatedReferences % 10 == 1 -> "ссылку"
                        result.updatedReferences % 10 in 2..4 -> "ссылки"
                        else -> "ссылок"
                    }
                    val message = if (result.updatedReferences > 0) {
                        "ID обновлён. Обновлено ${result.updatedReferences} $plural в других сценах."
                    } else {
                        "ID обновлён. Ссылок на старый ID не было."
                    }
                    android.widget.Toast.makeText(
                        context, message, android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- Утилиты ----------

    private fun applyExpandedState(binding: ItemEditorSceneBinding, expanded: Boolean) {
        binding.body.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.tvChevron.text = if (expanded) "▼" else "▶"
    }

    private fun formatSceneIdLabel(scene: EditorScene): String = when {
        scene.id.isNotEmpty() -> "ID: ${scene.id}"
        scene.title.isNotBlank() -> "ID: (будет сгенерирован)"
        else -> "ID: (введите название)"
    }

    private fun formatChoicesCount(n: Int): String = when {
        n == 0 -> "нет вариантов"
        n == 1 -> "1 вариант"
        n in 2..4 -> "$n варианта"
        else -> "$n вариантов"
    }
}