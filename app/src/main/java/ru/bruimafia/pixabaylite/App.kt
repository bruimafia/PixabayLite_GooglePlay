package ru.bruimafia.pixabaylite

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.onesignal.OneSignal
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        SharedPreferencesManager.init(this)

        // Init FirebaseApp for all processes
        FirebaseApp.initializeApp(this)

        // OneSignal Initialization
        OneSignal.initWithContext(this)

        // Google Mobile Ads
        //MobileAds.initialize(this)

        // Yandex Mobile Ads
        //YandexMobileAds.initialize(this) { Log.d(Constants.TAG, "YandexMobileAds: SDK initialized") }

        // Yandex AppMetrica
        val config = AppMetricaConfig.newConfigBuilder(getString(R.string.appMetrica_api_key)).build()
        AppMetrica.activate(this, config)
        //val config: YandexMetricaConfig = YandexMetricaConfig.newConfigBuilder(getString(R.string.appMetrica_api_key)).build()
        //YandexMetrica.activate(this, config)

        createNotificationChannel()
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
//            AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(BuildConfig.APPLICATION_ID, name, importance)
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }

}