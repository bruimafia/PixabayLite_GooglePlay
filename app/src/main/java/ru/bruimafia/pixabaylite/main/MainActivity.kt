package ru.bruimafia.pixabaylite.main

import android.Manifest
import android.app.DownloadManager
import android.app.SearchManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.anjlab.android.iab.v3.BillingProcessor
import com.anjlab.android.iab.v3.PurchaseInfo
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import com.yandex.mobile.ads.interstitial.InterstitialAd as YandexInterstitialAd
import com.yandex.mobile.ads.common.AdRequest as YandexAdRequest
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
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
import ru.bruimafia.pixabaylite.adapter.ImageAdapter
import ru.bruimafia.pixabaylite.api.ApiClient
import ru.bruimafia.pixabaylite.api.ApiService
import ru.bruimafia.pixabaylite.databinding.ActivityMainBinding
import ru.bruimafia.pixabaylite.model.Image
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import java.io.File
import java.io.OutputStream
import java.util.Objects


class MainActivity : AppCompatActivity(), BillingProcessor.IBillingHandler {

    private var TAG = "ADS"

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var bind: ActivityMainBinding
    private lateinit var appUpdateManager: AppUpdateManager
    private var adapter: ImageAdapter = ImageAdapter()
    private lateinit var disposable: Disposable
    private lateinit var searchView: SearchView
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private var googleInterstitialAd: InterstitialAd? = null
    private var yandexInterstitialAd: YandexInterstitialAd? = null
    private lateinit var bp: BillingProcessor

    private var order = "popular"
    private var query = ""
    private var page = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(bind.toolbar)

        bp = BillingProcessor.newBillingProcessor(bind.root.context, getString(R.string.billing_license_key), this)
        bp.initialize()

        if (bp.isPurchased(getString(R.string.billing_product_id)))
            SharedPreferencesManager.isFullVersion = true

        checkPermission()
        loadData(query, order, page)
        initGoogleAdsInterstitial()
        initYandexAdsInterstitial()
        checkUpdateAvailability()

        adapter.setReachEndListener(object : ImageAdapter.OnReachEndListener {
            override fun onLoad() {
                bind.progressBarHor.visibility = View.VISIBLE
                loadData(query, order, page)
            }
        })

        adapter.setSaveButtonListener(object : ImageAdapter.OnSaveButtonListener {
            override fun onSave(image: Image, position: Int) {
                showAdsInterstitial()
                downloadFile(image.imageURL.replace("https://pixabay.com/get/", ""))
            }
        })

        adapter.setSearchTagListener(object : ImageAdapter.OnSearchTagListener {
            override fun onSearch(title: String) {
                searchView.setQuery(title, true)
                searchView.isIconified = false
                searchView.clearFocus()
                loadData(title, order, page)
            }
        })

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

        if (SharedPreferencesManager.isFirstLaunch)
            showAlertDialog()

    }

    // сервис в РФ заблокирован
    private fun showAlertDialog() {
        val view = layoutInflater.inflate(R.layout.bottom_sheet, null)
        bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)

        view.findViewById<MaterialButton>(R.id.btn_ok).setOnClickListener {
            SharedPreferencesManager.isFirstLaunch = false
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    // проверка разрешений
    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
    }

    // проверка обновлений
    private fun checkUpdateAvailability() {
        appUpdateManager = AppUpdateManagerFactory.create(this)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    this,
                    8
                )
            }
        }
    }

    // окно выставления оценки и отзыва
    private fun requestReview() {
        val reviewManager = ReviewManagerFactory.create(this)
        val requestReviewFlow = reviewManager.requestReviewFlow()

        requestReviewFlow.addOnCompleteListener { request ->
            if (request.isSuccessful) {
                val reviewInfo = request.result
                val flow = reviewManager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
//                    SharedPreferencesManager.isPlayRating = true
                }
            }
        }
    }

    // меню и строка поиска
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        menu?.findItem(R.id.action_buy)?.isVisible = !bp.isPurchased(getString(R.string.billing_product_id))

        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        searchView = menu!!.findItem(R.id.action_search).actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE
        searchView.queryHint = getString(R.string.menu_search)
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))

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

        return super.onCreateOptionsMenu(menu)
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

        if (item.itemId == R.id.action_buy) {
            bp.purchase(this, getString(R.string.billing_product_id))
        }

        if (item.itemId == R.id.action_about) {
            AboutDialog().show(supportFragmentManager, "AboutDialog")
        }

        return true
    }

    // загрузка данных
    private fun loadData(q: String, order: String, page: Int) {
        disposable = ApiClient.getClient("https://pixabay.com/api/")
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
        adapter.setList(list)
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

    // показ сообщения
    private fun showMessage(msg: String?) {
        Snackbar.make(bind.root, msg + "", Snackbar.LENGTH_LONG).show()
    }

    // скачивание файла
    private fun downloadFile(url: String) {
        showMessage(getString(R.string.download_started))
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

            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = contentResolver.openOutputStream(Objects.requireNonNull(imageUri)!!)!!

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

            MediaScannerConnection.scanFile(this@MainActivity, arrayOf(file.absolutePath), null, null)

            return true
        } catch (e: Exception) {
            println(e.toString())
        }

        return false
    }

    // показ межстраничной Google рекламы в приложении
    private fun showAdsInterstitial() {
        if (googleInterstitialAd != null && !SharedPreferencesManager.isFullVersion)
            googleInterstitialAd?.show(this)
        else if (googleInterstitialAd == null && yandexInterstitialAd?.isLoaded == true && !SharedPreferencesManager.isFullVersion)
            yandexInterstitialAd?.show()
        else
            Log.d(TAG, "Google & Yandex: the interstitial ad wasn't ready yet")
    }

    // инициализация межстраничной Google рекламы в приложении
    private fun initGoogleAdsInterstitial() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this, getString(R.string.ads_google_interstitialAd_id), adRequest, object : InterstitialAdLoadCallback() {
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

    // инициализация межстраничной Яндекс рекламы в приложении
    private fun initYandexAdsInterstitial() {
        yandexInterstitialAd = YandexInterstitialAd(this)
        yandexInterstitialAd?.setAdUnitId(getString(R.string.ads_yandex_interstitialAd_unitId))
        yandexInterstitialAd?.loadAd(YandexAdRequest.Builder().build())
        yandexInterstitialAd?.setInterstitialAdEventListener(object : InterstitialAdEventListener {
            override fun onAdLoaded() {
                Log.d(TAG, "Yandex: onAdLoaded")
            }

            override fun onAdFailedToLoad(adRequestError: AdRequestError) {
                Log.d(TAG, "Yandex (onAdFailedToLoad): " + adRequestError.description)
                yandexInterstitialAd = null
                initYandexAdsInterstitial()
            }

            override fun onImpression(impressionData: ImpressionData?) {
                Log.d(TAG, "Yandex: onImpression")
            }

            override fun onAdShown() {
                Log.d(TAG, "Yandex: onAdShown")
            }

            override fun onAdDismissed() {
                Log.d(TAG, "Yandex: onAdDismissed")
                yandexInterstitialAd = null
                initYandexAdsInterstitial()
            }

            override fun onAdClicked() {
                Log.d(TAG, "Yandex: onAdClicked")
            }

            override fun onLeftApplication() {
                Log.d(TAG, "Yandex: onLeftApplication")
            }

            override fun onReturnedToApplication() {
                Log.d(TAG, "Yandex: onReturnedToApplication")
            }

        })
    }

    override fun onResume() {
        super.onResume()

        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        this,
                        8
                    )
                }
            }
    }

    override fun onDestroy() {
        bp.release()
        if (!disposable.isDisposed)
            disposable.dispose()
        yandexInterstitialAd?.destroy()
        yandexInterstitialAd = null
        super.onDestroy()
    }

    override fun onProductPurchased(productId: String, details: PurchaseInfo?) {
        showMessage(getString(R.string.snackbar_reset_app))
        SharedPreferencesManager.isFullVersion = true
    }

    override fun onPurchaseHistoryRestored() {}

    override fun onBillingError(errorCode: Int, error: Throwable?) {
        showMessage(error?.message)
    }

    override fun onBillingInitialized() {}

    // запуск уведомления о скачивании файла
    private fun startNotificationDownload() {
        notificationBuilder = NotificationCompat.Builder(this, BuildConfig.APPLICATION_ID).apply {
            setContentTitle(getString(R.string.download_process))
            setSmallIcon(R.drawable.ic_download)
            priority = NotificationCompat.PRIORITY_LOW
        }

        NotificationManagerCompat.from(this).apply {
            notificationBuilder.setProgress(0, 0, true)
            notify(1, notificationBuilder.build())
        }
    }

    // остановка уведомления о скачивании файла
    private fun stopNotificationDownload() {
        NotificationManagerCompat.from(this).apply {
            notificationBuilder.setContentText(getString(R.string.download_complete))
                .setProgress(0, 0, false)
            notify(1, notificationBuilder.build())
            cancel(1)
        }
    }

    // не работает с включенным VPN
    private fun downloadFileWithDM(url: String) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
            .setTitle("Загрузка изображения")
            .setMimeType("image/jpg")
            .setAllowedOverRoaming(true)
            .setAllowedOverMetered(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_PICTURES,
                File.separator + "PixabayLite" + File.separator + "pixabaylite_" + System.currentTimeMillis() + ".jpg"
            )
        downloadManager.enqueue(request)
        showMessage("Изображение будет сохранено в папку PixabayLite")
    }

}