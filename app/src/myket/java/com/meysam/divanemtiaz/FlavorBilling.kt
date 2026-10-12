package com.meysam.divanemtiaz

import android.app.Activity
import android.content.Context
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.Purchase

/**
 * Myket in-app billing (official billing client). Myket has no subscriptions: timed plans are consumable
 * products that the server turns into periods; they are consumed only after the server recorded them.
 */
class FlavorBilling : StoreBilling {
    override fun availability(context: Context): String? {
        if (StoreInfo.myketKey.isBlank()) return "پرداخت مایکت در این نسخه پیکربندی نشده است."
        val installed = runCatching { context.packageManager.getPackageInfo(MYKET, 0); true }.getOrDefault(false)
        return if (installed) null else "برای خرید، برنامهٔ مایکت باید روی گوشی نصب و به‌روز باشد."
    }

    private fun helper(context: Context, fail: (String) -> Unit, ready: (IabHelper) -> Unit) {
        availability(context)?.let { fail(it); return }
        val h = IabHelper(context, StoreInfo.myketKey)
        runCatching {
            h.startSetup { r -> if (r.isSuccess) ready(h) else { fail("اتصال به مایکت برقرار نشد: ${r.message}"); runCatching { h.dispose() } } }
        }.onFailure { fail("اتصال به مایکت برقرار نشد.") }
    }

    override fun purchase(activity: Activity, sku: String, productType: String, payload: String, dynamicPriceToken: String?, done: (BillingResult) -> Unit) =
        helper(activity, { done(BillingResult.Failed(it)) }) { h ->
            h.launchPurchaseFlow(activity, sku, { result, purchase ->
                when {
                    result.isSuccess && purchase != null -> done(BillingResult.Success(purchase.toReceipt()))
                    result.response == IabHelper.IABHELPER_USER_CANCELLED || result.response == IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED -> done(BillingResult.Canceled)
                    result.response == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED -> done(BillingResult.Failed("این محصول قبلاً خریده شده است؛ «بازیابی خرید» را بزنید."))
                    else -> done(BillingResult.Failed("پرداخت مایکت انجام نشد: ${result.message}"))
                }
                runCatching { h.dispose() }
            }, payload)
        }

    override fun consume(context: Context, receipt: StoreReceipt, done: (Boolean) -> Unit) =
        helper(context.applicationContext, { done(false) }) { h ->
            val p = runCatching { Purchase(IabHelper.ITEM_TYPE_INAPP, receipt.originalJson, receipt.signature) }.getOrNull()
            if (p == null) { done(false); runCatching { h.dispose() }; return@helper }
            h.consumeAsync(p) { _, r -> done(r.isSuccess); runCatching { h.dispose() } }
        }

    override fun owned(context: Context, done: (List<StoreReceipt>?, String?) -> Unit) =
        helper(context.applicationContext, { done(null, it) }) { h ->
            h.queryInventoryAsync(false) { r, inv ->
                if (r.isSuccess && inv != null) done(inv.allPurchases.filter { it.purchaseState == 0 }.map { it.toReceipt() }, null)
                else done(null, "فهرست خریدهای مایکت دریافت نشد: ${r.message}")
                runCatching { h.dispose() }
            }
        }

    private fun Purchase.toReceipt() = StoreReceipt(sku, token, orderId, originalJson, signature, developerPayload, purchaseTime)

    companion object { const val MYKET = "ir.mservices.market" }
}
