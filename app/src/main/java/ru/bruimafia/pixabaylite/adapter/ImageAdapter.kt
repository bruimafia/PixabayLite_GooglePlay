package ru.bruimafia.pixabaylite.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.databinding.ItemPostBinding
import ru.bruimafia.pixabaylite.model.Image
import kotlin.math.pow
import kotlin.math.roundToInt

import com.squareup.picasso.Picasso

class ImageAdapter(private var list: MutableList<Image> = mutableListOf()) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    lateinit var onReachEndListener: OnReachEndListener
    lateinit var onSaveButtonListener: OnSaveButtonListener
    lateinit var onSearchTagListener: OnSearchTagListener

    interface OnReachEndListener {
        fun onLoad()
    }

    interface OnSaveButtonListener {
        fun onSave(image: Image, position: Int)
    }

    interface OnSearchTagListener {
        fun onSearch(title: String)
    }

    fun setSearchTagListener(listener: OnSearchTagListener) {
        onSearchTagListener = listener
    }

    fun setReachEndListener(listener: OnReachEndListener) {
        onReachEndListener = listener
    }

    fun setSaveButtonListener(listener: OnSaveButtonListener) {
        onSaveButtonListener = listener
    }

    fun setList(newList: MutableList<Image>) {
        list = newList
        notifyDataSetChanged()
    }

    fun updateList(newList: MutableList<Image>) {
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(list[position])

        if (list.size >= 10 && position == list.size - 3)
            onReachEndListener.onLoad()
    }

    override fun getItemCount() = list.size

    inner class ImageViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(image: Image) {
            binding.apply {
                tvUserName.text = image.user
                tvLike.text = image.likes.toString()
                tvFavorite.text = image.collections.toString()
                tvDownloads.text = root.resources.getString(R.string.downloads, image.downloads)
                tvSize.text = root.resources.getString(R.string.size, image.imageWidth, image.imageHeight)
                tvWeight.text = root.resources.getString(R.string.weight, byteTo(image.imageSize))

                if (image.userImageURL.isNotEmpty())
                    Picasso.get().load(image.userImageURL).into(ivUserPhoto)

                if (image.webformatURL.isNotEmpty())
                    Picasso.get().load(image.webformatURL).into(ivImage)

                val tagAdapter = TagAdapter(image.tags.split(", ").toMutableList())
                recycler.adapter = tagAdapter

                btnSave.setOnClickListener { onSaveButtonListener.onSave(image, itemId.toInt()) }

                tagAdapter.setClickTagListener(object : TagAdapter.OnClickTagListener {
                    override fun onClick(title: String) = onSearchTagListener.onSearch(title)
                })
            }
        }

    }

}

fun byteTo(bytes: Int) = (bytes.toDouble() / (1024 * 1024)).roundTo(2)

fun Double.roundTo(numFractionDigits: Int): Double {
    val factor = 10.0.pow(numFractionDigits.toDouble())
    return (this * factor).roundToInt() / factor
}