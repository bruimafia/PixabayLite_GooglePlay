package ru.bruimafia.pixabaylite.main

import android.Manifest
import android.app.SearchManager
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.SearchView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
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
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.bruimafia.pixabaylite.BuildConfig
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.adapter.image.ImageAdapter
import ru.bruimafia.pixabaylite.api.ApiClientImage
import ru.bruimafia.pixabaylite.api.ApiService
import ru.bruimafia.pixabaylite.databinding.FragmentImagesBinding
import ru.bruimafia.pixabaylite.model.image.Image
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import java.io.File
import java.io.OutputStream
import java.util.Objects
import kotlin.math.roundToInt


class ImagesFragment : Fragment() {

    private lateinit var bind: FragmentImagesBinding
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var adapter: ImageAdapter = ImageAdapter()
    private lateinit var disposable: Disposable
    private lateinit var searchView: SearchView

    //private var googleInterstitialAd: InterstitialAd? = null
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
        bind = DataBindingUtil.inflate(inflater, R.layout.fragment_images, container, false)
        return bind.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadData(query, order, page)
        //initGoogleAdsInterstitial()

        adapter.onReachEndListener = {
            bind.progressBarHor.visibility = View.VISIBLE
            loadData(query, order, page)
        }

        adapter.onSaveButtonListener = {
            if (!SharedPreferencesManager.isFullVersion)
                showAd()
            downloadFile(it.imageURL.replace("https://pixabay.com/get/", ""))
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
                    this@ImagesFragment.interstitialAd = interstitialAd
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
        disposable = ApiClientImage.getClient("https://pixabay.com/api/")
            .create(ApiService::class.java)
            .getImages(q, order, page)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { imagesResponse ->
                    if (page <= 1)
                        showData(imagesResponse.images as MutableList<Image>)
                    else
                        showLoadedData(imagesResponse.images as MutableList<Image>)
                },
                { throwable -> showMessage(throwable.message) }
            )
    }

    // показ данных
    private fun showData(list: MutableList<Image>) {
        adapter.submitList(list)
        bind.recycler.adapter = adapter
        page++
        bind.progressBar.visibility = View.GONE
        bind.swipeRefreshLayout.isRefreshing = false
    }

    // показ подгруженных данных
    private fun showLoadedData(list: MutableList<Image>) {
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
        showMessage(getString(R.string.download_image_started))
        startNotificationDownload()
        disposable = Retrofit.Builder()
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .baseUrl("https://pixabay.com/get/")
            .build()
            .create(ApiService::class.java)
            .downloadImage(url)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    GlobalScope.launch {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            saveFileWithAPI29(it.body()!!, url)
                        else
                            saveFileBeforeAPI29(it.body()!!, url)
                        stopNotificationDownload()
                    }
                },
                { throwable -> showMessage(throwable.message) }
            )
    }

    // сохранение файла API>=29
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveFileWithAPI29(body: ResponseBody, name: String): Boolean {

        val fos: OutputStream
        val bmp = BitmapFactory.decodeStream(body.byteStream())

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "pixabaylite_" + System.currentTimeMillis() + ".jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + File.separator + "PixabayLite"
                )
            }

            val imageUri = bind.root.context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = bind.root.context.contentResolver.openOutputStream(Objects.requireNonNull(imageUri)!!)!!

            try {
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                Objects.requireNonNull(fos)
            } catch (e: Exception) {
                println(e.toString())
            } finally {
                fos.close()
            }

            return true
        } catch (e: Exception) {
            println(e.toString())
        }

        return false
    }

    // сохранение файла API<29
    private fun saveFileBeforeAPI29(body: ResponseBody, name: String): Boolean {
        try {
            val mediaStorageDir = File(Environment.getExternalStorageDirectory(), "PixabayLite")
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs())
                    showMessage(getString(R.string.error_create_folder))
            }

            val file = File(mediaStorageDir, "pixabaylite_" + System.currentTimeMillis() + ".jpg")
            body.byteStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }

            MediaScannerConnection.scanFile(bind.root.context, arrayOf(file.absolutePath), null, null)

            return true
        } catch (e: Exception) {
            println(e.toString())
        }

        return false
    }

    // инициализация межстраничной Яндекс рекламы в приложении
    private fun loadInterstitialAd() {
        val adRequestConfiguration = AdRequestConfiguration.Builder(resources.getString(R.string.ads_yandex_interstitialAd_image_unitId)).build()
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
                    showMessage(getString(R.string.download_image_started))
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
            setAdUnitId(resources.getString(R.string.ads_yandex_banner_image_id))
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

    // запуск уведомления о скачивании файла
    private fun startNotificationDownload() {
        notificationBuilder = NotificationCompat.Builder(bind.root.context, BuildConfig.APPLICATION_ID).apply {
            setContentTitle(getString(R.string.download_process))
            setSmallIcon(R.drawable.ic_download)
            priority = NotificationCompat.PRIORITY_LOW
        }

        NotificationManagerCompat.from(bind.root.context).apply {
            notificationBuilder.setProgress(0, 0, true)
            if (ActivityCompat.checkSelfPermission(
                    bind.root.context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(1, notificationBuilder.build())
            }
        }
    }

    // остановка уведомления о скачивании файла
    private fun stopNotificationDownload() {
        NotificationManagerCompat.from(bind.root.context).apply {
            notificationBuilder.setContentText(getString(R.string.download_complete))
                .setProgress(0, 0, false)
            if (ActivityCompat.checkSelfPermission(
                    bind.root.context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(1, notificationBuilder.build())
                //cancel(1)
            }
        }
    }

    /*
  // показ межстраничной Google рекламы в приложении
  private fun showGoogleAdsInterstitial() {
      if (googleInterstitialAd != null && !SharedPreferencesManager.isFullVersion)
          googleInterstitialAd?.show(this)
      else if (googleInterstitialAd == null && yandexInterstitialAd?.isLoaded == true && !SharedPreferencesManager.isFullVersion)
          yandexInterstitialAd?.show()
      else
          Log.d(TAG, "Google & Yandex: the interstitial ad wasn't ready yet")
  }
  */

    /*
    // инициализация межстраничной Google рекламы в приложении
    private fun initGoogleAdsInterstitial() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            this,
            getString(R.string.ads_google_interstitialAd_id),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Google (onAdFailedToLoad): " + adError.message)
                    googleInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Google: onAdLoaded")
                    googleInterstitialAd = interstitialAd
                    googleInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Google: onAdDismissedFullScreenContent")
                            showMessage(getString(R.string.download_started))
                            googleInterstitialAd = null
                            initGoogleAdsInterstitial()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.d(TAG, "Google: onAdFailedToShowFullScreenContent")
                            googleInterstitialAd = null
                            initGoogleAdsInterstitial()
                            // если с google ошибка, то тогда показываем рекламу Яндекс
                            if (yandexInterstitialAd?.isLoaded == true)
                                yandexInterstitialAd?.show()
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Google: onAdShowedFullScreenContent")
                        }
                    }

                }
            })
    }
    */

    // не работает с включенным VPN
//    private fun downloadFileWithDM(url: String) {
//        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
//        val request = DownloadManager.Request(Uri.parse(url))
//        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
//            .setTitle("Загрузка изображения")
//            .setMimeType("image/jpg")
//            .setAllowedOverRoaming(true)
//            .setAllowedOverMetered(true)
//            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
//            .setDestinationInExternalPublicDir(
//                Environment.DIRECTORY_PICTURES,
//                File.separator + "PixabayLite" + File.separator + "pixabaylite_" + System.currentTimeMillis() + ".jpg"
//            )
//        downloadManager.enqueue(request)
//        showMessage("Изображение будет сохранено в папку PixabayLite")
//    }

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