package com.myapp.adventure.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.myapp.adventure.databinding.ItemAdventureBinding
import com.myapp.adventure.databinding.ItemLoadAdventureBinding
import com.myapp.adventure.engine.ir.Adventure

/**
 * Adapter for the adventure library list.
 *
 * b1 (Data path cleanup): takes `:engine/ir/Adventure` directly
 * instead of `:app/data/Adventure`. Displayed fields (`title`,
 * `author`, `description`) are identical in both models; the
 * migration removes a lossy Gson round-trip and lets future IR-only
 * fields (`is_ending`, `gate`, …) reach any consumer of this adapter
 * without a second model in between.
 */
class AdventureAdapter(
    private val adventures: List<Adventure>,
    private val onItemClick: (Adventure, Int) -> Unit,
    private val onLoadClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ADVENTURE = 0
        private const val TYPE_LOAD = 1
    }

    inner class AdventureViewHolder(val binding: ItemAdventureBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class LoadViewHolder(val binding: ItemLoadAdventureBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        if (position == adventures.size) TYPE_LOAD else TYPE_ADVENTURE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_LOAD) {
            val binding = ItemLoadAdventureBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            LoadViewHolder(binding)
        } else {
            val binding = ItemAdventureBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            AdventureViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AdventureViewHolder -> {
                val adventure = adventures[position]
                holder.binding.tvTitle.text = adventure.title
                holder.binding.tvAuthor.text = adventure.author
                holder.binding.tvDescription.text = adventure.description
                holder.itemView.setOnClickListener {
                    onItemClick(adventure, position)
                }
            }
            is LoadViewHolder -> {
                holder.itemView.setOnClickListener { onLoadClick() }
            }
        }
    }

    override fun getItemCount(): Int = adventures.size + 1
}