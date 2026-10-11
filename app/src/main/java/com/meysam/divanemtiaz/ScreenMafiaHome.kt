package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/** «راوی مافیا» start page (source: startup screen). */
class MafiaHomeScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = run { MafiaRoleRegistry.activate(null); page() }

    private fun page(): View = scaffold("راوی مافیا", "گردانندگی بازی مافیا به سبکی نو",
        actions = listOf(kit.iconButton(RoyalIcon.SETTINGS, "تنظیمات راوی", ButtonKind.SECONDARY, 44) { host.push(MafiaSettingsScreen(host)) })) {
        addView(hero())
        host.mafia.latestUnfinished()?.let { g ->
            addView(kit.panel(PanelStyle.SELECTED, 12).apply {
                layoutParams = kit.spaced(8)
                addView(kit.horizontal().apply {
                    addView(seal(if (g.isDay) MafiaGlyph.CROWN else MafiaGlyph.MOON, null, 48))
                    addView(kit.hgap(10))
                    addView(kit.vertical().apply {
                        addView(kit.text("ادامهٔ بازی — ${g.phaseTitle}", TextStyle.BODY_BOLD, Royal.turquoiseLight))
                        addView(kit.text("${kit.n(g.alive.size)} نفر در بازی  •  ${kit.n(g.out.size)} نفر بیرون", TextStyle.CAPTION, Royal.muted))
                        addView(kit.text(JalaliDate.format(g.updatedAt, settings.general.persianDigits), TextStyle.CAPTION, Royal.dim))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                })
                addView(kit.gap(8))
                addView(kit.button("ادامه بازی گذشته", ButtonKind.SUCCESS, RoyalIcon.PLAY, 48) { host.push(MafiaGameScreen(host, g.id)) })
            })
        }
        addView(kit.button("شروع بازی جدید", ButtonKind.PRIMARY, RoyalIcon.PLAY, 54) { host.push(MafiaScenarioListScreen(host)) }, kit.spaced(10))
        addView(kit.grid(2, listOf(
            tile(MafiaGlyph.SCROLL, "سناریوها", "${kit.n(MafiaScenarios.builtin.size)} سناریو + سناریوی خودت") { host.push(MafiaScenarioListScreen(host, browse = true)) },
            tile(MafiaGlyph.MASK, "نقش‌ها و قوانین", "${kit.n(MafiaRoleRegistry.available().size)} نقش • افزودن و ویرایش") { host.push(MafiaRolesScreen(host)) },
            tile(MafiaGlyph.CARDS, "کارت حرکت آخر", "${kit.n(MafiaLastCard.values().size)} کارت") { host.push(MafiaCardsScreen(host)) },
            tile(MafiaGlyph.SCROLL, "گروه بازی", "بازیکنان و نقش‌های ذخیره‌شده") { host.push(MafiaGroupsScreen(host)) },
            tile(MafiaGlyph.HOURGLASS, "بازی‌های گذشته", "ادامه و گزارش بازی") { host.push(MafiaGamesScreen(host)) },
            tile(MafiaGlyph.BOOK, "قوانین بازی", "روند شب و روز") { host.push(MafiaRulesScreen(host)) },
            tile(MafiaGlyph.BELL, "تنظیمات راوی", "زمان‌ها، صداها و تصاویر") { host.push(MafiaSettingsScreen(host)) }
        ), 8))
    }

    private fun hero(): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        layoutParams = kit.spaced(8)
        addView(FrameLayout(host).apply {
            addView(roleCard(MafiaRole.GODFATHER).apply { rotation = 8f }, FrameLayout.LayoutParams(kit.dp(108), kit.dp(153), Gravity.CENTER).apply { leftMargin = kit.dp(110) })
            addView(roleCard(MafiaRole.DETECTIVE).apply { rotation = -8f }, FrameLayout.LayoutParams(kit.dp(108), kit.dp(153), Gravity.CENTER).apply { rightMargin = kit.dp(110) })
            addView(roleCard(MafiaRole.CITIZEN), FrameLayout.LayoutParams(kit.dp(120), kit.dp(170), Gravity.CENTER))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(186)))
        addView(kit.text("مافیا بازی، گردانندگی به سبکی نو", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
    }

    private fun tile(glyph: MafiaGlyph, title: String, caption: String, onClick: () -> Unit): View =
        kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
            setPadding(kit.dp(6), kit.dp(12), kit.dp(6), kit.dp(14))
            addView(seal(glyph, null, 52))
            addView(kit.gap(4))
            addView(kit.text(title, TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER, 1))
            addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 2))
            isClickable = true
            contentDescription = title
            setOnClickListener { kit.tap(it); onClick() }
        }
}

/** «نقش‌ها و قوانین»: every role card with its rules, filtered by side; add a role or edit any role's rules. */
class MafiaRolesScreen(host: MainActivity) : Screen(host) {
    private var side = MafiaSide.CITY

    override fun build(): View {
        MafiaRoleRegistry.activate(null)
        val roles = MafiaRoleRegistry.available()
        return scaffold("نقش‌ها و قوانین", "${kit.n(roles.size)} نقش  •  روی هر نقش بزنید",
            bottom = kit.button("افزودن نقش", ButtonKind.PRIMARY, RoyalIcon.PLUS, 52) { host.push(MafiaRoleEditScreen(host, null)) }) {
            addView(kit.grid(3, MafiaSide.values().map { s ->
                kit.chip("${s.title} (${kit.n(roles.count { it.side == s })})", side == s, ButtonKind.CHIP_GOLD) { side = s; host.refresh() }
            }, 6), kit.spaced(10))
            val mine = roles.filter { it.side == side && it.isCustomSlot }
            if (mine.isNotEmpty()) {
                addView(kit.section("نقش‌های ساخت شما", RoyalIcon.STAR))
                addView(grid(mine), kit.spaced(10))
                addView(kit.section("نقش‌های برنامه", RoyalIcon.CROWN))
            }
            addView(grid(roles.filter { it.side == side && !it.isCustomSlot }))
        }
    }

    private fun grid(list: List<MafiaRole>): View = kit.grid(3, list.map { r ->
        android.widget.FrameLayout(host).apply {
            addView(roleCard(r), android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (MafiaRoleRegistry.user.containsKey(r) && !r.isCustomSlot) addView(kit.badge("ویرایش‌شده", Royal.turquoise, true),
                android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT))
            isClickable = true
            contentDescription = r.faName
            setOnClickListener { kit.tap(it); roleInfo(r, extraActions = listOf(DialogAction("ویرایش قوانین", ButtonKind.PRIMARY) { host.push(MafiaRoleEditScreen(host, r)) })) }
        }
    }, 8)
}

/** «کارت حرکت آخر» catalogue. */
class MafiaCardsScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("کارت حرکت آخر", "${kit.n(MafiaLastCard.values().size)} کارت") {
        addView(kit.text("بازیکنی که در روز از بازی خارج می‌شود، در صورت داشتن حق انتخاب، یکی از کارت‌های برعکس را برمی‌دارد.", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(10))
        addView(kit.grid(3, MafiaLastCard.values().map { c ->
            lastCard(c).apply {
                isClickable = true
                setOnClickListener { kit.tap(it); cardInfo(c) }
            }
        }, 8))
    }
}

/** Saved and finished narrator games. */
class MafiaGamesScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("بازی‌های گذشته", "ادامه یا گزارش بازی") {
        val games = host.mafia.games().filter { it.started }
        if (games.isEmpty()) {
            addView(kit.text("هنوز بازی مافیایی ثبت نشده است", TextStyle.BODY, Royal.muted, Gravity.CENTER), kit.spaced(10))
            return@scaffold
        }
        games.forEach { g ->
            addView(kit.panel(if (g.finished) PanelStyle.NORMAL else PanelStyle.SELECTED, 12).apply {
                layoutParams = kit.spaced(8)
                addView(kit.horizontal().apply {
                    addView(seal(if (g.finished) MafiaGlyph.CROWN else if (g.isDay) MafiaGlyph.HOURGLASS else MafiaGlyph.MOON, null, 46))
                    addView(kit.hgap(10))
                    addView(kit.vertical().apply {
                        addView(kit.text(if (g.finished) g.winner else "در جریان — ${g.phaseTitle}", TextStyle.BODY_BOLD, if (g.finished) Royal.goldLight else Royal.turquoiseLight, maxLines = 2))
                        addView(kit.text("${kit.n(g.players.size)} بازیکن  •  ${JalaliDate.format(g.updatedAt, settings.general.persianDigits)}", TextStyle.CAPTION, Royal.muted))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                })
                addView(kit.gap(8))
                addView(kit.horizontal().apply {
                    if (!g.finished) {
                        addView(kit.weight(kit.button("ادامه", ButtonKind.SUCCESS, RoyalIcon.PLAY, 44) { host.push(MafiaGameScreen(host, g.id)) }))
                        addView(kit.hgap(6))
                    }
                    addView(kit.weight(kit.button("گزارش بازی", ButtonKind.SECONDARY, RoyalIcon.HISTORY, 44) { host.push(MafiaReportScreen(host, g.id)) }))
                    addView(kit.hgap(6))
                    addView(kit.iconButton(RoyalIcon.TRASH, "حذف", ButtonKind.DANGER, 44) {
                        kit.confirm("حذف بازی", "این بازی و گزارش آن حذف شود؟", "حذف", true) { host.mafia.delete(g.id); host.refresh() }
                    })
                })
            })
        }
    }
}

/** «گروه بازی»: saved line-ups with roles, options and cards; starting one goes straight to distribution. */
class MafiaGroupsScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("گروه بازی", "بازیکنان، نقش‌ها و کارت‌های ذخیره‌شده") {
        val groups = host.mafia.groups()
        if (groups.length() == 0) {
            addView(kit.text("هیچ گروهی وجود ندارد", TextStyle.BODY, Royal.muted, Gravity.CENTER), kit.spaced(8))
            addView(kit.text("در صفحهٔ طراحی بازی با «ذخیره به عنوان گروه» یک گروه بسازید.", TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
            return@scaffold
        }
        for (i in 0 until groups.length()) {
            val o = groups.optJSONObject(i) ?: continue
            val g = runCatching { MafiaCodec.decodeGame(o.getJSONObject("game")) }.getOrNull() ?: continue
            addView(kit.panel(PanelStyle.NORMAL, 12).apply {
                layoutParams = kit.spaced(8)
                addView(kit.text("نام گروه :\n${o.optString("name")}", TextStyle.BODY_BOLD, Royal.goldLight))
                addView(kit.text("${kit.n(g.players.size)} بازیکن  •  ${kit.n(g.roleCounts.values.sum())} نقش  •  ${kit.n(g.deck.size)} کارت", TextStyle.CAPTION, Royal.muted))
                addView(kit.gap(6))
                addView(kit.horizontal().apply {
                    addView(kit.weight(kit.button("شروع", ButtonKind.SUCCESS, RoyalIcon.PLAY, 44) {
                        host.mafia.saveDraft(g)
                        host.push(MafiaDistributeScreen(host))
                    }))
                    addView(kit.hgap(6))
                    addView(kit.weight(kit.button("ویرایش", ButtonKind.SECONDARY, RoyalIcon.EDIT, 44) {
                        host.mafia.saveDraft(g)
                        host.push(MafiaDesignScreen(host))
                    }))
                    addView(kit.hgap(6))
                    addView(kit.iconButton(RoyalIcon.TRASH, "حذف", ButtonKind.DANGER, 44) {
                        kit.confirm("حذف گروه", "گروه «${o.optString("name")}» حذف شود؟", "حذف", true) {
                            val a = org.json.JSONArray()
                            for (k in 0 until groups.length()) if (k != i) a.put(groups.get(k))
                            host.mafia.saveGroups(a)
                            host.refresh()
                        }
                    })
                })
            })
        }
    }
}

/** Short guide to the narrator flow. */
class MafiaRulesScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("قوانین بازی", "روند گردانندگی") {
        listOf(
            "طراحی بازی" to "بازیکنان را انتخاب کنید، نقش‌ها و قابلیت‌هایشان را تنظیم کنید و در صورت تمایل کارت‌های حرکت آخر را اضافه کنید. تعداد نقش‌ها باید با تعداد بازیکنان برابر باشد و نقش‌های شهروندی بیشتر از مافیا باشند.",
            "توزیع نقش" to "نقش‌ها به صورت تصادفی (یا دستی) پخش می‌شود. هر بازیکن روی نام خودش می‌زند، کارت نقش را می‌بیند و «دیدم» را می‌زند. وقتی همه دیدند «شروع» فعال می‌شود.",
            "شب معارفه" to "گروه مافیا بیدار می‌شوند و همدیگر را می‌شناسند، سپس هر نقش خودش را به راوی معرفی می‌کند. چند نقش مثل کرونا و نوستراداموس در همین شب کارشان را انجام می‌دهند.",
            "شب" to "دکمهٔ هر نقش به ترتیب پیشنهادی نمایش داده می‌شود. نقش‌هایی که صاحبشان بیرون رفته ولی نقششان اعلام نشده همچنان صوری اجرا می‌شوند تا کسی متوجه نشود. پاسخ استعلام‌ها فوراً نشان داده می‌شود و پایان شب همهٔ شلیک‌ها، نجات‌ها و زره‌ها را حساب می‌کند.",
            "روز" to "گزارش شب، صحبت با زمان و چالش، رأی‌گیری دور اول (حد نصاب ورود به دفاع)، دفاعیه، رأی‌گیری دور دوم، شهردار و نتیجه. با تساوی، قرعهٔ مرگ انداخته می‌شود. بازیکن خارج‌شده در صورت داشتن حق، کارت حرکت آخر برمی‌دارد.",
            "پایان بازی" to "اگر مافیایی نماند شهروندان برنده‌اند و اگر تعداد مافیا به تعداد شهروندان برسد مافیا برنده است. نقش‌های مستقل شرط برد خودشان را دارند. با سه نفر باقی‌مانده کار به «دست دادن» می‌کشد."
        ).forEach { (t, b) ->
            addView(kit.section(t, RoyalIcon.RULES))
            addView(kit.text(b, TextStyle.BODY, Royal.ivory), kit.spaced(6))
        }
    }
}
