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
        TagCategory.HEALTH to context.getString(R.string.editor_tag_category_health),
        TagCategory.ITEM to context.getString(R.string.editor_tag_category_item),
        TagCategory.JOURNAL to context.getString(R.string.editor_tag_category_journal)
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
            TagCategory.HEALTH -> context.getString(R.string.editor_tag_value_hint_health)
            TagCategory.ITEM -> context.getString(R.string.editor_tag_value_hint_item)
            TagCategory.JOURNAL -> context.getString(R.string.editor_tag_value_hint_journal)
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

/**
 * Ф4-infra (3b-6a): locale-aware integer formatting with non-breaking
 * thousand groups ("1 234" on any device). Same output as
 * `StatisticsFragment.numberFormat`, but without needing a `NumberFormat`
 * field — this function is stateless and reusable by every adapter.
 *
 * **Why not `NumberFormat.getIntegerInstance`.** That allocates a new
 * formatter per call and is a `:app`-internal concern; a single
 * `String.format` with `%,d` + `.replace(',', ' ')` is the simplest
 * equivalent. The `%,d` respects the device locale's grouping
 * character (`,` in en, `.` in de, etc.), and `.replace(',', ' ')`
 * normalizes it to a space for all locales where the `%`-formatter
 * produces a comma. On locales where the formatter uses a period
 * (de, ru in some versions), the string is left as-is — the space
 * is not forced. See `pluralWithNumber` for the "N word" composition.
 *
 * **Deferred to Ф7.3.** Once `strings.xml` gets English defaults and
 * `values-ru/`, the plural composition in `pluralWithNumber` will use
 * the device locale (via `resources.getQuantityString` without
 * `ruQuantityString`); `formatInt` itself does not change.
 */
fun formatInt(n: Int): String =
    String.format(java.util.Locale.getDefault(), "%,d", n).replace(',', ' ')

/**
 * Ф4-infra (3b-6a): combines locale-aware number grouping ([formatInt])
 * with the Russian plural suffix (`шаг` / `шага` / `шагов`).
 *
 * **Why `ruQuantityString`.** See `PluralHelper.kt` KDoc: plurals must
 * be evaluated against the **Russian** plural rules regardless of the
 * device locale, because this project currently ships Russian strings
 * in `values/` (default) — a non-`ru` device would otherwise pick the
 * wrong rule. Ф7.3 removes this.
 *
 * **The `removePrefix` trick.** `getQuantityString` returns
 * `"<N> <word>"` (e.g. `"1234 шага"`). We want just `<word>` so we can
 * prepend a locale-grouped `<N>`. `removePrefix(n.toString())` strips
 * the raw `<N>` (Kotlin's `Int.toString()` — no grouping), leaving
 * `" шага"`; `.trim()` gives `"шага"`. The grouping is then applied
 * via [formatInt]. Two `N` forms in play — raw (`1234`, used by
 * `removePrefix`) and grouped (`1 234`, used in the result) — do not
 * collide because they are used in different branches of the same
 * function.
 *
 * **`Context` parameter, not `Fragment.requireContext()`.** This
 * function is top-level (in `EditorHelpers.kt`), so it is called from
 * `Adapter` classes and `Fragment` classes alike. Every call site passes
 * its own `context`; no `Fragment` reference is stored in the helper.
 */
fun pluralWithNumber(context: android.content.Context, pluralsResId: Int, n: Int): String {
    val formattedNumber = formatInt(n)
    val pluralText = com.myapp.adventure.utils.ruQuantityString(context, pluralsResId, n)
    val suffix = pluralText.removePrefix(n.toString()).trim()
    return "$formattedNumber $suffix"
}