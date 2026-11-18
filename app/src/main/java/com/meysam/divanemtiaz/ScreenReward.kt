package com.meysam.divanemtiaz

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.TextView

fun Screen.giftButton(): View = kit.text("🎁", TextStyle.TITLE, Royal.goldLight, Gravity.CENTER).apply {
    contentDescription = "تماشای تبلیغ و دریافت یک ساعت Pro"
    setPadding(kit.dp(8), kit.dp(4), kit.dp(8), kit.dp(4))
    minimumWidth = kit.dp(44); minimumHeight = kit.dp(44)
    setOnClickListener { host.push(RewardScreen(host)) }
    var motion: ValueAnimator? = null
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            motion = ValueAnimator.ofFloat(-9f, 9f).apply {
                duration = 650; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
                addUpdateListener { v.rotation = it.animatedValue as Float }; start()
            }
        }
        override fun onViewDetachedFromWindow(v: View) { motion?.cancel(); motion = null }
    })
}

class RewardScreen(host: MainActivity) : Screen(host) {
    private val handler = Handler(Looper.getMainLooper())
    private var timer: TextView? = null
    private var watch: View? = null
    private var active = false
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val left = (ProState.rewardNextAt - ProState.now()).coerceAtLeast(0)
            timer?.text = if (left == 0L) "زمان دریافت جایزه رسیده است" else "دریافت بعدی: " + PersianText.digits("%02d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60))
            watch?.isEnabled = left == 0L && !ProState.isPro && !AdsManager.proRewardPending()
            handler.postDelayed(this, 1000)
        }
    }
    override fun build(): View = scaffold("🎁", "یک ساعت حساب Pro با تماشای ویدئو") {
        addView(kit.text("🎁", TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER), kit.spaced(16))
        addView(kit.text("تبلیغ ببین و یک ساعت Pro بگیر", TextStyle.HEADING, Royal.goldLight, Gravity.CENTER), kit.spaced(10))
        addView(kit.text("ویدئو باید کامل پخش شود. هر ۲۴ ساعت فقط یک بار برای هر حساب و گوشی جایزه می‌گیرید. خروج زودتر یا خطای پخش جایزه ندارد.", TextStyle.BODY, Royal.muted, Gravity.CENTER), kit.spaced(10))
        timer = kit.text("", TextStyle.HEADING, Royal.turquoiseLight, Gravity.CENTER)
        addView(timer, kit.spaced(16))
        watch = kit.button(if (ProState.loggedIn) "تماشای ویدئو" else "ثبت‌نام یا ورود", ButtonKind.PRIMARY, RoyalIcon.PLAY, 54) {
            if (!ProState.loggedIn) host.push(AccountScreen(host))
            else if (AdsManager.active()) AdsManager.reward(this@RewardScreen, { kit.toast(it) }, { host.refresh() }, proReward = true)
            else if (!AdEngine.rewardedAvailable()) kit.toast("فعلاً ویدئویی آماده نیست؛ کمی بعد دوباره امتحان کنید")
            else AdEngine.showRewarded(this@RewardScreen) { _ ->
                host.refresh()
            }
        }
        addView(watch)
        addView(kit.button("سکهٔ رایگان", ButtonKind.SECONDARY, RoyalIcon.STAR) { host.push(CoinScreen(host)) }, kit.spaced(10))
    }
    override fun onShow() {
        active = true; handler.removeCallbacks(tick); handler.post(tick)
        ProSync.refreshAccount {
            ProSync.refreshConfig(force = true) { if (host.current === this) host.refresh() }
        }
    }
    override fun onHide() { active = false; handler.removeCallbacks(tick) }
}


/** A centered invitation, once per availability window; never interrupts a game. */
object RewardInvitation {
    private var shownThisRun=false
    fun offer(screen:HomeScreen) {
        if(!ProState.loggedIn || shownThisRun || ProState.isPro || ProState.rewardNextAt>ProState.now() || AdsManager.proRewardPending())return
        if(!AdEngine.rewardedAvailable(realOnly=true) && !AdsManager.proRewardAvailable(realOnly=true))return
        val prefs=screen.host.getSharedPreferences("divan_reward_invitation",android.content.Context.MODE_PRIVATE)
        val window=ProState.rewardNextAt
        if(prefs.getLong("window",Long.MIN_VALUE)==window && ProState.now()-prefs.getLong("shown_at",0)<86400)return
        screen.host.post(2500) {
            if(screen.host.current!==screen || screen.host.isFinishing || !Notices.asked(screen.host) || ProState.isPro)return@post
            if(shownThisRun)return@post
            shownThisRun=true;prefs.edit().putLong("window",window).putLong("shown_at",ProState.now()).apply()
            screen.kit.dialog("🎁 هدیهٔ شما آماده است", "می‌توانید یک ویدئوی کامل ببینید و یک ساعت از امکانات Pro استفاده کنید.", null,
                listOf(DialogAction("دریافت یک ساعت Pro",ButtonKind.PRIMARY){screen.host.push(RewardScreen(screen.host))},DialogAction("بعداً"))).show()
        }
    }
}
