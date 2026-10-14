package com.meysam.divanemtiaz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.entity.PurchaseState
import ir.cafebazaar.poolakey.request.PurchaseRequest

/**
 * Cafe Bazaar in-app billing (Poolakey). Every receipt is checked by the server (signature and the Bazaar developer
 * API) before Pro is given; the app's public RSA key from the Bazaar panel also lets Poolakey refuse a purchase whose
 * signature does not match on the phone itself.
 */
class FlavorBilling : StoreBilling {
    override fun availability(context: Context): String? {
        val installed = runCatching { context.packageManager.getPackageInfo(BAZAAR, 0); true }.getOrDefault(false)
        return if (installed) null else "برای خرید، برنامهٔ کافه‌بازار باید روی گوشی نصب و به‌روز باشد."
    }

    override fun purchase(activity: Activity, sku: String, productType: String, payload: String, dynamicPriceToken: String?, done: (BillingResult) -> Unit) {
        BazaarPayActivity.pending = done
        activity.startActivity(Intent(activity, BazaarPayActivity::class.java)
            .putExtra("sku", sku).putExtra("payload", payload).putExtra("type", productType).putExtra("dynamic", dynamicPriceToken))
    }

    override fun consume(context: Context, receipt: StoreReceipt, done: (Boolean) -> Unit) = connect(context, { done(false) }) { payment, conn ->
        payment.consumeProduct(receipt.token) {
            consumeSucceed { conn.disconnect(); done(true) }
            consumeFailed { conn.disconnect(); done(false) }
        }
    }

    override fun owned(context: Context, done: (List<StoreReceipt>?, String?) -> Unit) = connect(context, { done(null, it) }) { payment, conn ->
        payment.getPurchasedProducts {
            querySucceed { products ->
                val bought = products.filter { it.purchaseState == PurchaseState.PURCHASED }.map { it.toReceipt() }
                // Bazaar subscriptions are listed separately.
                payment.getSubscribedProducts {
                    querySucceed { subs -> conn.disconnect(); done(bought + subs.filter { it.purchaseState == PurchaseState.PURCHASED }.map { it.toReceipt() }, null) }
                    queryFailed { conn.disconnect(); done(bought, null) }
                }
            }
            queryFailed { conn.disconnect(); done(null, message(it)) }
        }
    }

    override fun prices(context: Context, inApp: List<String>, subs: List<String>, done: (Map<String, String>?) -> Unit) = connect(context, { done(null) }) { payment, conn ->
        val out = HashMap<String, String>()
        fun finish() { conn.disconnect(); done(out) }
        fun subscriptions() {
            if (subs.isEmpty()) { finish(); return }
            payment.getSubscriptionSkuDetails(subs) {
                getSkuDetailsSucceed { list -> list.forEach { out[it.sku] = it.price }; finish() }
                getSkuDetailsFailed { finish() }
            }
        }
        if (inApp.isEmpty()) subscriptions()
        else payment.getInAppSkuDetails(inApp) {
            getSkuDetailsSucceed { list -> list.forEach { out[it.sku] = it.price }; subscriptions() }
            getSkuDetailsFailed { subscriptions() }
        }
    }

    private fun connect(context: Context, fail: (String) -> Unit, block: (Payment, Connection) -> Unit) {
        availability(context)?.let { fail(it); return }
        val payment = Payment(context.applicationContext, config(context))
        var conn: Connection? = null
        conn = payment.connect {
            connectionSucceed { conn?.let { block(payment, it) } }
            connectionFailed { fail(message(it)) }
            disconnected { }
        }
    }

    companion object {
        const val BAZAAR = "com.farsitel.bazaar"

        /** Signature check with the app's Bazaar RSA key when the build has it (all release builds do). */
        fun config(context: Context): PaymentConfiguration {
            val key = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA).metaData?.getString("divan.bazaarKey")
            }.getOrNull().orEmpty().trim()
            return PaymentConfiguration(if (key.isNotEmpty()) SecurityCheck.Enable(key) else SecurityCheck.Disable, true)
        }

        fun message(t: Throwable): String = when (t.javaClass.simpleName) {
            "BazaarNotFoundException" -> "برنامهٔ کافه‌بازار پیدا نشد."
            "IAPNotSupportedException" -> "نسخهٔ کافه‌بازار پرداخت درون‌برنامه‌ای را پشتیبانی نمی‌کند؛ کافه‌بازار را به‌روز کنید."
            "DynamicPriceNotSupportedException" -> "برای تخفیف، کافه‌بازار را به‌روز کنید."
            else -> "پرداخت بازار انجام نشد: " + (t.message ?: t.javaClass.simpleName)
        }

        fun PurchaseInfo.toReceipt() = StoreReceipt(productId, purchaseToken, orderId, originalJson, dataSignature, payload, purchaseTime)
    }
}

/** Runs one Bazaar purchase and returns the result to [FlavorBilling]. */
class BazaarPayActivity : ComponentActivity() {
    private var conn: Connection? = null
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (pending == null) { finish(); return }
        val sku = intent.getStringExtra("sku").orEmpty()
        val payload = intent.getStringExtra("payload").orEmpty()
        val dynamic = intent.getStringExtra("dynamic")
        val subscription = intent.getStringExtra("type") == "subscription"
        val payment = Payment(applicationContext, FlavorBilling.config(this))
        conn = payment.connect {
            connectionSucceed {
                val request = PurchaseRequest(sku, payload, dynamic)
                val callback: ir.cafebazaar.poolakey.callback.PurchaseCallback.() -> Unit = {
                    purchaseSucceed { deliver(BillingResult.Success(with(FlavorBilling) { it.toReceipt() })) }
                    purchaseCanceled { deliver(BillingResult.Canceled) }
                    purchaseFailed { deliver(BillingResult.Failed(FlavorBilling.message(it))) }
                    failedToBeginFlow { deliver(BillingResult.Failed(FlavorBilling.message(it))) }
                }
                if (subscription) payment.subscribeProduct(activityResultRegistry, request, callback)
                else payment.purchaseProduct(activityResultRegistry, request, callback)
            }
            connectionFailed { deliver(BillingResult.Failed(FlavorBilling.message(it))) }
            disconnected { }
        }
    }

    private fun deliver(r: BillingResult) {
        if (delivered) return
        delivered = true
        val cb = pending
        pending = null
        conn?.disconnect()
        finish()
        cb?.invoke(r)
    }

    override fun onDestroy() {
        if (!delivered && isFinishing) deliver(BillingResult.Canceled)
        super.onDestroy()
    }

    companion object {
        var pending: ((BillingResult) -> Unit)? = null
    }
}
