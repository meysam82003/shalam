package com.meysam.divanemtiaz

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import com.adivery.sdk.*

class AdiveryIranAdapter: IranianAdAdapter {
    private val main=Handler(Looper.getMainLooper())
    private val listeners=HashSet<String>()
    override fun configure(activity:Activity,appId:String,test:Boolean):Boolean{Adivery.configure(activity.application,appId);Adivery.setLoggingEnabled(false);return true}
    override fun ready(format:String,zone:String)=Adivery.isLoaded(zone)
    override fun load(activity:Activity,format:String,zone:String,done:(Boolean)->Unit){
        if(ready(format,zone)){done(true);return}
        var called=false
        fun finish(ok:Boolean){if(!called){called=true;Adivery.removePlacementListener(zone);listeners.remove(zone);done(ok)}}
        listeners+=zone
        Adivery.addPlacementListener(zone,object:AdiveryListener(){
            override fun onRewardedAdLoaded(placementId:String){finish(true)}
            override fun onInterstitialAdLoaded(placementId:String){finish(true)}
        })
        main.postDelayed({finish(false)},15000)
        if(format=="rewarded")Adivery.prepareRewardedAd(activity,zone) else Adivery.prepareInterstitialAd(activity,zone)
    }
    override fun show(activity:Activity,format:String,zone:String,event:(String)->Unit){
        if(!ready(format,zone)){event("error");return}
        listeners+=zone
        fun finish(){Adivery.removePlacementListener(zone);listeners.remove(zone);event("close")}
        Adivery.addPlacementListener(zone,object:AdiveryListener(){
            override fun onRewardedAdShown(placementId:String){event("impression")}
            override fun onRewardedAdClicked(placementId:String){event("click")}
            override fun onRewardedAdClosed(placementId:String,isRewarded:Boolean){if(isRewarded)event("complete");finish()}
            override fun onInterstitialAdShown(placementId:String){event("impression")}
            override fun onInterstitialAdClicked(placementId:String){event("click")}
            override fun onInterstitialAdClosed(placementId:String){finish()}
        });Adivery.showAd(zone)
    }
    override fun banner(activity:Activity,zone:String,box:FrameLayout,event:(String)->Unit){
        val ad=AdiveryBannerAdView(activity)
        ad.setPlacementId(zone);ad.setBannerSize(BannerSize.BANNER)
        ad.setBannerAdListener(object:AdiveryAdListener(){
            override fun onAdLoaded(){event("loaded")}
            override fun onAdShown(){event("impression")}
            override fun onAdClicked(){event("click")}
            override fun onError(reason:String){event("error")}
        });box.removeAllViews();box.addView(ad);ad.loadAd()
    }
    override fun destroyBanner(box:FrameLayout){(box.getChildAt(0) as? AdiveryBannerAdView)?.let { it.setRetryOnError(false);it.setBannerAdListener(object:AdiveryAdListener(){}) };box.removeAllViews()}
    override fun release(){main.removeCallbacksAndMessages(null);listeners.toList().forEach{Adivery.removePlacementListener(it)};listeners.clear()}
}
