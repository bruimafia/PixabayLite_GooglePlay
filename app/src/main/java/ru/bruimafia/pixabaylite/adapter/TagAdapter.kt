package ru.bruimafia.pixabaylite.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.bruimafia.pixabaylite.R

class TagAdapter(private val list: MutableList<String>) : RecyclerView.Adapter<TagAdapter.TagViewHolder>() {

    var onClickTagListener: ((String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        return TagViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_tag, parent, false))
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.tag.text = list[position]
        holder.tag.setOnClickListener {
            onClickTagListener?.invoke(list[position])
        }
    }

    inner class TagViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tag: TextView = view.findViewById(R.id.tv_title)
    }

}