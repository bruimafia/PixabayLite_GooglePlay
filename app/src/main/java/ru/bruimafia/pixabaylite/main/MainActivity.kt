package ru.bruimafia.pixabaylite.main

import android.Manifest
import android.app.Dialog
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.adapter.ImageAdapter
import ru.bruimafia.pixabaylite.api.ApiClient
import ru.bruimafia.pixabaylite.api.ApiService
import ru.bruimafia.pixabaylite.databinding.ActivityMainBinding
import ru.bruimafia.pixabaylite.model.Image
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import java.io.File


class MainActivity : AppCompatActivity() {

    private var TAG = "TESTADS"

    private lateinit var bind: ActivityMainBinding
    private var adapter: ImageAdapter = ImageAdapter()
    private lateinit var disposable: Disposable
    private lateinit var searchView: SearchView
    private var mInterstitialAd: InterstitialAd? = null

    private var order = "popular"
    private var query = ""
    private var page = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(bind.toolbar)

        checkPermiss()
        loadData(query, order, page)
        initAdsInterstitial()
        showPlayRatingDialog()

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

    }

    // проверка разрешений
    private fun checkPermiss() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
    }

    // показ окна рейтинга
    private fun showPlayRatingDialog() {
        if (!SharedPreferencesManager.isPlayRating) {
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_rating)
            dialog.findViewById<MaterialButton>(R.id.btn_ok).setOnClickListener {
                // обновление информации об успешном оценивании и открытие приложения в Google Play
                SharedPreferencesManager.isPlayRating = true
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + applicationContext.packageName)))
                } catch (e: ActivityNotFoundException) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=" + applicationContext.packageName)
                        )
                    )
                }
                dialog.cancel()
            }
            dialog.findViewById<MaterialButton>(R.id.btn_later).setOnClickListener { dialog.cancel() }
            dialog.show()
        }
    }

    // меню и строка поиска
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        searchView = menu!!.findItem(R.id.action_search).actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE
        searchView.queryHint = getString(R.string.menu_search)
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(newText: String?): Boolean {
                query = newText ?: ""
                page = 1
                bind.progressBar.visibility = View.VISIBLE;
                loadData(query, order, page)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                query = newText ?: ""
                page = 1
                bind.progressBar.visibility = View.VISIBLE;
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
    }

    // показ сообщения
    private fun showMessage(msg: String?) {
        Snackbar.make(bind.root, msg + "", Snackbar.LENGTH_LONG).show()
    }

    // скачивание файла
    private fun downloadFile(url: String) {
        showMessage(getString(R.string.download_started))
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
                        if (saveFile(it.body()!!, url))
                            showMessage(getString(R.string.image_saved))
                    }
                },
                { throwable -> showMessage(throwable.message) }
            )
    }

    // сохранение файла
    private fun saveFile(body: ResponseBody, name: String): Boolean {
        try {
            val mediaStorageDir = File(Environment.getExternalStorageDirectory(), "PixabayLite")
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs())
                    showMessage(getString(R.string.error_create_folder))
            }

            val file = File(mediaStorageDir, "pixabaylite_$name")

            val inputStream = body.byteStream()
            inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }

            MediaScannerConnection.scanFile(this@MainActivity, arrayOf(file.absolutePath), null, null)

            return true
        } catch (e: Exception) {
            println(e.toString())
        }

        return false
    }

    // показ межстраничной рекламы в приложении
    private fun showAdsInterstitial() {
        if (mInterstitialAd != null)
            mInterstitialAd?.show(this)
        else
            Log.d(TAG, "The interstitial ad wasn't ready yet.")
    }

    // инициализация межстраничной рекламы в приложении
    private fun initAdsInterstitial() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this, getString(R.string.ads_interstitialAd_id), adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError.message)
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Ad was loaded.")
                mInterstitialAd = interstitialAd
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Ad was dismissed.")
                        showMessage(getString(R.string.image_saved))
                        mInterstitialAd = null
                        initAdsInterstitial()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError?) {
                        Log.d(TAG, "Ad failed to show.")
                        mInterstitialAd = null
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Ad showed fullscreen content.")
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!disposable.isDisposed)
            disposable.dispose()
    }

}