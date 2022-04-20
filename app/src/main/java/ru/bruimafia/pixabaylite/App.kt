package ru.bruimafia.pixabaylite

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.onesignal.OneSignal
import com.yandex.metrica.YandexMetrica
import com.yandex.metrica.YandexMetricaConfig
import com.yandex.mobile.ads.common.InitializationListener
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import com.yandex.mobile.ads.common.MobileAds as YandexMobileAds


class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        SharedPreferencesManager.init(this)

        // Logging set to help debug issues, remove before releasing your app.
        // OneSignal Initialization
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.initWithContext(this)
        OneSignal.setAppId(getString(R.string.onesignal_app_id))

        // Google Mobile Ads
        MobileAds.initialize(this)

        // Yandex Mobile Ads
        YandexMobileAds.initialize(this) { Log.d("TESTADS", "SDK initialized") }

        // Yandex AppMetrica
        val config: YandexMetricaConfig = YandexMetricaConfig.newConfigBuilder(getString(R.string.appMetrica_api_key)).build()
        YandexMetrica.activate(applicationContext, config)
        YandexMetrica.enableActivityAutoTracking(this)

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
//            AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES);
    }

    companion object {
        lateinit var instance: App
            private set
    }

}