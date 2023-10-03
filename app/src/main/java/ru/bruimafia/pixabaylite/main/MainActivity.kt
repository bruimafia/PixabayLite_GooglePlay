package ru.bruimafia.pixabaylite.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
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
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import ru.bruimafia.pixabaylite.App
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.adapter.TabAdapter
import ru.bruimafia.pixabaylite.databinding.ActivityMainBinding
import ru.bruimafia.pixabaylite.util.Constants
import ru.bruimafia.pixabaylite.util.Security
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean


class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private lateinit var bind: ActivityMainBinding
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var billingClient: BillingClient

    private lateinit var consentInformation: ConsentInformation
    private var isMobileAdsInitializeCalled = AtomicBoolean(false)

    private val MY_PERMISSIONS_REQUEST = 777
    private var PERMISSIONS = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private var PERMISSIONS_API_33 = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private var ackPurchase =
        AcknowledgePurchaseResponseListener { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                SharedPreferencesManager.isFullVersion = true
                Log.d(Constants.TAG, "Purchase is successful")
                Log.d(Constants.TAG, "Yay! Purchased")
                showMessage(getString(R.string.snackbar_reset_app))
                Log.d(Constants.TAG, "Item Purchased")
                recreate()
            }
        }

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
                    if (loadAndShowError != null) {
                        Log.w(
                            Constants.TAG, String.format(
                                "%s: %s",
                                loadAndShowError.errorCode,
                                loadAndShowError.message
                            )
                        )
                    }

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

        billingClient = BillingClient
            .newBuilder(App.instance)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams
                            .newBuilder()
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    ) { _, purchaseList ->
                        if (purchaseList.size > 0)
                            handlePurchases(purchaseList)
                        else
                            SharedPreferencesManager.isFullVersion = false
                    }
                } else handleBillingError(billingResult.responseCode)
            }

            override fun onBillingServiceDisconnected() {}
        })

        if (SharedPreferencesManager.isFullVersion)
            Log.d(Constants.TAG, "Purchase Status : Purchased")
        else
            Log.d(Constants.TAG, "Purchase Status : Not Purchased")

        checkPermission()
        checkUpdateAvailability()

        if (SharedPreferencesManager.isFirstLaunch)
            showAlertDialog()
    }

    private fun initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.get()) return
        isMobileAdsInitializeCalled.set(true)
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
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPermissions(this, PERMISSIONS_API_33))
                ActivityCompat.requestPermissions(this, PERMISSIONS_API_33, MY_PERMISSIONS_REQUEST)
        } else {
            if (!hasPermissions(this, PERMISSIONS))
                ActivityCompat.requestPermissions(this, PERMISSIONS, MY_PERMISSIONS_REQUEST)
        }
    }

    // получение разрешений
    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
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

    // обновление информации о покупках
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams
                    .newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { _, purchaseList ->
                if (purchaseList.size > 0)
                    handlePurchases(purchaseList)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(App.instance, "Purchase Canceled", Toast.LENGTH_SHORT).show()
            Log.d(Constants.TAG, "Purchase Canceled")
        } else {
            handleBillingError(billingResult.responseCode)
            Toast.makeText(App.instance, "Error " + billingResult.debugMessage, Toast.LENGTH_SHORT).show()
            Log.d(Constants.TAG, "Error " + billingResult.debugMessage)
        }
    }

    // установка соединения с google play для покупок
    private fun establishConnection() {
        if (billingClient.isReady) {
            initiatePurchase()
        } else {
            billingClient = BillingClient
                .newBuilder(this)
                .enablePendingPurchases()
                .setListener(this)
                .build()

            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                        initiatePurchase()
                    else {
                        handleBillingError(billingResult.responseCode)
                        Toast.makeText(App.instance, "Error " + billingResult.debugMessage, Toast.LENGTH_SHORT).show()
                        Log.d(Constants.TAG, "Error " + billingResult.debugMessage)
                    }
                }

                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    private fun initiatePurchase() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(resources.getString(R.string.billing_product_id))
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams
            .newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (productDetailsList.size > 0) {
                    val productDetailsParamsList = listOf(
                        ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetailsList[0])
                            .build()
                    )

                    val billingFlowParams = BillingFlowParams
                        .newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .build()

                    billingClient.launchBillingFlow(this@MainActivity, billingFlowParams)
                } else {
                    Toast.makeText(App.instance, "Purchase Item not Found", Toast.LENGTH_SHORT).show()
                    Log.d(Constants.TAG, "Purchase Item not Found")
                }
            } else {
                handleBillingError(billingResult.responseCode)
                Toast.makeText(App.instance, " Error " + billingResult.debugMessage, Toast.LENGTH_SHORT).show()
                Log.d(Constants.TAG, " Error " + billingResult.debugMessage)
            }
        }
    }

    fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (resources.getString(R.string.billing_product_id) == purchase.products[0] && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!verifyValidSignature(purchase.originalJson, purchase.signature)) {
                    Toast.makeText(App.instance, "Error : Invalid Purchase", Toast.LENGTH_SHORT).show()
                    Log.d(Constants.TAG, "Error : Invalid Purchase")
                    return
                }

                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams
                        .newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams, ackPurchase)
                } else {
                    if (!SharedPreferencesManager.isFullVersion) {
                        SharedPreferencesManager.isFullVersion = true
                        showMessage(getString(R.string.snackbar_reset_app))
                        Log.d(Constants.TAG, "Item Purchased")
                        recreate()
                    }
                }
            } else if (resources.getString(R.string.billing_product_id) == purchase.products[0] && purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                Toast.makeText(App.instance, "Purchase is Pending. Please complete Transaction", Toast.LENGTH_SHORT).show()
                Log.d(Constants.TAG, "Purchase is Pending. Please complete Transaction")
            } else if (resources.getString(R.string.billing_product_id) == purchase.products[0] && purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
                SharedPreferencesManager.isFullVersion = false
                Toast.makeText(App.instance, "Purchase Status Unknown", Toast.LENGTH_SHORT).show()
                Log.d(Constants.TAG, "Purchase Status Unknown")
            }
        }
    }

    private fun verifyValidSignature(signedData: String, signature: String): Boolean {
        return try {
            val base64Key = resources.getString(R.string.billing_license_key)
            Security.verifyPurchase(base64Key, signedData, signature)
        } catch (e: IOException) {
            false
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

    override fun onDestroy() {
        super.onDestroy()
        billingClient.endConnection()
    }

}