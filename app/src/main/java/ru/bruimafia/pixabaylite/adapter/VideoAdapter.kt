package ru.bruimafia.pixabaylite.adapter

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.yandex.mobile.ads.nativeads.template.NativeBannerView
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.databinding.ItemPostVideoBinding
import ru.bruimafia.pixabaylite.databinding.LayoutAdBinding
import ru.bruimafia.pixabaylite.model.video.Video
import ru.bruimafia.pixabaylite.util.Constants
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import com.yandex.mobile.ads.common.AdRequestError as YandexAdRequestError
import com.yandex.mobile.ads.common.ImpressionData as YandexImpressionData
import com.yandex.mobile.ads.nativeads.NativeAd as YandexNativeAd
import com.yandex.mobile.ads.nativeads.NativeAdEventListener as YandexNativeAdEventListener
import com.yandex.mobile.ads.nativeads.NativeAdLoadListener as YandexNativeAdLoadListener
import com.yandex.mobile.ads.nativeads.NativeAdLoader as YandexNativeAdLoader
import com.yandex.mobile.ads.nativeads.NativeAdRequestConfiguration as YandexNativeAdRequestConfiguration

class VideoAdapter(private var list: MutableList<Video> = mutableListOf()) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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
        fun onSave(video: Video, position: Int)
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

    fun setList(newList: MutableList<Video>) {
        list = newList
        notifyDataSetChanged()
    }

    fun updateList(newList: MutableList<Video>) {
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == ITEM_VIEW) {
            val view = ItemPostVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            VideoViewHolder(view)
        } else {
            val view = LayoutAdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AdViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == ITEM_VIEW) {
            var pos = if (SharedPreferencesManager.isFullVersion) position else position - (position / ITEM_FEED_COUNT)
            if (pos >= list.size) pos = list.size - 1 // иначе ошибка при быстром пролистывании
            (holder as VideoViewHolder).bind(list[pos])
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

    inner class VideoViewHolder(private val binding: ItemPostVideoBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: Video) {
            binding.apply {
                tvUserName.text = video.user
                tvLike.text = video.likes.toString()
                tvViews.text = video.views.toString()
                tvDuration.text = root.resources.getString(R.string.duration, video.duration)
                tvDownloads.text = root.resources.getString(R.string.downloads, video.downloads)

                Picasso.get().load("https://i.vimeocdn.com/video/${video.picture_id}_640x360.jpg").into(previewImageView)
                try {
                    videoView.setVideoURI(Uri.parse(video.videos.small.url))
                    videoView.setOnClickListener {
                        if (!videoView.isPlaying) {
                            previewImageView.visibility = View.GONE
                            playImageView.visibility = View.GONE
                            videoView.seekTo(0)
                            videoView.start()
                        } else {
                            videoView.pause()
                            previewImageView.visibility = View.VISIBLE
                            playImageView.visibility = View.VISIBLE
                        }
                    }
                    videoView.setOnCompletionListener {
                        previewImageView.visibility = View.VISIBLE
                        playImageView.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    Log.d(Constants.TAG, "videoView exception = ${e.message.toString()}")
                }

                if (video.videos.large.url != "") {
                    tvSize.text = root.resources.getString(R.string.size, video.videos.large.width, video.videos.large.height)
                    tvWeight.text = root.resources.getString(R.string.weight, byteTo(video.videos.large.size))
                } else {
                    tvSize.text = root.resources.getString(R.string.size, video.videos.medium.width, video.videos.medium.height)
                    tvWeight.text = root.resources.getString(R.string.weight, byteTo(video.videos.medium.size))
                }

                if (video.userImageURL.isNotEmpty())
                    Picasso.get().load(video.userImageURL).into(ivUserPhoto)


                val tagAdapter = TagAdapter(video.tags.split(", ").toMutableList())
                recycler.adapter = tagAdapter

                btnSave.setOnClickListener { onSaveButtonListener.onSave(video, itemId.toInt()) }

                tagAdapter.setClickTagListener(object : TagAdapter.OnClickTagListener {
                    override fun onClick(title: String) = onSearchTagListener.onSearch(title)
                })

            }

        }

    }

    inner class AdViewHolder(private val binding: LayoutAdBinding) : RecyclerView.ViewHolder(binding.root) {

        private var nativeAdLoader: YandexNativeAdLoader? = null
        private val eventLogger = NativeAdEventLogger()

        fun bind() {
            nativeAdLoader = YandexNativeAdLoader(binding.root.context)
            nativeAdLoader?.setNativeAdLoadListener(eventLogger)
            nativeAdLoader?.loadAd(
                YandexNativeAdRequestConfiguration
                    .Builder(binding.root.resources.getString(R.string.ads_yandex_nativeAd_video_id))
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