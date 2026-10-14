package com.meysam.divanemtiaz

import android.app.Activity
import android.widget.FrameLayout
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.BannerSize
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState
import ir.tapsell.mediation.ad.views.banner.BannerContainer

/**
 * Tapsell Mediation, compiled only into builds made with a Tapsell app key. Its automatic start-up provider is
 * removed from the manifest; [init] starts it by hand, and [AdEngine] calls that only for users without Pro.
 */
class TapsellBridge : NetworkAds {
    private var started = false

    override fun init(activity: Activity, appId: String, test: Boolean) {
        if (started) return
        // The SDK reads its key from the manifest (TapsellMediationAppKey) when its initializer runs.
        ir.tapsell.mediation.core.internal.init.Initializer().initialize(activity.applicationContext)
        Tapsell.setUserConsent(activity, true)
        started = true
    }

    override fun banner(activity: Activity, zone: String, container: FrameLayout, done: (Boolean) -> Unit) {
        Tapsell.requestBannerAd(zone, BannerSize.BANNER_320_50, activity, object : RequestResultListener {
            override fun onSuccess(adId: String) {
                val box = BannerContainer(activity)
                container.removeAllViews()
                container.addView(box, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                container.tag = adId
                Tapsell.showBannerAd(adId, box, activity, object : AdStateListener.Banner {
                    override fun onAdImpression() { done(true) }
                    override fun onAdClicked() {}
                    override fun onAdFailed(message: String) { done(false) }
                })
            }
            override fun onFailure(message: String) = done(false)
        })
    }

    override fun interstitial(activity: Activity, zone: String, done: (Boolean) -> Unit) {
        Tapsell.requestInterstitialAd(zone, activity, object : RequestResultListener {
            override fun onSuccess(adId: String) {
                Tapsell.showInterstitialAd(adId, activity, object : AdStateListener.Interstitial {
                    override fun onAdImpression() {}
                    override fun onAdClicked() {}
                    override fun onAdFailed(message: String) = done(false)
                    override fun onAdClosed(state: AdShowCompletionState) = done(true)
                })
            }
            override fun onFailure(message: String) = done(false)
        })
    }

    override fun rewarded(activity: Activity, zone: String, done: (Boolean) -> Unit) {
        Tapsell.requestRewardedAd(zone, activity, object : RequestResultListener {
            override fun onSuccess(adId: String) {
                var rewarded = false
                Tapsell.showRewardedAd(adId, activity, object : AdStateListener.Rewarded {
                    override fun onRewarded() { rewarded = true }
                    override fun onAdImpression() {}
                    override fun onAdClicked() {}
                    override fun onAdFailed(message: String) = done(false)
                    override fun onAdClosed(state: AdShowCompletionState) = done(rewarded)
                })
            }
            override fun onFailure(message: String) = done(false)
        })
    }

    override fun destroy(container: FrameLayout) {
        (container.tag as? String)?.let { Tapsell.destroyBannerAd(it) }
    }
}
