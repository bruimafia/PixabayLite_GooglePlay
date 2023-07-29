package ru.bruimafia.pixabaylite.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.stfalcon.imageviewer.StfalconImageViewer
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.databinding.ItemPostBinding
import ru.bruimafia.pixabaylite.databinding.LayoutAdBinding
import ru.bruimafia.pixabaylite.model.Image
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import kotlin.math.pow
import kotlin.math.roundToInt

import com.yandex.mobile.ads.common.AdRequestError as YandexAdRequestError
import com.yandex.mobile.ads.nativeads.template.NativeBannerView
import com.yandex.mobile.ads.nativeads.NativeAd as YandexNativeAd
import com.yandex.mobile.ads.nativeads.NativeAdLoadListener as YandexNativeAdLoadListener
import com.yandex.mobile.ads.nativeads.NativeAdLoader as YandexNativeAdLoader
import com.yandex.mobile.ads.nativeads.NativeAdRequestConfiguration as YandexNativeAdRequestConfiguration
import com.yandex.mobile.ads.nativeads.NativeAdEventListener as YandexNativeAdEventListener
import com.yandex.mobile.ads.common.ImpressionData as YandexImpressionData

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
            var pos = if (SharedPreferencesManager.isFullVersion) position else position - (position / ITEM_FEED_COUNT)
            if (pos >= list.size) pos = list.size - 1 // иначе ошибка при быстром пролистывании
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

                ivImage.setOnClickListener {
                    StfalconImageViewer.Builder(binding.root.context, listOf(image)) { view, image ->
                        Picasso.get().load(image.fullHDURL).placeholder(R.drawable.logo).into(view)
                    }.withHiddenStatusBar(false)
                        .withTransitionFrom(ivImage)
                        .withBackgroundColorResource(R.color.base)
                        .show()
                }
            }
        }

    }

    inner class AdViewHolder(private val binding: LayoutAdBinding) : RecyclerView.ViewHolder(binding.root) {

        /*fun bind() {
            binding.apply {
                val builder = AdLoader.Builder(root.context, root.resources.getString(R.string.ads_google_nativeAd_id))
                    .forNativeAd { nativeAd ->
                        val adView = LayoutInflater.from(root.context).inflate(R.layout.item_native_ad, null) as NativeAdView
                        populateNativeAdView(nativeAd, adView)
                        adLayout.removeAllViews()
                        adLayout.addView(adView)
                    }

                val adLoader = builder
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d("ADS", "Error (Ad Failed To Load): ${adError.message}")
                        }
                    })
                    .build()

                adLoader.loadAd(AdRequest.Builder().build())
            }
        }
        */

        private var nativeAdLoader: YandexNativeAdLoader? = null
        private val eventLogger = NativeAdEventLogger()
        fun bind() {
            nativeAdLoader = YandexNativeAdLoader(binding.root.context)
            nativeAdLoader?.setNativeAdLoadListener(eventLogger)
            nativeAdLoader?.loadAd(
                YandexNativeAdRequestConfiguration
                    .Builder(binding.root.resources.getString(R.string.ads_yandex_nativeAd_id))
                    .setShouldLoadImagesAutomatically(true)
                    .build()
            )
        }

        private fun bindNative(ad: YandexNativeAd) {
            ad.setNativeAdEventListener(eventLogger)
            val adView = LayoutInflater.from(binding.root.context).inflate(R.layout.yandex_native_ad, null) as NativeBannerView
            binding.adLayout.removeAllViews()
            binding.adLayout.addView(adView)
            adView.setAd(ad)
        }

        private inner class NativeAdEventLogger : YandexNativeAdLoadListener, YandexNativeAdEventListener {
            override fun onAdLoaded(ad: YandexNativeAd) {
                bindNative(ad)
            }

            override fun onAdFailedToLoad(error: YandexAdRequestError) {
                Log.d("ADS", "Native ad failed to load with code ${error.code}: ${error.description}")
            }

            override fun onAdClicked() {
                Log.d("ADS", "Native ad clicked")
            }

            override fun onLeftApplication() {
                Log.d("ADS", "Left application")
            }

            override fun onReturnedToApplication() {
                Log.d("ADS", "Returned to application")
            }

            override fun onImpression(data: YandexImpressionData?) {
                Log.d("ADS", "Impression: ${data?.rawData}")
            }
        }

    }

}

fun byteTo(bytes: Int) = (bytes.toDouble() / (1024 * 1024)).roundTo(2)

fun Double.roundTo(numFractionDigits: Int): Double {
    val factor = 10.0.pow(numFractionDigits.toDouble())
    return (this * factor).roundToInt() / factor
}