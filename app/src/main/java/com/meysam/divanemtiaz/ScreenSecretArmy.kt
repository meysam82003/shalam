package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** «میز ارتش سری»: guns, private talks, day shots with vests and saves, Reinhart, status and Chaos. */
class MafiaSecretArmyScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    override fun build(): View {
        if (!load()) return missing()
        val notes = SecretArmy.sync(g)
        if (notes.isNotEmpty()) store()
        return scaffold("میز ارتش سری", g.phaseTitle) {
            if (notes.isNotEmpty()) addView(narration(notes.joinToString("\n"), null, PanelStyle.SELECTED))
            addView(kit.panel(PanelStyle.RAISED, 12).apply {
                layoutParams = kit.spaced(10)
                fun line(t: String, v: String) = addView(kit.horizontal().apply {
                    addView(kit.weight(kit.text(t, TextStyle.LABEL, Royal.muted)))
                    addView(kit.text(v, TextStyle.BODY_BOLD, Royal.goldLight))
                })
                line("اسلحهٔ آزاد نزد گرداننده", kit.n(SecretArmy.narratorGuns(g)))
                line("شلیک امروز", "${kit.n(SecretArmy.shotsToday(g))} از ${kit.n(SecretArmy.MAX_PER_DAY)}")
                line("گفتگوی خصوصی امروز", "${kit.n(SecretArmy.talksToday(g))} از ${kit.n(SecretArmy.MAX_PER_DAY)}")
                line("نجات باقی‌ماندهٔ دکتر پاسکال", kit.n(SecretArmy.pascal(g)?.counter(SecretArmy.SAVES) ?: 0))
                line("حدس باقی‌ماندهٔ راینهارت", kit.n(SecretArmy.reinhartReady(g)?.counter(SecretArmy.GUESSES) ?: 0))
            })
            addView(kit.grid(2, listOf(
                kit.button("گفتگوی خصوصی (VIP)", ButtonKind.SECONDARY, RoyalIcon.USER, 48) { talk() },
                kit.button("شلیک", ButtonKind.DANGER, RoyalIcon.FLAG, 48) { shot() },
                kit.button("حدس راینهارت", ButtonKind.SECONDARY, RoyalIcon.EYE, 48) { reinhart() },
                kit.button("استعلام وضعیت", ButtonKind.SECONDARY, RoyalIcon.CHART, 48) { status() }
            ), 8), kit.spaced(8))
            if (SecretArmy.chaos(g)) addView(kit.button("کیاس: تصمیم خلبان", ButtonKind.PRIMARY, RoyalIcon.CROWN, 50) { chaos() }, kit.spaced(8))
            addView(kit.button("بررسی برد", ButtonKind.GHOST, RoyalIcon.TROPHY, 44) { winCheck() }, kit.spaced(10))
            addView(kit.text("اگر یک دور روز بدون شلیک تمام شود، وضعیت اضطراری است: اول حدس راینهارت، بعد رأی‌گیری با چشم بسته از مراحل روز. تساوی ← کسی خارج نمی‌شود.",
                TextStyle.CAPTION, Royal.muted), kit.spaced(8))
            addView(kit.section("بازیکنان", RoyalIcon.PLAYERS))
            g.players.forEach { p -> addView(row(p)) }
        }
    }

    private fun row(p: MafiaPlayer): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, if (p.alive) PanelStyle.FLAT else PanelStyle.GHOST, 14f)
        setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
        layoutParams = kit.spaced(6)
        addView(kit.avatar(p.avatar, 40))
        addView(kit.hgap(8))
        addView(kit.vertical().apply {
            addView(kit.text("${p.name} — ${p.role?.faName ?: ""}", TextStyle.BODY_BOLD, if (p.alive) Royal.ivory else Royal.dim, maxLines = 1))
            val tags = ArrayList<String>()
            tags += SecretArmy.faction(p).title
            if (SecretArmy.armed(p)) tags += "مسلح"
            if (p.has("fired")) tags += "شلیک کرده"
            if (p.has("talkUsed")) tags += "گفتگو مصرف شد"
            if (p.has("shotShield")) tags += "جلیقه مصرف شد"
            if (p.has("voteShield")) tags += "مصونیت رأی مصرف شد"
            if (!p.alive) tags += "بیرون"
            addView(kit.text(tags.joinToString(" • "), TextStyle.CAPTION, Royal.muted, maxLines = 2))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun talk() {
        val can = g.alive.filter { SecretArmy.canTalk(g, it) == null }
        if (can.isEmpty()) { kit.toast(SecretArmy.canTalk(g, g.alive.firstOrNull() ?: return) ?: "گفتگویی ممکن نیست"); return }
        pickPlayers("چه کسی گفتگوی خصوصی را درخواست می‌کند؟", can, 1, 1) { a ->
            val requester = g.player(a.first()) ?: return@pickPlayers
            pickPlayers("${requester.name} چه کسی را دعوت می‌کند؟", g.alive.filter { it.id != requester.id }, 1, 1) { b ->
                val invitee = g.player(b.first()) ?: return@pickPlayers
                SecretArmy.talk(g, requester, invitee)
                store()
                val rule = if (SecretArmy.mustTellSide(invitee)) "${invitee.name} باید ساید واقعی خود را بگوید: ${SecretArmy.faction(invitee).title}"
                    else "${invitee.name} (${invitee.role?.faName}) می‌تواند دربارهٔ ساید خود دروغ بگوید"
                kit.dialog("اتاق خصوصی", "$rule\nدرخواست‌کننده می‌تواند دروغ بگوید.\nتا پایان امروز هیچ‌کدام حق افشا یا علامت دادن دربارهٔ این گفتگو را ندارند.", null, listOf(
                    DialogAction("درخواست اسلحه", ButtonKind.PRIMARY) { gun(requester, invitee) },
                    DialogAction("پایان گفتگو") { host.refresh() }
                )).show()
            }
        }
    }

    private fun gun(requester: MafiaPlayer, invitee: MafiaPlayer) {
        val answer = SecretArmy.requestGun(g, requester, invitee)
        store()
        kit.dialog("پاسخ گرداننده", answer.text, null, listOf(DialogAction("تایید", ButtonKind.PRIMARY) { host.refresh() })).show()
    }

    private fun shot() {
        val shooters = g.alive.filter { SecretArmy.canShoot(g, it) == null }
        if (shooters.isEmpty()) { kit.toast(if (SecretArmy.shotsToday(g) >= SecretArmy.MAX_PER_DAY) "سقف شلیک امروز پر شده است" else "بازیکن مسلحی با شلیک باقی‌مانده نیست"); return }
        pickPlayers("چه کسی شلیک می‌کند؟", shooters, 1, 1) { a ->
            val shooter = g.player(a.first()) ?: return@pickPlayers
            pickPlayers("${shooter.name} به چه کسی شلیک می‌کند؟", g.alive.filter { it.id != shooter.id }, 1, 1) { b ->
                val target = g.player(b.first()) ?: return@pickPlayers
                when (SecretArmy.shoot(g, shooter, target)) {
                    SecretArmy.ShotState.VEST -> {
                        store()
                        kit.dialog("شلیک", "جلیقهٔ ${target.name} شلیک اول را گرفت؛ در بازی می‌ماند.", null, listOf(DialogAction("تایید", ButtonKind.PRIMARY) { host.refresh() })).show()
                    }
                    SecretArmy.ShotState.SAVABLE -> doctor(target)
                    SecretArmy.ShotState.DIES -> dies(target)
                }
            }
        }
    }

    /** The shot player names his guess of Dr Pascal; a right guess forces the save. */
    private fun doctor(target: MafiaPlayer) {
        store()
        pickPlayers("${target.name} صحبت پایانی می‌کند و دکتر پاسکال را حدس می‌زند", g.alive.filter { it.id != target.id }, 1, 1) { ids ->
            val guess = g.player(ids.first())
            if (guess?.role == MafiaRole.PASCAL) {
                SecretArmy.save(g, target, true)
                store()
                kit.dialog("نجات اجباری", "حدس درست بود؛ دکتر پاسکال ${target.name} را نجات داد و او در بازی می‌ماند.", null, listOf(DialogAction("تایید", ButtonKind.PRIMARY) { host.refresh() })).show()
            } else {
                kit.dialog("دکتر پاسکال", "حدس اشتباه بود. دکتر پاسکال (${SecretArmy.pascal(g)?.name}) ${target.name} را نجات می‌دهد؟", null, listOf(
                    DialogAction("نجات می‌دهد", ButtonKind.SUCCESS) { SecretArmy.save(g, target, false); store(); host.refresh() },
                    DialogAction("نجات نمی‌دهد", ButtonKind.DANGER) { dies(target) }
                )).show()
            }
        }
    }

    private fun dies(target: MafiaPlayer) {
        store()
        if (target.role == MafiaRole.PILOT) {
            kit.dialog("مرگ خلبان", "طبق وایت‌شو: جناحی که با شلیک دوم خلبان را بکشد بازنده است؛ مگر اینکه از گشتاپو فقط یک عضو مسلح مانده باشد (برد گشتاپو) یا در شرایط ویژه آخرین مأمور نجات مسلح شلیک کرده باشد. راوی نتیجه را تعیین کند.", null, listOf(
                DialogAction("برد گروه نجات", ButtonKind.SUCCESS) { exitThen(target) { MafiaManual.declare(g, "CITY") } },
                DialogAction("برد گشتاپو", ButtonKind.DANGER) { exitThen(target) { MafiaManual.declare(g, "MAFIA") } },
                DialogAction("ادامهٔ بازی") { resultThenExit("شلیک", "${target.name} از بازی خارج می‌شود", listOf(target.id), MafiaDeath.GUN, allowCard = false) }
            )).show()
            return
        }
        resultThenExit("شلیک", "${target.name} از بازی خارج می‌شود (فرصت صحبت پایانی دارد)", listOf(target.id), MafiaDeath.GUN, allowCard = false)
    }

    private fun exitThen(target: MafiaPlayer, then: () -> Unit) {
        MafiaFlow.exit(g, target.id, MafiaDeath.GUN)
        then()
        store()
        host.refresh()
    }

    private fun reinhart() {
        val r = SecretArmy.reinhartReady(g) ?: run { kit.toast("راینهارت در بازی نیست یا توانایی‌اش تمام شده است"); return }
        pickPlayers("راینهارت نقش چه کسی را حدس می‌زند؟ (خلبان مجاز نیست)", g.alive.filter { it.id != r.id && it.role != MafiaRole.PILOT }, 1, 1) { ids ->
            val t = g.player(ids.first()) ?: return@pickPlayers
            rolePicker("نقش ${t.name}", g.players.mapNotNull { it.role }.distinct().filter { it != MafiaRole.PILOT }) { role ->
                val right = SecretArmy.reinhartGuess(g, t, role)
                store()
                if (right) resultThenExit("راینهارت", "حدس درست بود؛ ${t.name} از بازی خارج می‌شود و توانایی راینهارت تمام شد", listOf(t.id), MafiaDeath.EXECUTE, allowCard = false)
                else kit.dialog("راینهارت", "حدس اشتباه بود" + if ((g.aliveHolder(MafiaRole.REINHART)?.counter(SecretArmy.GUESSES) ?: 0) > 0) "؛ یک فرصت دیگر دارد" else "؛ توانایی تمام شد",
                    null, listOf(DialogAction("تایید", ButtonKind.PRIMARY) { host.refresh() })).show()
            }
        }
    }

    private fun status() {
        val text = if (g.out.size < 2) "استعلام بعد از خروج دو بازیکن اعلام می‌شود.\n\n" + SecretArmy.status(g) else SecretArmy.status(g)
        kit.dialog("استعلام وضعیت", text, null, listOf(DialogAction("بستن"))).show()
    }

    private fun chaos() {
        val pilot = g.alive.firstOrNull { it.role == MafiaRole.PILOT } ?: return
        pickPlayers("کیاس: گرداننده به ${pilot.name} اسلحه می‌دهد؛ دو نفر دیگر صحبت می‌کنند و خلبان به یکی شلیک می‌کند", g.alive.filter { it.id != pilot.id }, 1, 1) { ids ->
            val t = g.player(ids.first()) ?: return@pickPlayers
            val win = if (SecretArmy.faction(t) == SecretArmy.Faction.GESTAPO) "CITY" else "MAFIA"
            MafiaFlow.exit(g, t.id, MafiaDeath.GUN)
            MafiaManual.declare(g, win)
            store()
            playCue(MafiaFlow.winCue(g.winner))
            host.refresh()
        }
    }

    private fun winCheck() {
        val w = SecretArmy.winner(g)
        if (w == null) { kit.toast(if (SecretArmy.chaos(g)) "کیاس: تصمیم با خلبان است" else "هنوز برنده‌ای مشخص نیست"); return }
        kit.confirm("پایان بازی", if (w == "CITY") "همهٔ گشتاپوها خارج شده‌اند؛ گروه نجات برنده است." else "تعداد گشتاپو با گروه نجات برابر شده؛ گشتاپو برنده است.", "اعلام برنده") {
            MafiaManual.declare(g, w); store(); host.refresh()
        }
    }
}
