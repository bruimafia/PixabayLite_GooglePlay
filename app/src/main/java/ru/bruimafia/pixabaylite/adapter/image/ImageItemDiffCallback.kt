package ru.bruimafia.pixabaylite.adapter.image

import androidx.recyclerview.widget.DiffUtil
import ru.bruimafia.pixabaylite.model.image.Image
import ru.bruimafia.pixabaylite.model.video.Video


class ImageItemDiffCallback : DiffUtil.ItemCallback<Image>() {
    override fun areItemsTheSame(oldItem: Image, newItem: Image): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Image, newItem: Image): Boolean {
        return oldItem == newItem
    }
}