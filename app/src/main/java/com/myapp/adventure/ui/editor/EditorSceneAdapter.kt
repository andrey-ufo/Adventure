package com.myapp.adventure.ui.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.databinding.ItemEditorSceneBinding

class EditorSceneAdapter(
    private val viewModel: AdventureEditorViewModel
) : RecyclerView.Adapter<EditorSceneAdapter.ViewHolder>() {

    private var snapshot: List<EditorScene> = emptyList()

    init { setHasStableIds(true) }

    fun submitList() {
        val newSnapshot = viewModel.scenes.map { it.deepCopy() }
        val diff = DiffUtil.calculateDiff(SceneDiffCallback(snapshot, newSnapshot))
        snapshot = newSnapshot
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = snapshot.size

    override fun getItemId(position: Int): Long =
        snapshot.getOrNull(position)?.uid?.hashCode()?.toLong() ?: RecyclerView.NO_ID

    inner class ViewHolder(val binding: ItemEditorSceneBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEditorSceneBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scene = viewModel.scenes.getOrNull(position) ?: return
        val binding = holder.binding
        val context = binding.root.context

        binding.btnDeleteScene.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                viewModel.removeScene(pos)
                submitList()
            }
        }

        binding.tvSceneId.text = "ID: ${scene.id.ifEmpty { "(будет сгенерирован)" }}"

        binding.etSceneTitle.bindAfterTextChanged { newTitle ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@bindAfterTextChanged
            val s = viewModel.scenes.getOrNull(pos) ?: return@bindAfterTextChanged
            s.title = newTitle
            if (s.id.isEmpty() || s.id.startsWith("scene_")) {
                s.id = viewModel.generateSceneId(newTitle)
                binding.tvSceneId.text = "ID: ${s.id}"
            }
        }
        binding.etSceneTitle.setTextIfDifferent(scene.title)

        binding.etSceneText.bindAfterTextChanged { newText ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@bindAfterTextChanged
            viewModel.scenes.getOrNull(pos)?.text = newText
        }
        binding.etSceneText.setTextIfDifferent(scene.text)

        binding.containerTags.removeAllViews()
        for ((tagIdx, tag) in scene.tags.withIndex()) {
            val tagView = LayoutInflater.from(context)
                .inflate(R.layout.item_editor_tag, binding.containerTags, false)
            val etTagName = tagView.findViewById<EditText>(R.id.et_tag_name)
            val etTagValue = tagView.findViewById<EditText>(R.id.et_tag_value)
            val btnDeleteTag = tagView.findViewById<View>(R.id.btn_delete_tag)

            etTagName.bindAfterTextChanged { tag.name = it }
            etTagValue.bindAfterTextChanged { tag.value = it.toIntOrNull() ?: 0 }
            etTagName.setTextIfDifferent(tag.name)
            etTagValue.setTextIfDifferent(tag.value.toString())

            btnDeleteTag.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    viewModel.removeTag(pos, tagIdx)
                    submitList()
                }
            }
            binding.containerTags.addView(tagView)
        }

        binding.btnAddTag.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                viewModel.addTag(pos)
                submitList()
            }
        }

        binding.cbChapterEnd.setOnCheckedChangeListener(null)
        binding.cbChapterEnd.isChecked = scene.isChapterEnd
        binding.layoutSteps.visibility = if (scene.isChapterEnd) View.VISIBLE else View.GONE
        binding.cbChapterEnd.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
            viewModel.scenes.getOrNull(pos)?.isChapterEnd = isChecked
            binding.layoutSteps.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.etSteps.bindAfterTextChanged { newVal ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@bindAfterTextChanged
            viewModel.scenes.getOrNull(pos)?.requiredSteps = newVal.toIntOrNull() ?: 0
        }
        binding.etSteps.setTextIfDifferent(scene.requiredSteps.toString())

        binding.containerChoices.removeAllViews()
        for ((choiceIdx, choice) in scene.choices.withIndex()) {
            val choiceView = LayoutInflater.from(context)
                .inflate(R.layout.item_editor_choice, binding.containerChoices, false)
            val etChoiceText = choiceView.findViewById<EditText>(R.id.et_choice_text)
            val spinnerScene = choiceView.findViewById<Spinner>(R.id.spinner_next_scene)
            val etCondition = choiceView.findViewById<EditText>(R.id.et_choice_condition)
            val btnDeleteChoice = choiceView.findViewById<View>(R.id.btn_delete_choice)

            btnDeleteChoice.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    viewModel.removeChoice(pos, choiceIdx)
                    submitList()
                }
            }

            etChoiceText.bindAfterTextChanged { choice.text = it }
            etCondition.bindAfterTextChanged { choice.conditionTag = it }
            etChoiceText.setTextIfDifferent(choice.text)
            etCondition.setTextIfDifferent(choice.conditionTag)

            val options = viewModel.getSceneOptions(position)
            val displayList = mutableListOf("(выберите сцену)")
            displayList.addAll(options.map { it.first })
            val spinnerAdapter = ArrayAdapter(
                context, android.R.layout.simple_spinner_item, displayList
            )
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerScene.adapter = spinnerAdapter

            val selectedPos = options.indexOfFirst { it.second == choice.nextSceneId } + 1
            if (selectedPos in 1..options.size) spinnerScene.setSelection(selectedPos)

            spinnerScene.onItemSelectedListener = object :
                android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?, pos: Int, id: Long
                ) {
                    choice.nextSceneId = options.getOrNull(pos - 1)?.second ?: ""
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            binding.containerChoices.addView(choiceView)
        }

        binding.btnAddChoice.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                viewModel.addChoice(pos)
                submitList()
            }
        }
    }
}

private fun EditText.setTextIfDifferent(value: String) {
    if (text.toString() != value) setText(value)
}

private fun EditText.bindAfterTextChanged(action: (String) -> Unit) {
    (getTag(R.id.tag_watcher) as? TextWatcher)?.let { removeTextChangedListener(it) }
    val watcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = action(s?.toString() ?: "")
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
    }
    setTag(R.id.tag_watcher, watcher)
    addTextChangedListener(watcher)
}