package com.meysam.divanemtiaz

import android.app.Activity
import android.widget.FrameLayout
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.BannerSize
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState
import ir.tapsell.mediation.ad.views.banner.BannerContainer

/** Stable Mediation 1.3.0. Manifest key mismatch fails closed; never silently uses another publisher. */
class TapsellIranAdapter: IranianAdAdapter {
    private val loaded=HashMap<String,String>()
    override fun configure(activity:Activity,appId:String,test:Boolean):Boolean {
        val key=activity.packageManager.getApplicationInfo(activity.packageName,android.content.pm.PackageManager.GET_META_DATA).metaData?.getString("ir.tapsell.mediation.APPLICATION_KEY")
        if(key!=appId)return false
        ir.tapsell.mediation.core.internal.init.Initializer().initialize(activity.applicationContext)
        return true
    }
    override fun ready(format:String,zone:String)=loaded.containsKey("$format:$zone")
    override fun load(activity:Activity,format:String,zone:String,done:(Boolean)->Unit){
        if(ready(format,zone)){done(true);return}
        val listener=object:RequestResultListener{
            override fun onSuccess(adId:String){loaded["$format:$zone"]=adId;done(true)}
            override fun onFailure(message:String){done(false)}
        }
        if(format=="rewarded")Tapsell.requestRewardedAd(zone,activity,listener) else Tapsell.requestInterstitialAd(zone,activity,listener)
    }
    override fun show(activity:Activity,format:String,zone:String,event:(String)->Unit){
        val id=loaded.remove("$format:$zone")?:run{event("error");return}
        if(format=="rewarded")Tapsell.showRewardedAd(id,activity,object:AdStateListener.Rewarded{
            private var rewarded=false
            override fun onRewarded(){rewarded=true}
            override fun onAdImpression(){event("impression")}
            override fun onAdClicked(){event("click")}
            override fun onAdFailed(message:String){event("error")}
            override fun onAdClosed(state:AdShowCompletionState){if(rewarded)event("complete");event("close")}
        }) else Tapsell.showInterstitialAd(id,activity,object:AdStateListener.Interstitial{
            override fun onAdImpression(){event("impression")}
            override fun onAdClicked(){event("click")}
            override fun onAdFailed(message:String){event("error")}
            override fun onAdClosed(state:AdShowCompletionState){event("close")}
        })
    }
    override fun banner(activity:Activity,zone:String,box:FrameLayout,event:(String)->Unit){
        val weak=java.lang.ref.WeakReference(activity)
        Tapsell.requestBannerAd(zone,BannerSize.BANNER_320_50,activity,object:RequestResultListener{
            override fun onFailure(message:String){event("error")}
            override fun onSuccess(adId:String){
                val a=weak.get()?:return
                if(a.isFinishing)return
                val container=BannerContainer(a);box.removeAllViews();box.addView(container);box.tag=adId
                Tapsell.showBannerAd(adId,container,a,object:AdStateListener.Banner{
                    override fun onAdImpression(){event("impression")}
                    override fun onAdClicked(){event("click")}
                    override fun onAdFailed(message:String){event("error")}
                })
            }
        })
    }
    override fun destroyBanner(box:FrameLayout){(box.tag as? String)?.let{Tapsell.destroyBannerAd(it)};box.tag=null;box.removeAllViews()}
    override fun release(){loaded.clear()}
}
