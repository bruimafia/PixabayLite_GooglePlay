package ru.bruimafia.pixabaylite.adapter.image

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.stfalcon.imageviewer.StfalconImageViewer
import com.yandex.mobile.ads.nativeads.template.NativeBannerView
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.adapter.TagAdapter
import ru.bruimafia.pixabaylite.databinding.ItemPostImageBinding
import ru.bruimafia.pixabaylite.databinding.LayoutAdBinding
import ru.bruimafia.pixabaylite.model.image.Image
import ru.bruimafia.pixabaylite.util.Constants
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import kotlin.math.pow
import kotlin.math.roundToInt
import com.yandex.mobile.ads.common.AdRequestError as YandexAdRequestError
import com.yandex.mobile.ads.common.ImpressionData as YandexImpressionData
import com.yandex.mobile.ads.nativeads.NativeAd as YandexNativeAd
import com.yandex.mobile.ads.nativeads.NativeAdEventListener as YandexNativeAdEventListener
import com.yandex.mobile.ads.nativeads.NativeAdLoadListener as YandexNativeAdLoadListener
import com.yandex.mobile.ads.nativeads.NativeAdLoader as YandexNativeAdLoader
import com.yandex.mobile.ads.nativeads.NativeAdRequestConfiguration as YandexNativeAdRequestConfiguration

class ImageAdapter : ListAdapter<Image, RecyclerView.ViewHolder>(ImageItemDiffCallback()) {

    companion object {
        const val ITEM_VIEW_TYPE = 0
        const val AD_VIEW_TYPE = 1
        const val ITEM_FEED_COUNT = 6

        fun byteTo(bytes: Int) = (bytes.toDouble() / (1024 * 1024)).roundTo(2)

        private fun Double.roundTo(numFractionDigits: Int): Double {
            val factor = 10.0.pow(numFractionDigits.toDouble())
            return (this * factor).roundToInt() / factor
        }
    }

    var onSaveButtonListener: ((Image) -> Unit)? = null
    var onReachEndListener: (() -> Unit)? = null
    var onSearchTagListener: ((String) -> Unit)? = null

    fun updateList(newPart: MutableList<Image>) {
        val updatedList = currentList.toMutableList()
        updatedList.addAll(newPart)
        submitList(updatedList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE ->
                ImageViewHolder(binding = ItemPostImageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            AD_VIEW_TYPE ->
                AdViewHolder(binding = LayoutAdBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            else ->
                throw IllegalArgumentException("Invalid type of view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == ITEM_VIEW_TYPE) {
            var pos = if (SharedPreferencesManager.isFullVersion) position else position - (position / ITEM_FEED_COUNT)
            if (pos >= currentList.size) pos = currentList.size - 1 // иначе ошибка при быстром пролистывании
            (holder as ImageViewHolder).bind(currentList[pos])
        } else
            (holder as AdViewHolder).bind()

        if (currentList.size >= 10 && position == currentList.size - 3)
            onReachEndListener?.invoke()
    }

    override fun getItemViewType(position: Int): Int {
        return if ((position + 1) % ITEM_FEED_COUNT == 0 && !SharedPreferencesManager.isFullVersion)
            AD_VIEW_TYPE
        else
            ITEM_VIEW_TYPE
    }


    inner class ImageViewHolder(private val binding: ItemPostImageBinding) : RecyclerView.ViewHolder(binding.root) {
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

                btnSave.setOnClickListener {
                    onSaveButtonListener?.invoke(image)
                }

                tagAdapter.onClickTagListener = {
                    onSearchTagListener?.invoke(it)
                }

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
                    .Builder(binding.root.resources.getString(R.string.ads_yandex_nativeAd_image_id))
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
                Log.d(Constants.TAG, "Native ad failed to load with code ${error.code}: ${error.description}")
            }

            override fun onAdClicked() {
                Log.d(Constants.TAG, "Native ad clicked")
            }

            override fun onLeftApplication() {
                Log.d(Constants.TAG, "Left application")
            }

            override fun onReturnedToApplication() {
                Log.d(Constants.TAG, "Returned to application")
            }

            override fun onImpression(data: YandexImpressionData?) {
                Log.d(Constants.TAG, "Impression: ${data?.rawData}")
            }
        }

    }

}