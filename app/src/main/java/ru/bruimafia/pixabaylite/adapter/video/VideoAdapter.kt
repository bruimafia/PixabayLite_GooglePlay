package ru.bruimafia.pixabaylite.adapter.video

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.nativeads.NativeAd
import com.yandex.mobile.ads.nativeads.NativeAdEventListener
import com.yandex.mobile.ads.nativeads.NativeAdLoadListener
import com.yandex.mobile.ads.nativeads.NativeAdLoader
import com.yandex.mobile.ads.nativeads.NativeAdRequestConfiguration
import com.yandex.mobile.ads.nativeads.template.NativeBannerView
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.adapter.TagAdapter
import ru.bruimafia.pixabaylite.adapter.image.ImageAdapter
import ru.bruimafia.pixabaylite.databinding.ItemPostVideoBinding
import ru.bruimafia.pixabaylite.databinding.LayoutAdBinding
import ru.bruimafia.pixabaylite.model.video.Video
import ru.bruimafia.pixabaylite.util.Constants
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager

class VideoAdapter : ListAdapter<Video, RecyclerView.ViewHolder>(VideoItemDiffCallback()) {

    companion object {
        const val ITEM_VIEW_TYPE = 0
        const val AD_VIEW_TYPE = 1
        const val ITEM_FEED_COUNT = 6
    }

    var onSaveButtonListener: ((Video) -> Unit)? = null
    var onReachEndListener: (() -> Unit)? = null
    var onSearchTagListener: ((String) -> Unit)? = null

    fun updateList(newPart: MutableList<Video>) {
        val updatedList = currentList.toMutableList()
        updatedList.addAll(newPart)
        submitList(updatedList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE ->
                VideoViewHolder(binding = ItemPostVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

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
            (holder as VideoViewHolder).bind(currentList[pos])
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
                    tvWeight.text = root.resources.getString(R.string.weight, ImageAdapter.byteTo(video.videos.large.size))
                } else {
                    tvSize.text = root.resources.getString(R.string.size, video.videos.medium.width, video.videos.medium.height)
                    tvWeight.text = root.resources.getString(R.string.weight, ImageAdapter.byteTo(video.videos.medium.size))
                }

                if (video.userImageURL.isNotEmpty())
                    Picasso.get().load(video.userImageURL).into(ivUserPhoto)


                val tagAdapter = TagAdapter(video.tags.split(", ").toMutableList())
                recycler.adapter = tagAdapter

                btnSave.setOnClickListener {
                    onSaveButtonListener?.invoke(video)
                }

                tagAdapter.onClickTagListener = {
                    onSearchTagListener?.invoke(it)
                }

            }

        }
    }

    inner class AdViewHolder(private val binding: LayoutAdBinding) : RecyclerView.ViewHolder(binding.root) {
        private var nativeAdLoader: NativeAdLoader? = null
        private val eventLogger = NativeAdEventLogger()

        fun bind() {
            nativeAdLoader = NativeAdLoader(binding.root.context)
            nativeAdLoader?.setNativeAdLoadListener(eventLogger)
            nativeAdLoader?.loadAd(
                NativeAdRequestConfiguration
                    .Builder(binding.root.resources.getString(R.string.ads_yandex_nativeAd_video_id))
                    .setShouldLoadImagesAutomatically(true)
                    .build()
            )
        }

        private fun bindNative(ad: NativeAd) {
            ad.setNativeAdEventListener(eventLogger)
            val adView = LayoutInflater.from(binding.root.context).inflate(R.layout.yandex_native_ad, null) as NativeBannerView
            binding.adLayout.removeAllViews()
            binding.adLayout.addView(adView)
            adView.setAd(ad)
        }

        private inner class NativeAdEventLogger : NativeAdLoadListener, NativeAdEventListener {
            override fun onAdLoaded(ad: NativeAd) {
                bindNative(ad)
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
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

            override fun onImpression(data: ImpressionData?) {
                Log.d(Constants.TAG, "Impression: ${data?.rawData}")
            }
        }
    }

}
