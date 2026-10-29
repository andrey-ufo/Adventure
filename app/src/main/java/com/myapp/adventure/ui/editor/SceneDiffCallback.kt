package com.myapp.adventure.ui.editor

import androidx.recyclerview.widget.DiffUtil
import com.myapp.adventure.data.EditorScene

class SceneDiffCallback(
    private val oldList: List<EditorScene>,
    private val newList: List<EditorScene>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
        oldList[oldPos].uid == newList[newPos].uid

    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
        oldList[oldPos] == newList[newPos]
}