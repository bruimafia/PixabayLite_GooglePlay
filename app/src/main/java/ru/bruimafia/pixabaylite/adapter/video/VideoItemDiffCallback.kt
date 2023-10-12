package ru.bruimafia.pixabaylite.adapter.video

import androidx.recyclerview.widget.DiffUtil
import ru.bruimafia.pixabaylite.model.video.Video


class VideoItemDiffCallback : DiffUtil.ItemCallback<Video>() {
    override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean {
        return oldItem == newItem
    }
}