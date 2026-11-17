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
            watch?.isEnabled = left == 0L && !ProState.isPro
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
            else AdEngine.showRewarded(this@RewardScreen) { ok ->
                kit.toast(if (ok) "یک ساعت Pro فعال شد" else "جایزه ثبت نشد؛ تماشای کامل لازم است")
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
