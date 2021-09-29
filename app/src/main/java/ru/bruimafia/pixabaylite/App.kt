package ru.bruimafia.pixabaylite

import android.app.Application
import com.onesignal.OneSignal
import com.google.android.gms.ads.MobileAds
import com.yandex.metrica.YandexMetrica
import com.yandex.metrica.YandexMetricaConfig


class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Logging set to help debug issues, remove before releasing your app.
        // OneSignal Initialization
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.initWithContext(this)
        OneSignal.setAppId(getString(R.string.onesignal_app_id))

        // Google Mobile Ads
        MobileAds.initialize(this)

        // AppMetrica
        val config: YandexMetricaConfig = YandexMetricaConfig.newConfigBuilder(getString(R.string.appMetrica_api_key)).build()
        YandexMetrica.activate(applicationContext, config)
        YandexMetrica.enableActivityAutoTracking(this)

    }
}