package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View

/** Deck calculator for «هزارتایی» and «دو لو گشنیز»: every answer is a whole number of cards. */
class DeckCalcScreen(host: MainActivity, initialGame: GameType = GameType.HEZARTAII, players: Int = 0) : Screen(host) {
    private var game = if (initialGame == GameType.DOLO) GameType.DOLO else GameType.HEZARTAII
    private var hezarPlayers = if (players > 1 && game == GameType.HEZARTAII) players else settings.hezar.players.coerceAtLeast(2)
    private var doloPlayers = if (players > 1 && game == GameType.DOLO) players else settings.dolo.players
    private var decks = settings.deck.decks
    private var jokers = settings.deck.jokers
    private var handSize = settings.deck.handSize
    private var first = settings.deck.firstPacket
    private var next = settings.deck.nextPacket
    private var doloCards = settings.deck.doloCards

    override fun build(): View = scaffold(title = "ماشین‌حساب ورق", subtitle = "تعداد ورق هر نفر، پخش و ورق‌های اضافه") {
        addView(kit.grid(2, listOf(
            kit.chip("هزارتایی", game == GameType.HEZARTAII, ButtonKind.CHIP_GOLD) { game = GameType.HEZARTAII; host.refresh() },
            kit.chip("دو لو گشنیز", game == GameType.DOLO, ButtonKind.CHIP_GOLD) { game = GameType.DOLO; host.refresh() }
        ), 6), kit.spaced(8))
        if (game == GameType.HEZARTAII) hezar(this) else dolo(this)
    }

    private fun hezar(parent: android.widget.LinearLayout) {
        parent.addView(kit.stepperRow("تعداد نفرات", null, hezarPlayers, 2, MAX_PLAYERS, 1) { hezarPlayers = it; host.refresh() })
        parent.addView(kit.stepperRow("تعداد دستهٔ ورق", "هر دسته ${kit.n(52)} برگ", decks, 1, 40, 1) { decks = it; host.refresh() })
        parent.addView(kit.stepperRow("تعداد کل جوکرها", null, jokers, 0, 80, 1) { jokers = it; host.refresh() })
        parent.addView(kit.stepperRow("ورق هر نفر", null, handSize, 1, 60, 1) { handSize = it; host.refresh() })
        parent.addView(kit.stepperRow("بستهٔ اول پخش", "در دور اول به هر نفر", first, 1, 20, 1) { first = it; host.refresh() })
        parent.addView(kit.stepperRow("بسته‌های بعدی", "در دورهای بعد به هر نفر", next, 1, 20, 1) { next = it; host.refresh() })
        val deck = settings.deck.copy(handSize = handSize, firstPacket = first, nextPacket = next)
        val d = DeckCalc.hezar(hezarPlayers, decks, jokers, deck, settings.hezar.cards)
        parent.addView(kit.section("نتیجه", RoyalIcon.CHART))
        parent.addView(kit.panel(if (d.fits) PanelStyle.SUCCESS else if (d.fixableFromBottom) PanelStyle.RAISED else PanelStyle.DANGER, 12).apply {
            layoutParams = kit.spaced(8)
            line(this, "کل ورق‌ها", "${kit.n(d.totalCards)} برگ")
            line(this, "به هر نفر", "${kit.n(d.handSize)} برگ")
            line(this, "پخش در ${kit.n(d.packets.size)} دور", d.packets.joinToString(" + ") { kit.n(it) })
            line(this, "ورق پخش‌شده", "${kit.n(d.dealt)} برگ")
            line(this, "ورق باقی‌مانده (زمین)", "${kit.n(d.stock)} برگ")
            line(this, "مجموع امتیاز ورق‌ها", kit.n(d.totalPoints))
            addView(kit.gap(6))
            val message = when {
                d.fits -> "ورق کافی است. دور اول ${kit.n(d.packets.first())} برگ" + (if (d.packets.size > 1) " و بعد ${d.packets.drop(1).joinToString(" و ") { kit.n(it) }} برگ" else "") + " به هر نفر بدهید."
                d.fixableFromBottom -> "${kit.n(d.short)} برگ کم می‌آید: ${kit.n(d.takeFromBottom)} کارت از زیر دسته بردارید، خوب بُر بزنید و پخش را کامل کنید."
                else -> "${kit.n(d.short)} برگ کم است. دست‌کم ${kit.n(d.decksNeeded)} دسته ورق لازم است، یا به هر نفر حداکثر ${kit.n(d.maxHandSize)} برگ بدهید."
            }
            addView(kit.text(message, TextStyle.BODY_BOLD, if (d.fits) Royal.turquoiseLight else Royal.ivory, Gravity.CENTER))
        })
        val v = settings.hezar.cards
        parent.addView(kit.text("ارزش ورق‌ها: ۲ تا ۹ = ${kit.n(v.low)} • ۱۰ تا شاه = ${kit.n(v.high)} • تک = ${kit.n(v.ace)} • جوکر = ${kit.n(v.joker)}", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
    }

    private fun dolo(parent: android.widget.LinearLayout) {
        parent.addView(kit.stepperRow("تعداد نفرات", null, doloPlayers, 2, MAX_PLAYERS, 1) { doloPlayers = it; host.refresh() })
        parent.addView(kit.stepperRow("تعداد کل ورق‌ها", "مثلاً یک دسته ${kit.n(52)} برگ", doloCards, 1, 2000, 1) { doloCards = it; host.refresh() })
        val d = DeckCalc.dolo(doloPlayers, doloCards, settings.dolo.rules)
        parent.addView(kit.section("نتیجه", RoyalIcon.CHART))
        parent.addView(kit.panel(if (d.removeCards == 0) PanelStyle.SUCCESS else PanelStyle.RAISED, 12).apply {
            layoutParams = kit.spaced(8)
            line(this, "ورق هر نفر", "${kit.n(d.perPlayer)} برگ")
            line(this, "ورق‌هایی که کنار گذاشته می‌شوند", "${kit.n(d.removeCards)} برگ")
            line(this, "حداقل خواندن", kit.n(d.minimum))
            line(this, "دست‌برد هر دست", kit.n(d.perPlayer))
            addView(kit.gap(6))
            addView(kit.text(
                if (d.removeCards == 0) "ورق‌ها دقیق تقسیم می‌شود؛ به هر نفر ${kit.n(d.perPlayer)} برگ بدهید."
                else "${kit.n(d.removeCards)} برگ از دسته خارج کنید (دو لو گشنیز را خارج نکنید) تا به هر نفر ${kit.n(d.perPlayer)} برگ برسد.",
                TextStyle.BODY_BOLD, if (d.removeCards == 0) Royal.turquoiseLight else Royal.ivory, Gravity.CENTER
            ))
        })
    }

    private fun line(parent: android.widget.LinearLayout, label: String, value: String) {
        parent.addView(kit.horizontal().apply {
            addView(kit.weight(kit.text(label, TextStyle.LABEL, Royal.muted)))
            addView(kit.text(value, TextStyle.BODY_BOLD, Royal.ivory))
        })
    }
}
