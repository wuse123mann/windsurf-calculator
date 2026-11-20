package de.wuse.windsurfrechner

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Play-Variante: Einmalkauf „premium“ über Google Play Billing.
 * Das Produkt muss in der Play Console unter „Einmalkaufprodukte“ mit der ID [Premium.PRODUCT_ID]
 * angelegt und aktiv sein.
 */
object Store : PurchasesUpdatedListener {
    private var client: BillingClient? = null
    private var appContext: Context? = null
    private var details: ProductDetails? = null

    fun connect(context: Context) {
        appContext = context.applicationContext
        if (client == null) {
            client = BillingClient.newBuilder(context.applicationContext)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build()
        }
        if (client?.isReady != true) startConnection()
    }

    private fun startConnection() {
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    queryProduct()
                    refreshPurchases()
                } else {
                    Premium.message.value = "Google Play ist gerade nicht erreichbar."
                }
            }

            override fun onBillingServiceDisconnected() {
                // Die Bibliothek verbindet sich selbst neu (enableAutoServiceReconnection).
            }
        })
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(Premium.PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        client?.queryProductDetailsAsync(params) { result, productResult ->
            if (result.responseCode == BillingResponseCode.OK) {
                val pd = productResult.productDetailsList.firstOrNull()
                details = pd
                Premium.price.value = pd?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    private fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) handle(purchases, fromQuery = true)
        }
    }

    private fun handle(purchases: List<Purchase>, fromQuery: Boolean) {
        val ctx = appContext ?: return
        val mine = purchases.filter { Premium.PRODUCT_ID in it.products }
        val bought = mine.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        when {
            bought != null -> {
                Premium.setPurchased(ctx, true)
                // Ohne Bestätigung erstattet Google den Kauf nach 3 Tagen automatisch.
                if (!bought.isAcknowledged) acknowledge(bought)
            }
            mine.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                Premium.message.value = "Zahlung ausstehend – Premium wird danach automatisch freigeschaltet."
            // Kein Kauf (mehr) vorhanden, z. B. nach einer Erstattung
            fromQuery -> Premium.setPurchased(ctx, false)
        }
    }

    private fun acknowledge(p: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        client?.acknowledgePurchase(params) { }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases?.let { handle(it, fromQuery = false) }
            BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            BillingResponseCode.USER_CANCELED -> Unit
            else -> Premium.message.value = "Kauf hat nicht geklappt (Fehler ${result.responseCode})."
        }
    }

    fun buy(activity: Activity) {
        val c = client
        val pd = details
        if (c == null || !c.isReady) {
            Premium.message.value = "Google Play ist noch nicht verbunden – bitte gleich nochmal versuchen."
            connect(activity)
            return
        }
        if (pd == null) {
            Premium.message.value = "Produkt in Google Play nicht gefunden."
            queryProduct()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
        c.launchBillingFlow(activity, params)
    }

    fun restore(context: Context) {
        Premium.message.value = null
        connect(context)
        if (client?.isReady == true) refreshPurchases()
    }
}
