package ru.bruimafia.pixabaylite.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.adapter.TabAdapter
import ru.bruimafia.pixabaylite.databinding.ActivityMainBinding
import ru.bruimafia.pixabaylite.util.Constants
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private lateinit var bind: ActivityMainBinding
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var billingClient: BillingClient

    private lateinit var consentInformation: ConsentInformation
    private var isMobileAdsInitializeCalled = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(bind.toolbar)

        val params = ConsentRequestParameters
            .Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    this@MainActivity
                ) { loadAndShowError ->
                    // Consent gathering failed.
                    if (loadAndShowError != null) {
                        Log.w(
                            Constants.TAG, String.format(
                                "%s: %s",
                                loadAndShowError.errorCode,
                                loadAndShowError.message
                            )
                        )
                    }

                    // Consent has been gathered.
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsSdk()
                    }
                }
            },
            { requestConsentError ->
                // Consent gathering failed.
                Log.w(
                    Constants.TAG, String.format(
                        "%s: %s",
                        requestConsentError.errorCode,
                        requestConsentError.message
                    )
                )
            })
        if (consentInformation.canRequestAds())
            initializeMobileAdsSdk()

        bind.viewPager.adapter = TabAdapter(this)
        TabLayoutMediator(bind.tabLayout, bind.viewPager) { t, position ->
            when (position) {
                0 -> t.text = bind.root.context.getString(R.string.tab_images)
                1 -> t.text = bind.root.context.getString(R.string.tab_videos)
            }
        }.attach()

        billingClient = BillingClient.newBuilder(bind.root.context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        checkPermission()
        checkUpdateAvailability()

        if (SharedPreferencesManager.isFirstLaunch)
            showAlertDialog()
    }

    private fun initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.get()) return
        isMobileAdsInitializeCalled.set(true)
        // Initialize the Google Mobile Ads SDK.
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

    // меню и строка поиска
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        menu?.findItem(R.id.action_buy)?.isVisible = !SharedPreferencesManager.isFullVersion
        return super.onCreateOptionsMenu(menu)
    }

    // выбор пунктов меню
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_buy -> establishConnection()
            R.id.action_about -> AboutDialog().show(supportFragmentManager, "AboutDialog")
        }
        return false
    }

    // показ сообщения
    private fun showMessage(msg: String?) {
        Snackbar.make(bind.root, msg.toString(), Snackbar.LENGTH_LONG).show()
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

    // обноаление информации о покупках
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        billingClient = BillingClient.newBuilder(bind.root.context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases)
                handlePurchase(purchase)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            showMessage(getString(R.string.snackbar_reset_app))
            SharedPreferencesManager.isFullVersion = true
        } else handleBillingError(billingResult.responseCode)
    }

    // установка соединения с google play для покупок
    private fun establishConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    getSingleInAppDetail()
                } else retryBillingServiceConnection()
            }

            override fun onBillingServiceDisconnected() {
                retryBillingServiceConnection()
            }
        })
    }

    // повторное соединение с google play для покупок
    private fun retryBillingServiceConnection() {
        var tries = 1
        val maxTries = 3
        var isConnectionEstablished = false

        do {
            try {
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingServiceDisconnected() {
                        retryBillingServiceConnection()
                    }

                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        tries++
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                            isConnectionEstablished = true
                        else if (tries == maxTries)
                            handleBillingError(billingResult.responseCode)
                    }
                })
            } catch (e: Exception) {
                tries++
            }
        } while (tries <= maxTries && !isConnectionEstablished)

        if (!isConnectionEstablished)
            handleBillingError(-1)
    }

    // список доступных покупок
    private fun getSingleInAppDetail() {
        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(getString(R.string.billing_product_id))
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { _, productDetailsList ->
            launchPurchaseFlow(
                productDetailsList[0]
            )
        }
    }

    // запуск покупки
    private fun launchPurchaseFlow(productDetails: ProductDetails?) {
        val productList = ArrayList<ProductDetailsParams>()
        productList.add(
            ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails!!)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productList)
            .build()

        billingClient.launchBillingFlow(this, billingFlowParams)
    }

    // запуск покупки
    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams
                    .newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) {
                for (pur in purchase.products) {
                    if (pur.equals(getString(R.string.billing_product_id), ignoreCase = true)) {
                        Log.d(Constants.TAG, "Purchase is successful")
                        Log.d(Constants.TAG, "Yay! Purchased")
                        showMessage(getString(R.string.snackbar_reset_app))
                        SharedPreferencesManager.isFullVersion = true

                        consumePurchase(purchase)
                    }
                }
            }
        }
    }

    // запуск покупки
    private fun consumePurchase(purchase: Purchase) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(params) { _, s ->
            Log.d(Constants.TAG, "Consuming Successful: $s")
            Log.d(Constants.TAG, "Product Consumed")
        }
    }

    // обработка ошибок о покупках с google play
    private fun handleBillingError(responseCode: Int) {
        val errorMessage: String = when (responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing service is currently unavailable. Please try again later."
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "An error occurred while processing the request. Please try again later."
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "This feature is not supported on your device."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "You already own this item."
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "You do not own this item."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This item is not available for purchase."
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Billing service has been disconnected. Please try again later."
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Billing service is currently unavailable. Please try again later."
            BillingClient.BillingResponseCode.USER_CANCELED -> "The purchase has been canceled."
            else -> "An unknown error occurred."
        }
        Log.d(Constants.TAG, errorMessage)
    }

}