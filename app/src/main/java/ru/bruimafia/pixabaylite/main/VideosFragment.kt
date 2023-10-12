package ru.bruimafia.pixabaylite.main

import android.app.DownloadManager
import android.app.SearchManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.review.ReviewManagerFactory
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.adapter.video.VideoAdapter
import ru.bruimafia.pixabaylite.api.ApiClientVideo
import ru.bruimafia.pixabaylite.api.ApiService
import ru.bruimafia.pixabaylite.databinding.FragmentVideosBinding
import ru.bruimafia.pixabaylite.model.video.Video
import ru.bruimafia.pixabaylite.util.Constants
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import kotlin.math.roundToInt

class VideosFragment : Fragment() {

    private lateinit var bind: FragmentVideosBinding
    private var adapter: VideoAdapter = VideoAdapter()
    private lateinit var disposable: Disposable
    private lateinit var searchView: SearchView

    private var interstitialAd: InterstitialAd? = null
    private var interstitialAdLoader: InterstitialAdLoader? = null
    private var bannerAd: BannerAdView? = null
    private val adSize: BannerAdSize
        get() {
            var adWidthPixels = bind.banner.width
            if (adWidthPixels == 0) {
                adWidthPixels = resources.displayMetrics.widthPixels
            }
            val adWidth = (adWidthPixels / resources.displayMetrics.density).roundToInt()

            return BannerAdSize.stickySize(requireActivity(), adWidth)
        }

    private var order = "popular"
    private var query = ""
    private var page = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bind = DataBindingUtil.inflate(inflater, R.layout.fragment_videos, container, false)
        return bind.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadData(query, order, page)

        adapter.onReachEndListener = {
            bind.progressBarHor.visibility = View.VISIBLE
            loadData(query, order, page)
        }

        adapter.onSaveButtonListener = {
            if (!SharedPreferencesManager.isFullVersion)
                showAd()
            if (it.videos.large.url != "")
                downloadFile(it.videos.large.url.replace("https://player.vimeo.com/external/", ""))
            else
                downloadFile(it.videos.medium.url.replace("https://player.vimeo.com/external/", ""))
        }

        adapter.onSearchTagListener = {
            searchView.setQuery(it, true)
            searchView.isIconified = false
            searchView.clearFocus()
            loadData(it, order, page)
        }

        bind.swipeRefreshLayout.setOnRefreshListener {
            page = 1
            loadData(query, order, page)
        }

        bind.fab.setOnClickListener { bind.recycler.smoothScrollToPosition(0) }

        bind.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0)
                    bind.fab.visibility = View.VISIBLE
                else
                    bind.fab.visibility = View.GONE
            }
        })

        bind.banner.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                bind.banner.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (!SharedPreferencesManager.isFullVersion)
                    bannerAd = loadBannerAd(adSize)
            }
        })

        interstitialAdLoader = InterstitialAdLoader(requireActivity()).apply {
            setAdLoadListener(object : InterstitialAdLoadListener {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    this@VideosFragment.interstitialAd = interstitialAd
                }

                override fun onAdFailedToLoad(adRequestError: AdRequestError) {}
            })
        }
        loadInterstitialAd()
    }

    // меню и строка поиска
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val searchManager = requireActivity().getSystemService(AppCompatActivity.SEARCH_SERVICE) as SearchManager
        searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE
        searchView.queryHint = getString(R.string.menu_search)
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(newText: String?): Boolean {
                query = newText ?: ""
                page = 1
                bind.progressBar.visibility = View.VISIBLE
                loadData(query, order, page)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                query = newText ?: ""
                page = 1
                bind.progressBar.visibility = View.VISIBLE
                loadData(query, order, page)
                return false
            }
        })

        super.onCreateOptionsMenu(menu, inflater)
    }

    // выбор пунктов меню
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_sort) {
            when (order) {
                "popular" -> {
                    order = "latest"
                    page = 1
                    item.setIcon(R.drawable.ic_popular)
                    showMessage(getString(R.string.sort_by_latest))
                }

                "latest" -> {
                    order = "popular"
                    page = 1
                    item.setIcon(R.drawable.ic_latest)
                    showMessage(getString(R.string.sort_by_popular))
                }
            }
            loadData(query, order, page)
        }
        return false
    }

    // показ сообщения
    private fun showMessage(msg: String?) {
        Snackbar.make(bind.root.rootView, "$msg", Snackbar.LENGTH_LONG).show()
    }

    // загрузка данных
    private fun loadData(q: String, order: String, page: Int) {
        disposable = ApiClientVideo.getClient("https://pixabay.com/api/videos/")
            .create(ApiService::class.java)
            .getVideos(q, order, page)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { videosResponse ->
                    if (page <= 1)
                        showData(videosResponse.videos as MutableList<Video>)
                    else
                        showLoadedData(videosResponse.videos as MutableList<Video>)
                },
                { throwable -> showMessage(throwable.message) }
            )
    }

    // показ данных
    private fun showData(list: MutableList<Video>) {
        adapter.submitList(list)
        bind.recycler.adapter = adapter
        page++
        bind.progressBar.visibility = View.GONE
        bind.swipeRefreshLayout.isRefreshing = false
    }

    // показ подгруженных данных
    private fun showLoadedData(list: MutableList<Video>) {
        adapter.updateList(list)
        page++
        bind.progressBarHor.visibility = View.GONE
        requestReview()
    }

    // окно выставления оценки и отзыва
    private fun requestReview() {
        val reviewManager = ReviewManagerFactory.create(bind.root.context)
        val requestReviewFlow = reviewManager.requestReviewFlow()

        requestReviewFlow.addOnCompleteListener { request ->
            if (request.isSuccessful) {
                val reviewInfo = request.result
                val flow = reviewManager.launchReviewFlow(requireActivity(), reviewInfo)
                flow.addOnCompleteListener {
//                    SharedPreferencesManager.isPlayRating = true
                }
            }
        }
    }

    // скачивание файла
    private fun downloadFile(url: String) {
        showMessage(getString(R.string.download_video_started))
        disposable = Retrofit.Builder()
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .baseUrl("https://player.vimeo.com/external/")
            .build()
            .create(ApiService::class.java)
            .downloadVideo(url)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    GlobalScope.launch {
                        downloadFromUrl(url)
                    }
                },
                { throwable -> showMessage(throwable.message) }
            )
    }

    // сохранение файла
    private fun downloadFromUrl(url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            request.setTitle(getString(R.string.download_process))
            request.setDescription("from $url")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_PICTURES,
                "pixabaylite_" + System.currentTimeMillis() + ".mp4"
            )
            val downloadManager = requireActivity().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        } catch (exception: Exception) {
            Log.d(Constants.TAG, "downloadFromUrl exception = $exception")
        }
    }

    // инициализация межстраничной Яндекс рекламы в приложении
    private fun loadInterstitialAd() {
        val adRequestConfiguration =
            AdRequestConfiguration.Builder(resources.getString(R.string.ads_yandex_interstitialAd_video_unitId)).build()
        interstitialAdLoader?.loadAd(adRequestConfiguration)
    }

    // показ межстраничной Yandex рекламы в приложении
    private fun showAd() {
        interstitialAd?.apply {
            setAdEventListener(object : InterstitialAdEventListener {
                override fun onAdShown() {
                    loadInterstitialAd()
                }

                override fun onAdFailedToShow(adError: AdError) {}

                override fun onAdDismissed() {
                    interstitialAd?.setAdEventListener(null)
                    interstitialAd = null
                    loadInterstitialAd()
                    showMessage(getString(R.string.download_video_started))
                }

                override fun onAdClicked() {}
                override fun onAdImpression(impressionData: ImpressionData?) {}
            })
            show(requireActivity())
        }
    }

    // инициализация и отображение баннера Яндекс рекламы в приложении
    private fun loadBannerAd(adSize: BannerAdSize): BannerAdView {
        return bind.banner.apply {
            setAdSize(adSize)
            setAdUnitId(resources.getString(R.string.ads_yandex_banner_video_id))
            setBannerAdEventListener(object : BannerAdEventListener {
                override fun onAdLoaded() {}
                override fun onAdFailedToLoad(adRequestError: AdRequestError) {}
                override fun onAdClicked() {}
                override fun onLeftApplication() {}
                override fun onReturnedToApplication() {}
                override fun onImpression(impressionData: ImpressionData?) {}
            })
            loadAd(AdRequest.Builder().build())
        }
    }

    private fun destroyInterstitialAd() {
        interstitialAd?.setAdEventListener(null)
        interstitialAd = null
    }

    override fun onDestroy() {
        if (!disposable.isDisposed)
            disposable.dispose()
        interstitialAdLoader?.setAdLoadListener(null)
        interstitialAdLoader = null
        destroyInterstitialAd()
        bannerAd?.destroy()
        bannerAd = null
        super.onDestroy()
    }

}