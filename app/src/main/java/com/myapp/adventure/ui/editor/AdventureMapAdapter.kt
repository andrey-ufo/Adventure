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
                val ctx = holder.root.context
                val stats = StringBuilder(pluralWithNumber(ctx, R.plurals.word_scenes, item.sceneCount))
                if (item.wordCount > 0) {
                    stats.append(" · ")
                    stats.append(formatInt(item.wordCount))
                    stats.append(" ")
                    stats.append(ctx.getString(R.string.word_words_short))
                }
                holder.stats.text = stats.toString()
                holder.root.setOnClickListener { onChapterClick(item.index) }
            }

            is MapItem.SceneRow -> {
                holder as SceneViewHolder
                val scene = item.scene
                val ctx = holder.root.context
                holder.title.text = scene.title.ifBlank { ctx.getString(R.string.label_without_title) }

                // Badges: 🏁 for chapter end, 💬 for non-empty choices
                val badges = mutableListOf<String>()
                if (scene.isChapterEnd) badges.add("🏁")
                if (scene.choices.isNotEmpty()) badges.add("💬 ${scene.choices.size}")
                holder.badges.text = badges.joinToString(" ")

                // Transitions
                holder.transitionsContainer.removeAllViews()
                val inflater = LayoutInflater.from(holder.root.context)

                if (item.transitions.isEmpty()) {
                    val tv = TextView(holder.root.context).apply {
                        text = ctx.getString(R.string.map_no_transitions)
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
                            tr.missing -> "#F44336"    // red
                            tr.condition != null -> "#FF9800"  // orange
                            else -> "#4CAF50"          // green
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
                val ctx = holder.root.context
                val minutes = ((item.steps + 99) / 100).coerceAtLeast(1)
                val timeText = when {
                    item.steps == 0 -> ctx.getString(R.string.map_gate_opens_immediately)
                    minutes < 60 -> ctx.getString(R.string.map_gate_approximate_minutes_fmt, minutes)
                    else -> {
                        val hours = minutes / 60
                        val mins = minutes % 60
                        if (mins == 0) {
                            ctx.getString(R.string.map_gate_approximate_hours_fmt, hours)
                        } else {
                            ctx.getString(R.string.map_gate_approximate_hours_minutes_fmt, hours, mins)
                        }
                    }
                }
                val stepsText = pluralWithNumber(ctx, R.plurals.steps, item.steps)
                holder.text.text = ctx.getString(R.string.map_gate_text_fmt, stepsText, timeText)
                holder.text.setTextColor(
                    if (item.steps == 0) Color.parseColor("#FF9800")
                    else Color.parseColor("#FFC107")
                )
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // Ф4-infra (3b-6a): `formatInt` and the scene plural are now
    // top-level in `EditorHelpers.kt` (same package) and in
    // `strings.xml` (`<plurals name="word_scenes">`). The local copies
    // are deleted to keep one source of truth.
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
    val targetSceneUid: String?,    // null when the target scene was not found
    val targetTitle: String,
    val condition: String?,
    val missing: Boolean
)