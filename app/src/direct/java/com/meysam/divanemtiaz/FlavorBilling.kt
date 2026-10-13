package com.meysam.divanemtiaz

import android.app.Activity
import android.content.Context

/**
 * Site version: no store billing at all. Plans are paid through the web gateways that the server lists
 * for this version (ProScreen asks v1/pay/methods); the server verifies every payment itself.
 */
class FlavorBilling : StoreBilling {
    override fun availability(context: Context): String = "این نسخه از درگاه‌های وب پرداخت می‌کند."
    override fun purchase(activity: Activity, sku: String, productType: String, payload: String, dynamicPriceToken: String?, done: (BillingResult) -> Unit) =
        done(BillingResult.Failed("این نسخه از درگاه‌های وب پرداخت می‌کند."))
    override fun consume(context: Context, receipt: StoreReceipt, done: (Boolean) -> Unit) = done(false)
    override fun owned(context: Context, done: (List<StoreReceipt>?, String?) -> Unit) = done(emptyList(), null)
}
