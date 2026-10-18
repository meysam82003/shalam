package com.meysam.divanemtiaz

import android.view.View

/** Outside MafiaSectionScreen deliberately: users must always be able to sign in or buy. */
class MafiaAccessScreen(host: MainActivity) : Screen(host) {
    private var checking = false
    private val listener: () -> Unit = {
        host.post(0) {
            if (host.current === this) {
                if (ProState.mafiaAllowed) host.replace(MafiaHomeScreen(host)) else host.refresh()
            }
        }
    }
    override fun onShow() { if (listener !in ProState.listeners) ProState.listeners += listener }
    override fun onHide() { ProState.listeners -= listener }
    override fun build(): View = scaffold("مافیا با Pro", "تست کامل، فقط یک بار و ۱۰ دقیقه") {
        addView(kit.panel(PanelStyle.RAISED).apply {
            addView(kit.text("همهٔ امکانات راوی مافیا", TextStyle.TITLE, Royal.title), kit.spaced(8))
            addView(kit.text(ProState.mafiaMessage, TextStyle.BODY, Royal.ivory), kit.spaced(12))
            addView(kit.text("مهلت تست پس از ورود و دریافت موفق تنظیمات سرور آغاز می‌شود؛ خروج از حساب، بستن برنامه و تغییر اینترنت یا VPN آن را تمدید نمی‌کند. بازی‌های ذخیره‌شده پاک نمی‌شوند.", TextStyle.LABEL, Royal.muted), kit.spaced(12))
            addView(kit.button("خرید اشتراک Pro", ButtonKind.PRIMARY, RoyalIcon.CROWN) {
                host.push(ProScreen(host, "تمام بخش مافیا با اشتراک Pro فعال می‌شود."))
            }, kit.spaced(8))
            if (!ProState.loggedIn) addView(kit.button("ثبت‌نام یا ورود", ButtonKind.SECONDARY, RoyalIcon.USER) {
                host.push(AccountScreen(host) { host.replace(MafiaHomeScreen(host)) })
            }, kit.spaced(8))
            else addView(kit.button(if (checking) "در حال بررسی…" else "بررسی دسترسی", ButtonKind.SECONDARY, RoyalIcon.UNDO) {
                if (!checking) {
                    checking = true
                    host.refresh()
                    ProSync.refreshAccount { error ->
                        checking = false
                        if (host.current === this@MafiaAccessScreen) {
                            if (ProState.mafiaAllowed) host.replace(MafiaHomeScreen(host))
                            else { ProSync.refreshConfig(force = true) { if (host.current === this@MafiaAccessScreen) host.refresh() }; host.refresh(); if (error != null) kit.toast(error.message.orEmpty()) }
                        }
                    }
                }
            })
        })
    }
}
