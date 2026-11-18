package com.meysam.divanemtiaz

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import java.util.Locale

object ProCountdown {
    /** Whole seconds visible to the user; a newly granted hour begins at 00:59:59. */
    fun displaySeconds(license:License,now:Long):Long {
        val left=license.remainingSeconds(now)?:0
        val hourGift=license.plan=="gift"&&license.expiresAt==license.rewardEndsAt()&&left<=3600
        return if(hourGift)(left-1).coerceAtLeast(0)else left
    }
    fun format(seconds:Long):String {
        val left=seconds.coerceAtLeast(0)
        return String.format(Locale.US,"%02d:%02d:%02d",left/3600,left%3600/60,left%60)
    }
}

/** A live readout of the verified entitlement, with no local granting or background wake lock. */
fun Screen.proTimeView():View = object:TextView(host) {
    private val handler=Handler(Looper.getMainLooper())
    private var attached=false
    private val tick=object:Runnable {
        override fun run(){
            if(!attached||windowVisibility!=View.VISIBLE)return
            updateText()
            handler.postDelayed(this,1000)
        }
    }
    private fun updateText(){
        val license=ProState.license
        val shown=ProCountdown.displaySeconds(license,ProState.now())
        text=when {
            !ProState.isPro->"Pro فعال نیست"
            license.lifetime->"Pro مادام‌العمر فعال است"
            else->"زمان باقی‌ماندهٔ Pro: "+kit.n(ProCountdown.format(shown))
        }
        contentDescription=text
    }
    init {
        typeface=kit.text("",TextStyle.BODY).typeface
        textSize=kit.textSize(18f);setTextColor(Royal.turquoiseLight)
        gravity=Gravity.CENTER
        setPadding(kit.dp(4),kit.dp(10),kit.dp(4),kit.dp(10))
        updateText()
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();attached=true;handler.removeCallbacks(tick);handler.post(tick)}
    override fun onDetachedFromWindow(){attached=false;handler.removeCallbacks(tick);super.onDetachedFromWindow()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(visibility==View.VISIBLE&&attached){handler.removeCallbacks(tick);handler.post(tick)}else handler.removeCallbacks(tick)}
}
