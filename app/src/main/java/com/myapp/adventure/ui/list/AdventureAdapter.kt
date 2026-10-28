package com.myapp.adventure.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.databinding.ItemAdventureBinding

class AdventureAdapter(
    private val adventures: List<Adventure>,
    private val onItemClick: (Adventure, Int) -> Unit   // добавлена позиция
) : RecyclerView.Adapter<AdventureAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAdventureBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdventureBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val adventure = adventures[position]
        holder.binding.tvTitle.text = adventure.title
        holder.binding.tvAuthor.text = adventure.author
        holder.binding.tvDescription.text = adventure.description
        holder.itemView.setOnClickListener {
            onItemClick(adventure, position)
        }
    }

    override fun getItemCount(): Int = adventures.size
}