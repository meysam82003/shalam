package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View

/** Deck calculator for «هزارتایی» and «دو لو گشنیز»: every answer is a whole number of cards. */
class DeckCalcScreen(host: MainActivity, initialGame: GameType = GameType.HEZARTAII, players: Int = 0) : Screen(host) {
    private var game = if (initialGame == GameType.DOLO) GameType.DOLO else GameType.HEZARTAII
    private var hezarPlayers = if (players > 1 && game == GameType.HEZARTAII) players else settings.hezar.players.coerceAtLeast(2)
    private var doloPlayers = if (players > 1 && game == GameType.DOLO) players else settings.dolo.players
    private var doloCards = settings.deck.doloCards

    override fun build(): View = scaffold(title = "ماشین‌حساب ورق", subtitle = "تعداد ورق هر نفر، پخش و ورق‌های اضافه") {
        addView(kit.grid(2, listOf(
            kit.chip("هزارتایی", game == GameType.HEZARTAII, ButtonKind.CHIP_GOLD) { game = GameType.HEZARTAII; host.refresh() },
            kit.chip("دو لو گشنیز", game == GameType.DOLO, ButtonKind.CHIP_GOLD) { game = GameType.DOLO; host.refresh() }
        ), 6), kit.spaced(8))
        if (game == GameType.HEZARTAII) hezar(this) else dolo(this)
    }

    private fun hezar(parent: android.widget.LinearLayout) {
        val deck = settings.deck
        parent.addView(kit.stepperRow("تعداد نفرات", null, hezarPlayers, 2, MAX_PLAYERS, 1) { hezarPlayers = it; host.refresh() })
        parent.addView(kit.switchRow("تعداد کل ورق‌ها را خودم وارد می‌کنم", "به جای تعداد دسته و جوکر، مثلاً بگویید ${kit.n(80)} برگ", deck.customTotalEnabled) { v ->
            save { it.copy(customTotalEnabled = v) }
        })
        if (deck.customTotalEnabled) {
            parent.addView(kit.stepperRow("تعداد کل ورق‌ها", null, deck.customTotal, 1, 2000, 1) { v -> save { it.copy(customTotal = v) } })
        } else {
            parent.addView(kit.stepperRow("تعداد دستهٔ ورق", "هر دسته ${kit.n(52)} برگ", deck.decks, 1, 40, 1) { v -> save { it.copy(decks = v) } })
            parent.addView(kit.stepperRow("تعداد کل جوکرها", null, deck.jokers, 0, 80, 1) { v -> save { it.copy(jokers = v) } })
        }
        parent.addView(kit.switchRow("ورق هر نفر و پخش را خودم تعیین می‌کنم", "اگر خاموش باشد، برنامه تعداد ورق هر نفر و دورهای پخش را حساب می‌کند", !deck.autoHand) { v ->
            save { it.copy(autoHand = !v) }
        })
        if (deck.autoHand) {
            parent.addView(kit.stepperRow("ورق دلخواه هر نفر", "اگر ورق کافی نباشد، کمتر پیشنهاد می‌شود", deck.handSize, 1, 60, 1) { v -> save { it.copy(handSize = v) } })
            autoResult(parent, DeckCalc.hezarAuto(hezarPlayers, deck))
        } else {
            parent.addView(kit.stepperRow("ورق هر نفر", null, deck.handSize, 1, 60, 1) { v -> save { it.copy(handSize = v) } })
            parent.addView(kit.stepperRow("بستهٔ اول پخش", "در دور اول به هر نفر", deck.firstPacket, 1, 20, 1) { v -> save { it.copy(firstPacket = v) } })
            parent.addView(kit.stepperRow("بسته‌های بعدی", "در دورهای بعد به هر نفر", deck.nextPacket, 1, 20, 1) { v -> save { it.copy(nextPacket = v) } })
            manualResult(parent, deck)
        }
        val v = settings.hezar.cards
        parent.addView(kit.text("ارزش ورق‌ها: ۲ تا ۹ = ${kit.n(v.low)} • ۱۰ تا شاه = ${kit.n(v.high)} • تک = ${kit.n(v.ace)} • جوکر = ${kit.n(v.joker)}", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
    }

    private fun save(change: (DeckSettings) -> DeckSettings) {
        host.updateSettings { it.copy(deck = change(it.deck)) }
        host.refresh()
    }

    private fun ordinal(i: Int): String = when (i) {
        0 -> "اول"
        1 -> "دوم"
        2 -> "سوم"
        3 -> "چهارم"
        4 -> "پنجم"
        else -> kit.n(i + 1) + "م"
    }

    private fun autoResult(parent: android.widget.LinearLayout, d: AutoDeal) {
        parent.addView(kit.section("نتیجه", RoyalIcon.CHART))
        parent.addView(kit.panel(when (d.mode) {
            DealMode.FITS -> PanelStyle.SUCCESS
            DealMode.BOTTOM -> PanelStyle.RAISED
            DealMode.REDUCED -> PanelStyle.DANGER
        }, 12).apply {
            layoutParams = kit.spaced(8)
            addView(kit.text("به هر نفر ${kit.n(d.handSize)} برگ", TextStyle.TITLE, Royal.goldLight, Gravity.CENTER))
            addView(kit.gap(4))
            d.packets.forEachIndexed { i, p ->
                line(this, "دور ${ordinal(i)} پخش", "${kit.n(p)} برگ به هر نفر")
            }
            line(this, "کل ورق‌ها", "${kit.n(d.totalCards)} برگ")
            line(this, "ورق پخش‌شده", "${kit.n(d.dealt)} برگ")
            line(this, "ورق باقی‌مانده (زمین)", "${kit.n(d.stock)} برگ")
            if (!settings.deck.customTotalEnabled) line(this, "مجموع امتیاز ورق‌ها", kit.n(CardCalc.deckPoints(settings.deck.decks, settings.deck.jokers, settings.hezar.cards)))
            addView(kit.gap(6))
            val message = when (d.mode) {
                DealMode.FITS -> "ورق کافی است. " + d.packets.mapIndexed { i, p -> "دور ${ordinal(i)} ${kit.n(p)} برگ" }.joinToString("، ") + " به هر نفر بدهید."
                DealMode.BOTTOM -> "${kit.n(d.short)} برگ کم می‌آید: ${kit.n(d.takeFromBottom)} کارت از زیر دسته بردارید، خوب بُر بزنید و پخش را کامل کنید."
                DealMode.REDUCED -> "برای ${kit.n(d.preferredHand)} برگ به هر نفر ورق کافی نیست؛ به هر نفر ${kit.n(d.handSize)} برگ بدهید. " +
                    if (settings.deck.customTotalEnabled) "برای ${kit.n(d.preferredHand)} برگ دست‌کم ${kit.n(d.cardsNeeded)} برگ لازم است."
                    else "برای ${kit.n(d.preferredHand)} برگ به هر نفر ${kit.n(d.decksNeeded)} دسته ورق لازم است."
            }
            addView(kit.text(message, TextStyle.BODY_BOLD, if (d.mode == DealMode.FITS) Royal.turquoiseLight else Royal.ivory, Gravity.CENTER))
        })
    }

    private fun manualResult(parent: android.widget.LinearLayout, deck: DeckSettings) {
        val d = DeckCalc.hezar(hezarPlayers, deck.decks, deck.jokers, deck, settings.hezar.cards)
        val total = deck.totalCards()
        val dealt = hezarPlayers * deck.handSize
        val stock = total - dealt
        parent.addView(kit.section("نتیجه", RoyalIcon.CHART))
        parent.addView(kit.panel(if (stock >= 0) PanelStyle.SUCCESS else if (-stock <= deck.shortAllowance) PanelStyle.RAISED else PanelStyle.DANGER, 12).apply {
            layoutParams = kit.spaced(8)
            line(this, "کل ورق‌ها", "${kit.n(total)} برگ")
            line(this, "به هر نفر", "${kit.n(deck.handSize)} برگ")
            d.packets.forEachIndexed { i, p -> line(this, "دور ${ordinal(i)} پخش", "${kit.n(p)} برگ به هر نفر") }
            line(this, "ورق پخش‌شده", "${kit.n(dealt)} برگ")
            line(this, "ورق باقی‌مانده (زمین)", "${kit.n(stock.coerceAtLeast(0))} برگ")
            addView(kit.gap(6))
            val message = when {
                stock >= 0 -> "ورق کافی است."
                -stock <= deck.shortAllowance -> "${kit.n(-stock)} برگ کم می‌آید: ${kit.n(deck.shortAllowance)} کارت از زیر دسته بردارید، خوب بُر بزنید و پخش را کامل کنید."
                else -> "${kit.n(-stock)} برگ کم است؛ به هر نفر حداکثر ${kit.n(total / hezarPlayers.coerceAtLeast(1))} برگ می‌رسد."
            }
            addView(kit.text(message, TextStyle.BODY_BOLD, if (stock >= 0) Royal.turquoiseLight else Royal.ivory, Gravity.CENTER))
        })
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
