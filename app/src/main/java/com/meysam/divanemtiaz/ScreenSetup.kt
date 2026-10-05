package com.meysam.divanemtiaz

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

class SetupScreen(host: MainActivity, private val game: GameType) : Screen(host) {
    private val sides: MutableList<Side> = initialSides()
    private var joker = settings.shalam.defaultJoker
    private var endPoint = settings.shalam.mode(joker).defaultEndPoint
    private var dealType = settings.shalam.dealType
    private var menfiHands = settings.menfi.hands
    private var menfiHidden = settings.menfi.hidden
    private var hezarTarget = settings.hezar.target
    private var hezarRounds = settings.hezar.rounds
    private var hezarPenalty = settings.hezar.zeroPenalty
    private var dolo = settings.dolo.rules

    private fun initialSides(): MutableList<Side> = when (game) {
        GameType.HEZARTAII -> players(settings.hezar.players.coerceIn(2, MAX_PLAYERS))
        GameType.DOLO -> players(settings.dolo.players.coerceIn(game.minSides, MAX_PLAYERS))
        else -> mutableListOf(Side(settings.general.defaultTeam1, 0), Side(settings.general.defaultTeam2, 6))
    }

    private fun players(count: Int): MutableList<Side> =
        MutableList(count) { Side("بازیکن ${PersianText.digits(it + 1, settings.general.persianDigits)}", (it * 5) % Emblems.COUNT) }

    override fun build(): View = scaffold(
        title = "آماده‌سازی ${game.title}",
        subtitle = game.subtitle,
        bottom = kit.button("شروع داوری ${game.title}", ButtonKind.PRIMARY, RoyalIcon.PLAY) { start() }
    ) {
        addView(kit.section(if (game.isTeamGame) "تیم‌ها" else "بازیکنان (${kit.n(sides.size)})", RoyalIcon.PLAYERS))
        if (game.isTeamGame) sides.indices.forEach { addView(sideEditor(it)) }
        else {
            sides.indices.forEach { addView(playerRow(it)) }
            addView(kit.grid(3, listOf(
                kit.button("+ ۱ بازیکن", ButtonKind.GHOST, null, 40) { addPlayers(1) },
                kit.button("+ ۵ بازیکن", ButtonKind.GHOST, null, 40) { addPlayers(5) },
                kit.button("از فهرست", ButtonKind.GHOST, RoyalIcon.USER, 40) {
                    rosterPicker(false) { entry ->
                        if (sides.size < game.maxSides) sides += Side(entry.name, entry.avatar)
                        host.refresh()
                    }
                }
            ), 6), kit.spaced(8))
        }
        addView(kit.section("قوانین این بازی", RoyalIcon.RULES))
        when (game) {
            GameType.SHALAM -> shalamOptions(this)
            GameType.MENFI -> menfiOptions(this)
            GameType.HEZARTAII -> hezarOptions(this)
            GameType.DOLO -> doloOptions(this)
        }
        if (!game.isTeamGame) {
            addView(kit.button("ماشین‌حساب ورق برای ${kit.n(sides.size)} نفر", ButtonKind.GHOST, RoyalIcon.CHART, 42) {
                host.push(DeckCalcScreen(host, game, sides.size))
            }, kit.spaced(6))
        }
        addView(kit.gap(6))
        addView(kit.button("مشاهدهٔ شرح کامل قوانین", ButtonKind.GHOST, RoyalIcon.RULES, 46) {
            host.push(RulesScreen(host, null, game))
        })
    }

    private fun addPlayers(count: Int) {
        repeat(count) {
            if (sides.size < game.maxSides) sides += Side("بازیکن ${kit.n(sides.size + 1)}", (sides.size * 5) % Emblems.COUNT)
        }
        host.refresh()
    }

    /** Compact row for individual games, so dozens of players stay manageable. */
    private fun playerRow(index: Int): View {
        val side = sides[index]
        return kit.horizontal().apply {
            background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
            setPadding(kit.dp(8), kit.dp(4), kit.dp(8), kit.dp(7))
            layoutParams = kit.spaced(4)
            addView(kit.text(kit.n(index + 1), TextStyle.LABEL_BOLD, Royal.gold, Gravity.CENTER), LinearLayout.LayoutParams(kit.dp(24), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(kit.avatar(side.avatar, 32).apply {
                setOnClickListener {
                    kit.tap(it)
                    avatarPicker(sides[index].avatar) { picked ->
                        sides[index] = sides[index].copy(avatar = picked)
                        host.refresh()
                    }
                }
            })
            addView(kit.hgap(6))
            addView(kit.field("نام بازیکن", side.name).apply {
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (index < sides.size) sides[index] = sides[index].copy(name = s?.toString().orEmpty())
                    }
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (sides.size > game.minSides) {
                addView(kit.hgap(6))
                addView(kit.iconButton(RoyalIcon.TRASH, "حذف بازیکن", ButtonKind.CHIP, 34) {
                    sides.removeAt(index)
                    host.refresh()
                })
            }
        }
    }

    private fun sideEditor(index: Int): View {
        val side = sides[index]
        val nameField = kit.field(if (game.isTeamGame) "نام تیم" else "نام بازیکن", side.name).apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (index < sides.size) sides[index] = sides[index].copy(name = s?.toString().orEmpty())
                }
            })
        }
        return kit.horizontal().apply {
            background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
            setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(11))
            layoutParams = kit.spaced(6)
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                addView(kit.avatar(side.avatar, 48).apply {
                    setOnClickListener {
                        kit.tap(it)
                        avatarPicker(sides[index].avatar) { picked ->
                            sides[index] = sides[index].copy(avatar = picked)
                            host.refresh()
                        }
                    }
                })
                addView(kit.text("نشان", TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
            })
            addView(kit.hgap(10))
            addView(kit.vertical().apply {
                addView(kit.text(if (game.isTeamGame) "تیم ${kit.n(index + 1)}" else "بازیکن ${kit.n(index + 1)}", TextStyle.LABEL_BOLD, Royal.gold))
                addView(kit.gap(4))
                addView(nameField, kit.fill())
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(kit.hgap(8))
            addView(kit.vertical(Gravity.CENTER).apply {
                addView(kit.iconButton(RoyalIcon.USER, "انتخاب از فهرست", ButtonKind.CHIP, 40) {
                    rosterPicker(game.isTeamGame) { entry ->
                        sides[index] = Side(entry.name, entry.avatar)
                        host.refresh()
                    }
                })

            })
        }
    }

    private fun shalamOptions(parent: LinearLayout) {
        parent.addView(kit.choiceRow("جوکر", "حالت بازی؛ امتیاز هر دست ${kit.n(165)} یا با جوکر ${kit.n(200)}", listOf(0 to "بدون جوکر", 1 to "با جوکر"), if (joker) 1 else 0) {
            joker = it == 1
            endPoint = settings.shalam.mode(joker).defaultEndPoint
            host.refresh()
        })
        parent.addView(kit.stepperRow("امتیاز پایان بازی", "از ${kit.n(400)} تا ${kit.n(2800)}", endPoint, 400, 2800, 5) { endPoint = it })
        parent.addView(kit.choiceRow("نوع بازی", "برچسب این بازی در دفتر و تاریخچه", listOf(
            DealType.TWELVE to DealType.title(DealType.TWELVE),
            DealType.TWELVE_POSITIVE_ONLY to DealType.title(DealType.TWELVE_POSITIVE_ONLY),
            DealType.FOUR to DealType.title(DealType.FOUR)
        ), dealType) { dealType = it })
        val rules = settings.shalam.rules(joker, endPoint)
        parent.addView(kit.panel(PanelStyle.FLAT, 14).apply {
            layoutParams = kit.spaced(8)
            addView(kit.text("خلاصهٔ قوانین فعال", TextStyle.LABEL_BOLD, Royal.goldLight))
            RulesText.shalamSummary(rules, kit).forEach { addView(kit.text("•  $it", TextStyle.CAPTION, Royal.muted)) }
            addView(kit.gap(6))
            addView(kit.button("تغییر قوانین شلم", ButtonKind.SECONDARY, RoyalIcon.SLIDERS, 42) {
                host.push(SettingsScreen(host, SettingsScreen.TAB_SHALAM))
            })
        })
    }

    private fun menfiOptions(parent: LinearLayout) {
        parent.addView(kit.stepperRow("تعداد دست‌ها", "بازی پس از این تعداد دست تمام می‌شود", menfiHands, 1, 40, 1) { menfiHands = it })
        parent.addView(kit.switchRow("پنهان‌بودن جمع امتیاز", "جمع و امتیاز دست‌ها تا پایان بازی دیده نشود", menfiHidden) { menfiHidden = it })
        parent.addView(kit.panel(PanelStyle.FLAT, 14).apply {
            layoutParams = kit.spaced(8)
            RulesText.menfiSummary(settings.menfi.rules().copy(hands = menfiHands, hidden = menfiHidden), kit).forEach {
                addView(kit.text("•  $it", TextStyle.CAPTION, Royal.muted))
            }
        })
    }

    private fun hezarOptions(parent: LinearLayout) {
        parent.addView(kit.stepperRow("امتیاز هدف", "رسیدن به این امتیاز بازی را تمام می‌کند", hezarTarget, 100, 10000, 50) { hezarTarget = it })
        parent.addView(kit.stepperRow("تعداد دورها", "صفر یعنی بدون محدودیت دور", hezarRounds, 0, 100, 1, { if (it == 0) "نامحدود" else kit.n(it) }) { hezarRounds = it })
        parent.addView(kit.stepperRow("جریمهٔ صفر", "امتیاز صفر در یک دور با این مقدار ثبت می‌شود", hezarPenalty, -1000, 0, 10, { kit.signed(it) }) { hezarPenalty = it })
    }

    private fun doloOptions(parent: LinearLayout) {
        parent.addView(kit.text("حداقل خواندن برای ${kit.n(sides.size)} نفر: ${kit.n(dolo.minimumFor(sides.size))} (در هر دست هم قابل تغییر است)", TextStyle.LABEL_BOLD, Royal.turquoiseLight), kit.spaced(6))
        editDolo(parent, dolo) { dolo = it }
    }

    private fun start() {
        if (sides.size < game.minSides) {
            kit.toast("دست‌کم ${kit.n(game.minSides)} بازیکن لازم است")
            return
        }
        val defaults = initialSides()
        val finalSides = sides.mapIndexed { i, s ->
            Side(s.name.trim().ifBlank { defaults.getOrNull(i)?.name ?: "بازیکن ${kit.n(i + 1)}" }, s.avatar)
        }
        val base = settings.rulesFor(game, joker, endPoint)
        val rules = when (game) {
            GameType.SHALAM -> base.copy(shalam = base.shalam.copy(dealType = dealType))
            GameType.MENFI -> base.copy(menfi = base.menfi.copy(hands = menfiHands, hidden = menfiHidden))
            GameType.HEZARTAII -> base.copy(hezar = HezarRules(hezarTarget, hezarRounds, hezarPenalty))
            GameType.DOLO -> base.copy(dolo = dolo)
        }
        val now = host.repo.newSessionId()
        val session = GameSession(
            id = now,
            game = game,
            sides = finalSides,
            rules = rules,
            updatedAt = now,
            label = if (game == GameType.SHALAM) DealType.title(dealType) else ""
        )
        SessionOps.rosterAdd(host, finalSides, game.isTeamGame)
        host.replace(host.boardFor(session))
    }
}
