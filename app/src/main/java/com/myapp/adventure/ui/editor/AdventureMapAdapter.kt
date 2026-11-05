package com.myapp.adventure.ui.editor

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene

class AdventureMapAdapter(
    private val items: List<MapItem>,
    private val onChapterClick: (Int) -> Unit,
    private val onSceneClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CHAPTER_HEADER = 0
        private const val TYPE_SCENE_ROW = 1
        private const val TYPE_STEPS_GATE = 2
    }

    inner class ChapterViewHolder(val root: View) : RecyclerView.ViewHolder(root) {
        val chevron: TextView = root.findViewById(R.id.tv_chapter_chevron)
        val title: TextView = root.findViewById(R.id.tv_chapter_title)
        val stats: TextView = root.findViewById(R.id.tv_chapter_stats)
    }

    inner class SceneViewHolder(val root: View) : RecyclerView.ViewHolder(root) {
        val title: TextView = root.findViewById(R.id.tv_scene_title)
        val badges: TextView = root.findViewById(R.id.tv_scene_badges)
        val transitionsContainer: LinearLayout = root.findViewById(R.id.container_transitions)
    }

    inner class StepsGateViewHolder(val root: View) : RecyclerView.ViewHolder(root) {
        val text: TextView = root.findViewById(R.id.tv_gate_text)
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is MapItem.ChapterHeader -> TYPE_CHAPTER_HEADER
        is MapItem.SceneRow -> TYPE_SCENE_ROW
        is MapItem.StepsGate -> TYPE_STEPS_GATE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CHAPTER_HEADER -> ChapterViewHolder(
                inflater.inflate(R.layout.item_map_chapter_header, parent, false)
            )
            TYPE_STEPS_GATE -> StepsGateViewHolder(
                inflater.inflate(R.layout.item_map_steps_gate, parent, false)
            )
            else -> SceneViewHolder(
                inflater.inflate(R.layout.item_map_scene_row, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MapItem.ChapterHeader -> {
                holder as ChapterViewHolder
                holder.chevron.text = if (item.expanded) "▼" else "▶"
                holder.title.text = item.title
                val stats = StringBuilder("${item.sceneCount} ${formatScenes(item.sceneCount)}")
                if (item.wordCount > 0) {
                    stats.append(" · ${formatInt(item.wordCount)} сл.")
                }
                holder.stats.text = stats.toString()
                holder.root.setOnClickListener { onChapterClick(item.index) }
            }

            is MapItem.SceneRow -> {
                holder as SceneViewHolder
                val scene = item.scene
                holder.title.text = scene.title.ifBlank { "(без названия)" }

                // Бейджи: 🏁 если конец главы, 💬 если много выборов
                val badges = mutableListOf<String>()
                if (scene.isChapterEnd) badges.add("🏁")
                if (scene.choices.isNotEmpty()) badges.add("💬 ${scene.choices.size}")
                holder.badges.text = badges.joinToString(" ")

                // Переходы
                holder.transitionsContainer.removeAllViews()
                val inflater = LayoutInflater.from(holder.root.context)

                if (item.transitions.isEmpty()) {
                    val tv = TextView(holder.root.context).apply {
                        text = "(нет переходов)"
                        setTextColor(Color.parseColor("#9E9E9E"))
                        textSize = 11f
                    }
                    holder.transitionsContainer.addView(tv)
                } else {
                    for (tr in item.transitions) {
                        val chip = inflater.inflate(
                            R.layout.item_map_transition_chip,
                            holder.transitionsContainer, false
                        ) as TextView

                        val labelPrefix = if (tr.missing) "⚠️ " else "→ "
                        chip.text = "$labelPrefix${tr.targetTitle}"

                        val color = when {
                            tr.missing -> "#F44336"    // красный
                            tr.condition != null -> "#FF9800"  // оранжевый
                            else -> "#4CAF50"          // зелёный
                        }
                        chip.setTextColor(Color.parseColor(color))

                        chip.setOnClickListener {
                            if (!tr.missing && tr.targetSceneUid != null) {
                                onSceneClick(tr.targetSceneUid)
                            }
                        }
                        holder.transitionsContainer.addView(chip)
                    }
                }

                holder.root.setOnClickListener { onSceneClick(scene.uid) }
            }

            is MapItem.StepsGate -> {
                holder as StepsGateViewHolder
                val minutes = ((item.steps + 99) / 100).coerceAtLeast(1)
                val timeText = when {
                    item.steps == 0 -> "откроется сразу"
                    minutes < 60 -> "≈ $minutes мин"
                    else -> {
                        val hours = minutes / 60
                        val mins = minutes % 60
                        if (mins == 0) "≈ $hours ч" else "≈ $hours ч $mins мин"
                    }
                }
                val formatted = String.format(java.util.Locale.getDefault(), "%,d", item.steps).replace(',', ' ')
                holder.text.text = "🚶 ${formatted} шагов ($timeText)"
                holder.text.setTextColor(
                    if (item.steps == 0) Color.parseColor("#FF9800")
                    else Color.parseColor("#FFC107")
                )
            }
        }
    }

    override fun getItemCount(): Int = items.size

    private fun formatInt(n: Int): String =
        String.format(java.util.Locale.getDefault(), "%,d", n).replace(',', ' ')

    private fun formatScenes(n: Int): String = when {
        n % 100 in 11..14 -> "сцен"
        n % 10 == 1 -> "сцена"
        n % 10 in 2..4 -> "сцены"
        else -> "сцен"
    }
}

// ---------- Модель элементов карты ----------

sealed class MapItem {
    data class ChapterHeader(
        val index: Int,
        val title: String,
        val sceneCount: Int,
        val wordCount: Int,
        val expanded: Boolean
    ) : MapItem()

    data class SceneRow(
        val scene: EditorScene,
        val transitions: List<TransitionInfo>
    ) : MapItem()

    data class StepsGate(val steps: Int) : MapItem()
}

data class TransitionInfo(
    val targetSceneUid: String?,    // null, если сцена не найдена
    val targetTitle: String,
    val condition: String?,
    val missing: Boolean
)