package com.meysam.divanemtiaz

import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * «تنظیمات راوی»: the source app's settings plus a sound for every narrator moment (built-in, a file from the
 * phone or the narrator's own recorded voice) and the narrator's own picture for each role.
 */
class MafiaSettingsScreen(host: MainActivity, private var tab: Int = 0) : Screen(host) {
    private var side = MafiaSide.CITY

    private fun update(change: (MafiaSettings) -> MafiaSettings, refresh: Boolean = false) {
        host.mafia.saveSettings(change(mafiaSettings))
        if (refresh) host.refresh()
    }

    override fun build(): View = scaffold("تنظیمات راوی", "زمان‌ها، قوانین، صداها و تصاویر") {
        addView(kit.grid(3, listOf("بازی", "صداها", "تصاویر نقش").mapIndexed { i, t ->
            kit.chip(t, tab == i, ButtonKind.CHIP_GOLD) { tab = i; host.refresh() }
        }, 6), kit.spaced(8))
        when (tab) {
            0 -> mafiaGameSettings(this)
            1 -> sounds(this)
            else -> pictures(this)
        }
    }

    override fun onHide() {
        val audio = host.audio()
        audio.stopCue()
        if (audio.isRecording) audio.stopRecording(discard = true)
    }

    // ------------------------------------------------------------------ game

    // ------------------------------------------------------------------ sounds

    private fun label(c: SoundChoice?): String = when {
        c == null || !c.isSet -> "بدون صدا"
        c.source == SoundSource.BUILTIN -> MafiaBuiltinSounds.title(c.builtin)
        else -> c.label.ifBlank { "فایل انتخابی" }
    }

    private fun sounds(parent: LinearLayout) {
        val s = mafiaSettings
        parent.addView(kit.switchRow("افکت صوتی", "همهٔ صداهای راوی (به جز موزیک شب)", s.soundEffects) { v -> update({ it.copy(soundEffects = v) }) })
        parent.addView(kit.switchRow("پخش خودکار صدای هر مرحله", "با باز شدن هر مرحله صدایش پخش شود؛ اگر خاموش باشد فقط با دکمهٔ پخش", s.autoPlay) { v -> update({ it.copy(autoPlay = v) }) })
        parent.addView(kit.stepperRow("بلندی افکت صوتی", null, s.effectVolume, 0, 100, 10, { kit.n(it) + "٪" }) { v -> update({ it.copy(effectVolume = v) }) })
        parent.addView(kit.section("موزیک شب", RoyalIcon.PLAY))
        parent.addView(soundRow("موزیک شب", "در شب به صورت تکرار پخش می‌شود", s.nightMusic, music = true))
        parent.addView(kit.stepperRow("بلندی موزیک شب", null, s.musicVolume, 0, 100, 10, { kit.n(it) + "٪" }) { v -> update({ it.copy(musicVolume = v) }) })
        parent.addView(kit.section("صدای مراحل بازی", RoyalIcon.FLAG))
        parent.addView(kit.text("برای هر جمله‌ای که راوی باید بگوید صدا بگذارید تا با یک لمس پخش شود و راوی نیازی به گفتن نداشته باشد.", TextStyle.CAPTION, Royal.muted), kit.spaced(6))
        MafiaCues.general.forEach { (key, title) -> parent.addView(soundRow(title, null, s.cue(key), key = key)) }
        parent.addView(kit.section("صدای نقش‌ها", RoyalIcon.PLAYERS))
        parent.addView(kit.grid(3, MafiaSide.values().map { sd -> kit.chip(sd.title, side == sd, ButtonKind.CHIP_GOLD) { side = sd; host.refresh() } }, 6), kit.spaced(8))
        MafiaRoleRegistry.available().filter { it.side == side }.forEach { r ->
            val keys = listOf(MafiaCues.wake(r), MafiaCues.done(r), MafiaCues.sleep(r))
            val setCount = keys.count { s.cue(it)?.isSet == true }
            parent.addView(kit.horizontal().apply {
                background = PanelDrawable(kit.density, PanelStyle.FLAT, 16f)
                setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
                layoutParams = kit.spaced(6)
                addView(seal(r.glyph, r.side, 40))
                addView(kit.hgap(8))
                addView(kit.vertical().apply {
                    addView(kit.text(r.faName, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1))
                    addView(kit.text(if (setCount == 0) "بدون صدا" else "${kit.n(setCount)} صدا از ${kit.n(3)}", TextStyle.CAPTION, if (setCount > 0) Royal.turquoiseLight else Royal.muted))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(kit.icon(RoyalIcon.NEXT, Royal.gold, 20))
                isClickable = true
                setOnClickListener { kit.tap(it); roleSounds(r) }
            })
        }
        parent.addView(kit.gap(6))
        parent.addView(kit.button("بازگردانی همهٔ صداها به پیش‌فرض", ButtonKind.GHOST, RoyalIcon.UNDO, 44) {
            kit.confirm("صداهای پیش‌فرض", "همهٔ صداهای انتخابی و ضبط‌شده کنار گذاشته شوند؟", "بازگردانی", true) { update({ it.copy(sounds = emptyMap()) }, true) }
        })
    }

    private fun roleSounds(r: MafiaRole) {
        lateinit var dialog: Dialog
        val body = kit.vertical().apply {
            listOf(MafiaCues.wake(r), MafiaCues.done(r), MafiaCues.sleep(r)).forEach { key ->
                addView(soundRow(MafiaCues.title(key), null, mafiaSettings.cue(key), key = key) { dialog.dismiss(); roleSounds(r) })
            }
        }
        dialog = kit.dialog(r.faName, "صدای بیدار شدن، پایان کار و خوابیدن", body, listOf(DialogAction("بستن") { host.refresh() }))
        dialog.show()
    }

    /** One sound setting: title, current choice, play and change buttons. */
    private fun soundRow(title: String, subtitle: String?, current: SoundChoice?, key: String = "", music: Boolean = false, after: (() -> Unit)? = null): View =
        kit.horizontal().apply {
            background = PanelDrawable(kit.density, PanelStyle.FLAT, 16f)
            setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
            layoutParams = kit.spaced(6)
            addView(kit.vertical().apply {
                addView(kit.text(title, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 2))
                if (subtitle != null) addView(kit.text(subtitle, TextStyle.CAPTION, Royal.dim, maxLines = 2))
                addView(kit.text(label(current), TextStyle.CAPTION, if (current?.isSet == true) Royal.turquoiseLight else Royal.muted, maxLines = 1))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(kit.iconButton(RoyalIcon.PLAY, "پخش $title", if (current?.isSet == true) ButtonKind.CHIP_GOLD else ButtonKind.CHIP, 40) {
                if (current?.isSet != true) kit.toast("صدایی انتخاب نشده است") else {
                    val audio = host.audio()
                    if (audio.isPlaying) audio.stopCue() else { audio.volume = mafiaSettings.effectVolume / 100f; audio.play(current) }
                }
            })
            addView(kit.hgap(6))
            addView(kit.iconButton(RoyalIcon.EDIT, "تغییر $title", ButtonKind.CHIP, 40) { change(title, key, music, after) })
        }

    private fun save(key: String, music: Boolean, choice: SoundChoice?, after: (() -> Unit)?) {
        update({ s ->
            if (music) s.copy(nightMusic = choice ?: SoundChoice())
            else if (choice == null) s.copy(sounds = s.sounds - key)
            else s.copy(sounds = s.sounds + (key to choice))
        })
        if (after != null) after() else host.refresh()
    }

    private fun change(title: String, key: String, music: Boolean, after: (() -> Unit)?) {
        val actions = ArrayList<DialogAction>()
        actions += DialogAction("صداهای آماده", ButtonKind.PRIMARY) {
            lateinit var d: Dialog
            val list = kit.vertical().apply {
                MafiaBuiltinSounds.all.forEach { (name, t) ->
                    addView(kit.horizontal().apply {
                        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                        setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(8))
                        layoutParams = kit.spaced(5)
                        addView(kit.iconButton(RoyalIcon.PLAY, "پیش‌نمایش $t", ButtonKind.CHIP, 36) { host.audio().play(SoundChoice(SoundSource.BUILTIN, name)) })
                        addView(kit.hgap(8))
                        addView(kit.weight(kit.text(t, TextStyle.BODY_BOLD)))
                        addView(kit.chip("انتخاب", false, ButtonKind.CHIP_GOLD) {
                            host.audio().stopCue(); d.dismiss(); save(key, music, SoundChoice(SoundSource.BUILTIN, name, "", t), after)
                        }.apply { setPadding(kit.dp(12), kit.dp(4), kit.dp(12), kit.dp(6)) })
                    })
                }
            }
            d = kit.dialog("صداهای آماده", null, list, listOf(DialogAction("بستن") { host.audio().stopCue() }))
            d.show()
        }
        actions += DialogAction("انتخاب فایل از گوشی") {
            Gate.open(this@MafiaSettingsScreen, "mafia_audio") { host.pickFile(arrayOf("audio/*")) { uri ->
                if (uri == null) return@pickFile
                val file = host.audio().importFile(uri, if (music) "music" else key)
                if (file == null) kit.toast("فایل خوانده نشد") else save(key, music, SoundChoice(SoundSource.FILE, "", file.absolutePath, "فایل: " + (uri.lastPathSegment?.substringAfterLast('/') ?: file.name)), after)
            } }
        }
        if (!music) actions += DialogAction("ضبط صدای خودم") { Gate.open(this@MafiaSettingsScreen, "mafia_audio") { record(title, key, after) } }
        actions += DialogAction("بدون صدا") { save(key, music, SoundChoice(), after) }
        if (!music && MafiaCues.defaults().containsKey(key)) actions += DialogAction("صدای پیش‌فرض") { save(key, music, null, after) }
        actions += DialogAction("لغو")
        kit.dialog(title, "صدای این مرحله را انتخاب کنید", null, actions).show()
    }

    private fun record(title: String, key: String, after: (() -> Unit)?) {
        host.requestMicrophone { granted ->
            if (!granted) { kit.toast("برای ضبط، اجازهٔ میکروفون لازم است"); return@requestMicrophone }
            val audio = host.audio()
            audio.stopCue()
            if (!audio.startRecording(key)) { kit.toast("میکروفون در دسترس نیست"); return@requestMicrophone }
            val started = System.currentTimeMillis()
            val time = kit.text(clock(0), TextStyle.DISPLAY, Royal.crimsonLight, Gravity.CENTER)
            val tick = object : Runnable {
                override fun run() {
                    if (!audio.isRecording) return
                    time.text = clock(((System.currentTimeMillis() - started) / 1000).toInt())
                    time.postDelayed(this, 500)
                }
            }
            time.post(tick)
            val body = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                addView(seal(MafiaGlyph.BELL, MafiaSide.MAFIA, 70))
                addView(time)
                addView(kit.text("در حال ضبط… جملهٔ «$title» را بگویید", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
            }
            lateinit var dialog: Dialog
            dialog = kit.dialog("ضبط صدا", null, body, listOf(
                DialogAction("پایان ضبط", ButtonKind.PRIMARY) {
                    val file = audio.stopRecording()
                    if (file == null) kit.toast("ضبط انجام نشد") else save(key, false, SoundChoice(SoundSource.FILE, "", file.absolutePath, "صدای ضبط‌شدهٔ راوی"), after)
                },
                DialogAction("لغو") { audio.stopRecording(discard = true) }
            ))
            dialog.setCancelable(false)
            dialog.show()
        }
    }

    // ------------------------------------------------------------------ pictures

    private fun pictures(parent: LinearLayout) {
        parent.addView(kit.text("برای هر نقش می‌توانید تصویر دلخواه خود را بگذارید؛ در غیر این صورت نقش با نشان طراحی‌شدهٔ برنامه نمایش داده می‌شود.", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(8))
        parent.addView(kit.grid(4, MafiaSide.values().map { sd -> kit.chip(sd.title, side == sd && tab == 2 && cardsTab.not(), ButtonKind.CHIP_GOLD) { side = sd; cardsTab = false; host.refresh() } } +
            kit.chip("کارت‌ها", cardsTab, ButtonKind.CHIP_GOLD) { cardsTab = true; host.refresh() }, 6), kit.spaced(8))
        val pics = mafiaSettings.rolePictures
        val cells: List<View> = if (cardsTab) MafiaLastCard.values().map { c -> pictureCell(lastCard(c), "CARD_" + c.name, c.faName, pics) }
            else MafiaRoleRegistry.available().filter { it.side == side }.map { r -> pictureCell(roleCard(r), r.name, r.faName, pics) }
        parent.addView(kit.grid(3, cells, 8))
    }

    private var cardsTab = false

    private fun pictureCell(card: RoleCardView, key: String, name: String, pics: Map<String, String>): View = FrameLayout(host).apply {
        addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (pics.containsKey(key)) addView(kit.badge("تصویر من", Royal.turquoise, true), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT).apply { topMargin = kit.dp(4); leftMargin = kit.dp(4) })
        isClickable = true
        contentDescription = name
        setOnClickListener {
            kit.tap(it)
            val actions = arrayListOf(DialogAction("انتخاب تصویر از گوشی", ButtonKind.PRIMARY) {
                host.pickFile(arrayOf("image/*")) { uri ->
                    if (uri == null) return@pickFile
                    val path = MafiaPictures.import(host, uri, key)
                    if (path == null) kit.toast("تصویر خوانده نشد") else update({ s -> s.copy(rolePictures = s.rolePictures + (key to path)) }, true)
                }
            })
            if (pics.containsKey(key)) actions += DialogAction("حذف تصویر و نمایش نشان", ButtonKind.DANGER) {
                pics[key]?.let { p -> MafiaPictures.forget(p); java.io.File(p).delete() }
                update({ s -> s.copy(rolePictures = s.rolePictures - key) }, true)
            }
            actions += DialogAction("لغو")
            kit.dialog(name.replace("\n", " "), "تصویر این کارت", null, actions).show()
        }
    }
}

/** Times, rules and device options of the narrator (also shown in the app settings under «راوی مافیا»). */
fun Screen.mafiaGameSettings(parent: LinearLayout) {
    fun update(change: (MafiaSettings) -> MafiaSettings) = host.mafia.saveSettings(change(mafiaSettings))
    val s = mafiaSettings
    val sec = { v: Int -> kit.n(v) + " ثانیه" }
    parent.addView(kit.section("زمان‌ها", RoyalIcon.TIMER))
    parent.addView(kit.stepperRow("زمان صحبت بازیکنان", "زمان صحبت را برای بازیکنان تعین میکند", s.speakTime, 15, 120, 5, sec) { v -> update({ it.copy(speakTime = v) }) })
    parent.addView(kit.stepperRow("زمان چالش", "زمان چالش را برای بازیکنان تعین میکند", s.challengeTime, 15, 120, 5, sec) { v -> update({ it.copy(challengeTime = v) }) })
    parent.addView(kit.stepperRow("زمان دفاعیه", "زمان دفاع را برای بازیکنان تعین میکند", s.defenceTime, 15, 120, 5, sec) { v -> update({ it.copy(defenceTime = v) }) })
    parent.addView(kit.section("قوانین", RoyalIcon.RULES))
    parent.addView(kit.stepperRow("تعداد استعلام", "تعداد استعلام گروهی را برای بازی تعیین میکند", s.queryCount, 0, 5, 1) { v -> update({ it.copy(queryCount = v) }) })
    parent.addView(kit.choiceRow("حد نصاب آرا ورود به دفاعیه", null, listOf(1 to "50 درصد گرد شده رو به بالا", 0 to "50 درصد گرد شده رو به پایین"), if (s.voteEdgeUp) 1 else 0) { v ->
        update({ it.copy(voteEdgeUp = v == 1) })
    })
    parent.addView(kit.switchRow("خودزنی", "مافیا می‌تواند به اعضای خودش هم شلیک کند", s.selfKill) { v -> update({ it.copy(selfKill = v) }) })
    parent.addView(kit.switchRow("تفنگ کاپو", "روز اول به جای رأی‌گیری، معتمد شهر با تفنگ کاپو شلیک می‌کند", s.capoGun) { v -> update({ it.copy(capoGun = v) }) })
    parent.addView(kit.section("دستگاه", RoyalIcon.SETTINGS))
    parent.addView(kit.switchRow("ممانعت از به خواب رفتن دستگاه", null, s.keepScreenOn) { v -> update({ it.copy(keepScreenOn = v) }) })
    parent.addView(kit.switchRow("ویبره", null, s.vibrate) { v -> update({ it.copy(vibrate = v) }) })
}
