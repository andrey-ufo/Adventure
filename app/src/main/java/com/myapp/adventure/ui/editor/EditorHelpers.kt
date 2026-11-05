package com.myapp.adventure.ui.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.TagCategory

fun EditText.setTextIfDifferentSilently(value: String) {
    if (text.toString() == value) return
    val watcher = getTag(R.id.tag_watcher) as? TextWatcher
    watcher?.let { removeTextChangedListener(it) }
    setText(value)
    watcher?.let { addTextChangedListener(it) }
}

fun EditText.bindAfterTextChanged(action: (String) -> Unit) {
    (getTag(R.id.tag_watcher) as? TextWatcher)?.let { removeTextChangedListener(it) }
    val watcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = action(s?.toString() ?: "")
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
    }
    setTag(R.id.tag_watcher, watcher)
    addTextChangedListener(watcher)
}

/**
 * Рендерит одну строку тега (имя, значение, displayName, категория, видимость, удаление).
 * Используется и для тегов сцены, и для тегов эффектов выбора, и для начального состояния.
 */
fun renderTagRow(
    container: LinearLayout,
    tag: EditorTag,
    onChanged: () -> Unit,
    onDelete: () -> Unit
) {
    val context = container.context
    val tagView = LayoutInflater.from(context)
        .inflate(R.layout.item_editor_tag, container, false)
    val etTagName = tagView.findViewById<EditText>(R.id.et_tag_name)
    val etTagValue = tagView.findViewById<EditText>(R.id.et_tag_value)
    val etTagDisplay = tagView.findViewById<EditText>(R.id.et_tag_display)
    val spinnerCategory = tagView.findViewById<Spinner>(R.id.spinner_tag_category)
    val cbVisible = tagView.findViewById<CheckBox>(R.id.cb_tag_visible)
    val btnDeleteTag = tagView.findViewById<View>(R.id.btn_delete_tag)

    etTagName.setTextIfDifferentSilently(tag.name)
    etTagValue.setTextIfDifferentSilently(tag.value.toString())
    etTagName.bindAfterTextChanged { tag.name = it; onChanged() }
    etTagValue.bindAfterTextChanged { tag.value = it.toIntOrNull() ?: 0; onChanged() }

    etTagDisplay.setTextIfDifferentSilently(tag.displayName)
    etTagDisplay.bindAfterTextChanged { tag.displayName = it; onChanged() }

    val categories = listOf(
        TagCategory.HEALTH to "Здоровье",
        TagCategory.ITEM to "Предмет",
        TagCategory.JOURNAL to "Журнал"
    )
    val displayList = categories.map { it.second }
    val catAdapter = ArrayAdapter(
        context, android.R.layout.simple_spinner_item, displayList
    )
    catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

    spinnerCategory.onItemSelectedListener = null
    spinnerCategory.adapter = catAdapter

    val currentCatIdx = categories.indexOfFirst { it.first == tag.category }.coerceAtLeast(0)
    spinnerCategory.setSelection(currentCatIdx)

    fun applyValueHint(cat: TagCategory) {
        etTagValue.hint = when (cat) {
            TagCategory.HEALTH -> "Изменение"
            TagCategory.ITEM -> "Количество"
            TagCategory.JOURNAL -> "Знач."
        }
    }
    applyValueHint(tag.category)

    spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?, view: View?, pos: Int, id: Long
        ) {
            val newCat = categories.getOrNull(pos)?.first ?: TagCategory.JOURNAL
            applyValueHint(newCat)
            if (tag.category != newCat) {
                tag.category = newCat
                if (newCat == TagCategory.ITEM && tag.value == 0) {
                    tag.value = 1
                    etTagValue.setTextIfDifferentSilently("1")
                }
                onChanged()
            }
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    cbVisible.setOnCheckedChangeListener(null)
    cbVisible.isChecked = tag.visible
    cbVisible.setOnCheckedChangeListener { _, isChecked ->
        tag.visible = isChecked
        onChanged()
    }

    btnDeleteTag.setOnClickListener { onDelete() }

    container.addView(tagView)
}