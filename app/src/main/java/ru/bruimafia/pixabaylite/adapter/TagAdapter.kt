package ru.bruimafia.pixabaylite.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.bruimafia.pixabaylite.R

class TagAdapter(private val list: MutableList<String>) :
    RecyclerView.Adapter<TagAdapter.TagViewHolder>() {

    lateinit var onClickTagListener: OnClickTagListener

    interface OnClickTagListener {
        fun onClick(title: String)
    }

    fun setClickTagListener(listener: OnClickTagListener) {
        onClickTagListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tag, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.tag.text = list[position]

        holder.tag.setOnClickListener { onClickTagListener.onClick(list[position]) }
    }

    override fun getItemCount() = list.size

    inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tag: TextView = itemView.findViewById(R.id.tv_title)
    }

}