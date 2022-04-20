package ru.bruimafia.pixabaylite.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.squareup.picasso.Picasso
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.databinding.ItemPostBinding
import ru.bruimafia.pixabaylite.databinding.LayoutAdBinding
import ru.bruimafia.pixabaylite.model.Image
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import kotlin.math.pow
import kotlin.math.roundToInt


class ImageAdapter(private var list: MutableList<Image> = mutableListOf()) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val ITEM_VIEW = 0
    private val AD_VIEW = 1
    private val ITEM_FEED_COUNT = 6

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == ITEM_VIEW) {
            val view = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ImageViewHolder(view)
        } else {
            val view = LayoutAdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AdViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == ITEM_VIEW) {
            val pos = if (SharedPreferencesManager.isFullVersion) position else position - (position / ITEM_FEED_COUNT)
            (holder as ImageViewHolder).bind(list[pos])
        } else
            (holder as AdViewHolder).bind()

        if (list.size >= 10 && position == list.size - 3)
            onReachEndListener.onLoad()
    }

    override fun getItemCount(): Int {
        if (list.size > 0)
            return list.size + (list.size / ITEM_FEED_COUNT)

        return list.size
    }

    override fun getItemViewType(position: Int): Int {
        if ((position + 1) % ITEM_FEED_COUNT == 0 && !SharedPreferencesManager.isFullVersion)
            return AD_VIEW

        return ITEM_VIEW
    }

    /*
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
     */

    inner class ImageViewHolder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {

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

    inner class AdViewHolder(private val binding: LayoutAdBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.apply {
                val builder = AdLoader.Builder(root.context, root.resources.getString(R.string.ads_nativeAd_id))
                    .forNativeAd { nativeAd ->
                        val adView = LayoutInflater.from(root.context).inflate(R.layout.item_native_ad, null) as NativeAdView
                        populateNativeAdView(nativeAd, adView)
                        adLayout.removeAllViews()
                        adLayout.addView(adView)
                    }

                val adLoader = builder
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d("TESTADS", "Error (Ad Failed To Load): ${adError.message}")
                        }
                    })
                    .build()

                adLoader.loadAd(AdRequest.Builder().build())
            }
        }

        private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
            adView.mediaView = adView.findViewById(R.id.ad_media)
            adView.headlineView = adView.findViewById(R.id.ad_headline)
            adView.bodyView = adView.findViewById(R.id.ad_body)
            adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
            adView.iconView = adView.findViewById(R.id.ad_app_icon)
            adView.priceView = adView.findViewById(R.id.ad_price)
            adView.starRatingView = adView.findViewById(R.id.ad_stars)
            adView.storeView = adView.findViewById(R.id.ad_store)
            adView.advertiserView = adView.findViewById(R.id.ad_advertiser)

            (adView.headlineView as TextView).text = nativeAd.headline
            adView.mediaView.setMediaContent(nativeAd.mediaContent)
            if (nativeAd.body == null) {
                adView.bodyView.visibility = View.INVISIBLE
            } else {
                adView.bodyView.visibility = View.VISIBLE
                (adView.bodyView as TextView).text = nativeAd.body
            }

            if (nativeAd.callToAction == null) {
                adView.callToActionView.visibility = View.INVISIBLE
            } else {
                adView.callToActionView.visibility = View.VISIBLE
                (adView.callToActionView as Button).setText(nativeAd.callToAction)
            }

            if (nativeAd.icon == null) {
                adView.iconView.visibility = View.GONE
            } else {
                (adView.iconView as ImageView).setImageDrawable(
                    nativeAd.icon.drawable
                )
                adView.iconView.setVisibility(View.VISIBLE)
            }

            if (nativeAd.price == null) {
                adView.priceView.visibility = View.INVISIBLE
            } else {
                adView.priceView.visibility = View.VISIBLE
                (adView.priceView as TextView).text = nativeAd.price
            }

            if (nativeAd.store == null) {
                adView.storeView.visibility = View.INVISIBLE
            } else {
                adView.storeView.visibility = View.VISIBLE
                (adView.storeView as TextView).text = nativeAd.store
            }

            if (nativeAd.starRating == null) {
                adView.starRatingView.visibility = View.INVISIBLE
            } else {
                (adView.starRatingView as RatingBar).rating = nativeAd.starRating.toFloat()
                adView.starRatingView.visibility = View.VISIBLE
            }

            if (nativeAd.advertiser == null) {
                adView.advertiserView.visibility = View.INVISIBLE
            } else {
                (adView.advertiserView as TextView).text = nativeAd.advertiser
                adView.advertiserView.visibility = View.VISIBLE
            }

            adView.setNativeAd(nativeAd)
        }

    }

}

fun byteTo(bytes: Int) = (bytes.toDouble() / (1024 * 1024)).roundTo(2)

fun Double.roundTo(numFractionDigits: Int): Double {
    val factor = 10.0.pow(numFractionDigits.toDouble())
    return (this * factor).roundToInt() / factor
}