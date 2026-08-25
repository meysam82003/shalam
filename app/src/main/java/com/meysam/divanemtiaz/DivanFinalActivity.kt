package com.meysam.divanemtiaz

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Final UI/UX for Divan Emtiaz.
 *
 * This Activity is intentionally independent from the old graphical screens.
 * It keeps the Room/repository data layer, but rebuilds navigation and scoring
 * around short referee workflows and validated rule engines.
 */
class DivanFinalActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repo: V3Repository
    private lateinit var settings: V3Settings
    private var backAction: (() -> Unit)? = null
    private var pendingExport: String? = null
    private var saveLabel: TextView? = null

    companion object {
        private const val EXPORT_REQUEST = 8101
        private const val IMPORT_REQUEST = 8102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = V3Repository(this)
        settings = V3SettingsStore.load(this)
        window.statusBarColor = DivanTheme.bg
        window.navigationBarColor = DivanTheme.bg
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        applyWindowSettings()
        home()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        backAction?.invoke() ?: super.onBackPressed()
    }

    private fun applyWindowSettings() {
        if (settings.keepScreenAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // region Home

    private fun home() {
        backAction = null
        scope.launch {
            val unfinished = repo.history().firstOrNull { !it.finished }
            val body = page()
            body.addView(hero())
            if (unfinished != null) {
                body.addView(section("ادامه سریع"))
                body.addView(actionCard(
                    unfinished.game.title,
                    "${unfinished.game.title} در جریان",
                    sessionSummary(unfinished),
                    DivanTheme.warning,
                    unfinished.game.ordinal
                ) { live(unfinished) })
            }

            body.addView(section("بازی جدید"))
            body.addView(actionCard("ش", "شلم", "دو گروه • تعهد، یاسا، شلم و سرشلم", DivanTheme.emerald, 0) { setupTeams(V3GameType.SHALAM) })
            body.addView(actionCard("م", "منفی", "حکم ثابت • نتیجهٔ دست فقط از حالت‌های ممکن", DivanTheme.crimson, 2) { setupTeams(V3GameType.MENFI) })
            body.addView(actionCard("۱۰۰۰", "هزارتایی", "انفرادی • ۵ تا ۶۰ بازیکن • ثبت امتیاز دوربه‌دور", DivanTheme.blue, 4) { setupHezartaii() })

            body.addView(section("مدیریت"))
            val grid = GridLayout(this@DivanFinalActivity).apply { columnCount = 2 }
            addMenu(grid, "تاریخچه", "ادامه، ویرایش و حذف", 5) { history() }
            addMenu(grid, "گروه‌ها", "بانک گروه و آمار", 6) { teams() }
            addMenu(grid, "لیگ", "Round-robin و تجمعی", 7) { league() }
            addMenu(grid, "تنظیمات", "قوانین و پشتیبان", 8) { settingsScreen() }
            body.addView(grid)
            body.addView(space(26))
            body.addView(text("نسخه نهایی V4 • آفلاین • ذخیره خودکار", 11f, DivanTheme.muted, false, Gravity.CENTER))
            render(body)
        }
    }

    private fun hero(): View = panel(DivanTheme.surfaceHigh, DivanTheme.gold, 26).apply {
        setPadding(dp(16), dp(22), dp(16), dp(18))
        addView(LinearLayout(this@DivanFinalActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(SealAvatarView(this@DivanFinalActivity).apply { label = "دیوان"; variant = 0 }, LinearLayout.LayoutParams(dp(92), dp(92)))
            addView(spaceH(14))
            addView(LinearLayout(this@DivanFinalActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("دیوان امتیاز", 30f, DivanTheme.ivory, true))
                addView(space(4))
                addView(text("داور شیک و دقیق بازی‌های ایرانی", 13f, DivanTheme.goldSoft))
                addView(space(5))
                addView(text("شلم • منفی • هزارتایی", 12f, DivanTheme.muted))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(space(12))
        addView(DivanDivider(this@DivanFinalActivity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
        addView(text("ظاهر سلطنتی، مسیر داوری کوتاه، قانون‌سنجی قبل از ثبت", 11f, DivanTheme.muted, false, Gravity.CENTER))
    }

    private fun addMenu(grid: GridLayout, title: String, subtitle: String, avatar: Int, action: () -> Unit) {
        grid.addView(panel().apply {
            setPadding(dp(10), dp(12), dp(10), dp(10))
            gravity = Gravity.CENTER
            addView(SealAvatarView(this@DivanFinalActivity).apply { label = title; variant = avatar }, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(space(5))
            addView(text(title, 14f, DivanTheme.ivory, true, Gravity.CENTER))
            addView(text(subtitle, 10f, DivanTheme.muted, false, Gravity.CENTER))
            setOnClickListener { tap(it); action() }
        }, GridLayout.LayoutParams().apply {
            width = 0
            height = dp(138)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        })
    }

    // endregion

    // region Setup team games

    private fun setupTeams(game: V3GameType) {
        backAction = { home() }
        var avatarA = 0
        var avatarB = 1
        var trump = "پیک"
        val body = page()
        body.addView(topbar("آماده‌سازی ${game.title}") { home() })
        body.addView(lead(
            if (game == V3GameType.MENFI) "دو گروه، حکم ثابت" else "دو گروه، چهار بازیکن",
            if (game == V3GameType.MENFI) "حکم برای کل مسابقه ثابت می‌ماند. نتیجهٔ هر دست بعد از اعلام‌ها انتخاب می‌شود." else "نام گروه‌ها و یارها را ثبت کن؛ امتیازدهی کاملاً آفلاین است."
        ))

        val nameA = field("نام گروه اول", "گروه اول")
        val a1 = field("یار اول", "")
        val a2 = field("یار دوم", "")
        val avatarViewA = SealAvatarView(this).apply { label = "گروه اول"; variant = avatarA }
        body.addView(teamEditor("گروه اول", avatarViewA, nameA, a1, a2,
            avatarNext = { avatarA = (avatarA + 1) % 16; avatarViewA.variant = avatarA },
            bank = { chooseTeam { t -> nameA.setText(t.name); a1.setText(t.member1); a2.setText(t.member2); avatarA = t.avatar; avatarViewA.label = t.name; avatarViewA.variant = avatarA } }
        ))

        val nameB = field("نام گروه دوم", "گروه دوم")
        val b1 = field("یار اول", "")
        val b2 = field("یار دوم", "")
        val avatarViewB = SealAvatarView(this).apply { label = "گروه دوم"; variant = avatarB }
        body.addView(teamEditor("گروه دوم", avatarViewB, nameB, b1, b2,
            avatarNext = { avatarB = (avatarB + 1) % 16; avatarViewB.variant = avatarB },
            bank = { chooseTeam { t -> nameB.setText(t.name); b1.setText(t.member1); b2.setText(t.member2); avatarB = t.avatar; avatarViewB.label = t.name; avatarViewB.variant = avatarB } }
        ))

        if (game == V3GameType.MENFI) {
            val trumpText = text("حکم: $trump", 15f, DivanTheme.gold, true, Gravity.CENTER)
            body.addView(section("حکم ثابت مسابقه"))
            body.addView(trumpText)
            body.addView(space(7))
            body.addView(outline("تغییر حکم") {
                choose("خال حکم", arrayOf("پیک", "دل", "خشت", "گشنیز")) { selected ->
                    trump = selected
                    trumpText.text = "حکم: $trump"
                }
            })
        }

        body.addView(space(14))
        body.addView(outline("ذخیره هر دو گروه در بانک") {
            scope.launch {
                val cleanA = nameA.clean("گروه اول")
                val cleanB = nameB.clean("گروه دوم")
                repo.saveTeam(V3TeamEntity(name = cleanA, member1 = a1.clean(""), member2 = a2.clean(""), avatar = avatarA))
                repo.saveTeam(V3TeamEntity(name = cleanB, member1 = b1.clean(""), member2 = b2.clean(""), avatar = avatarB))
                toast("گروه‌ها ذخیره شدند")
            }
        })
        body.addView(space(8))
        body.addView(primary("شروع ${game.title}") {
            val nA = nameA.clean("گروه اول")
            val nB = nameB.clean("گروه دوم")
            if (nA == nB) { toast("نام دو گروه باید متفاوت باشد"); return@primary }
            val session = V3Session(
                game = game,
                teamA = V3Team(nA, a1.clean(""), a2.clean(""), avatarA),
                teamB = V3Team(nB, b1.clean(""), b2.clean(""), avatarB),
                menfiTrump = trump,
                menfiHandsTarget = settings.menfiHands.coerceAtLeast(1),
                settingsSnapshot = settings.toJson().toString()
            )
            save(session) { live(session) }
        })
        render(body)
    }

    private fun teamEditor(
        title: String,
        avatar: SealAvatarView,
        name: EditText,
        p1: EditText,
        p2: EditText,
        avatarNext: () -> Unit,
        bank: () -> Unit
    ): View = panel().apply {
        setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(text(title, 14f, DivanTheme.gold, true))
        addView(space(8))
        addView(LinearLayout(this@DivanFinalActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            avatar.setOnClickListener { avatarNext() }
            addView(avatar, LinearLayout.LayoutParams(dp(76), dp(76)))
            addView(spaceH(9))
            addView(name, LinearLayout.LayoutParams(0, dp(52), 1f))
        })
        addView(space(7))
        addView(two(p1, p2))
        addView(space(7))
        addView(outline("انتخاب از بانک گروه‌ها") { bank() })
    }.also { it.layoutParams = spaced() }

    private fun chooseTeam(action: (V3TeamEntity) -> Unit) {
        scope.launch {
            val list = repo.teams()
            if (list.isEmpty()) { toast("هنوز گروهی ذخیره نشده"); return@launch }
            AlertDialog.Builder(this@DivanFinalActivity)
                .setTitle("بانک گروه‌ها")
                .setItems(list.map { it.name }.toTypedArray()) { _, index -> action(list[index]) }
                .show()
        }
    }

    // endregion

    // region Hezartaii setup

    private data class PlayerDraft(val input: EditText, var avatar: Int)

    private fun setupHezartaii() {
        backAction = { home() }
        val body = page()
        body.addView(topbar("آماده‌سازی هزارتایی") { home() })
        body.addView(lead("رقابت انفرادی ۵ تا ۶۰ نفر", "اسم‌ها را دستی اضافه کن یا یک لیست چندخطی Paste کن. نام تکراری قبل از شروع رد می‌شود."))

        val countText = text("", 12f, DivanTheme.gold, true, Gravity.CENTER)
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val drafts = mutableListOf<PlayerDraft>()

        fun refreshCount() {
            countText.text = "${fa(drafts.size)} / ${fa(HezartaiiRulesV4.MAX_PLAYERS)} بازیکن"
        }

        fun rebuild() {
            holder.removeAllViews()
            drafts.forEachIndexed { index, draft ->
                holder.addView(compactPlayerRow(index, draft) {
                    if (drafts.size <= HezartaiiRulesV4.MIN_PLAYERS) { toast("حداقل ۵ بازیکن لازم است") }
                    else { drafts.removeAt(index); rebuild(); refreshCount() }
                }, spaced(5))
            }
            refreshCount()
        }

        fun addPlayer(name: String = "") {
            if (drafts.size >= HezartaiiRulesV4.MAX_PLAYERS) { toast("حداکثر ۶۰ بازیکن"); return }
            drafts += PlayerDraft(field("نام بازیکن ${fa(drafts.size + 1)}", name), drafts.size % 16)
            rebuild()
        }

        repeat(HezartaiiRulesV4.MIN_PLAYERS) { addPlayer() }
        body.addView(countText)
        body.addView(space(8))
        body.addView(holder)
        body.addView(two(
            outline("+ یک بازیکن") { addPlayer() },
            outline("+ پنج بازیکن") { repeat(5) { if (drafts.size < HezartaiiRulesV4.MAX_PLAYERS) addPlayer() } }
        ))
        body.addView(space(7))
        body.addView(outline("Paste گروهی اسم‌ها") {
            bulkNamesDialog { names ->
                names.forEach { if (drafts.size < HezartaiiRulesV4.MAX_PLAYERS) addPlayer(it) }
            }
        })
        body.addView(space(10))
        body.addView(primary("شروع هزارتایی") {
            val players = drafts.mapIndexed { index, draft ->
                V3Player(draft.input.text.toString().trim(), draft.avatar.takeIf { it >= 0 } ?: index % 16)
            }
            val validation = runCatching { HezartaiiRulesV4.validatePlayers(players) }
            if (validation.isFailure) {
                val names = players.map { it.name }
                val message = when {
                    players.size !in 5..60 -> "تعداد بازیکن باید بین ۵ تا ۶۰ باشد"
                    names.any { it.isBlank() } -> "نام همه بازیکن‌ها را وارد کن"
                    names.distinct().size != names.size -> "اسم تکراری وجود دارد؛ هر بازیکن باید نام جدا داشته باشد"
                    else -> "لیست بازیکن‌ها معتبر نیست"
                }
                toast(message)
                return@primary
            }
            val session = V3Session(
                game = V3GameType.HEZARTAII,
                players = players.toMutableList(),
                settingsSnapshot = settings.toJson().toString()
            )
            save(session) { live(session) }
        })
        render(body)
    }

    private fun compactPlayerRow(index: Int, draft: PlayerDraft, remove: () -> Unit): View = panel(horizontal = true).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(5), dp(8), dp(5))
        val avatar = SealAvatarView(this@DivanFinalActivity).apply { label = "${index + 1}"; variant = draft.avatar }
        avatar.setOnClickListener {
            draft.avatar = (draft.avatar + 1) % 16
            avatar.variant = draft.avatar
        }
        addView(text(fa(index + 1), 11f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(28), dp(48)))
        addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(spaceH(6))
        addView(draft.input, LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(textButton("×", DivanTheme.danger) { remove() }, LinearLayout.LayoutParams(dp(44), dp(48)))
    }

    private fun bulkNamesDialog(onAdd: (List<String>) -> Unit) {
        val input = field("هر خط یک اسم", "").apply {
            setSingleLine(false)
            minLines = 7
            maxLines = 12
            gravity = Gravity.TOP or Gravity.RIGHT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle("افزودن گروهی اسامی")
            .setMessage("از تلگرام، نوت یا اکسل Paste کن؛ هر خط یا ویرگول یک بازیکن.")
            .setView(input)
            .setPositiveButton("افزودن") { _, _ ->
                val names = input.text.toString().split('\n', ',', '،', ';').map { it.trim() }.filter { it.isNotBlank() }
                if (names.isEmpty()) toast("اسمی پیدا نشد") else onAdd(names)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // endregion

    // region Live routing

    private fun live(session: V3Session) {
        when (session.game) {
            V3GameType.SHALAM -> shalam(session)
            V3GameType.MENFI -> menfi(session)
            V3GameType.HEZARTAII -> hezartaii(session)
        }
    }

    private fun liveHeader(session: V3Session): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(topbar("${session.game.title} • داوری زنده") { leaveLive(session) })
        saveLabel = text("● ذخیره خودکار فعال", 10f, DivanTheme.turquoise, true, Gravity.CENTER)
        addView(saveLabel)
        addView(space(7))
    }

    private fun leaveLive(session: V3Session) {
        confirm("خروج از بازی", "بازی ذخیره می‌شود و از ادامه سریع یا تاریخچه قابل ادامه است.") { save(session) { home() } }
    }

    // endregion

    // region Shalam

    private fun shalam(session: V3Session) {
        backAction = { leaveLive(session) }
        val body = page()
        body.addView(liveHeader(session))
        body.addView(teamScoreboard(session, false))
        body.addView(gapCard(session))
        body.addView(section("دست ${fa(session.rounds.size + 1)}"))
        body.addView(info("روش ثبت", "اول تیم حاکم، بعد تعهد؛ امتیاز واقعی حریف را وارد کن تا سهم حاکم از ۱۶۵ خودکار حساب شود."))
        var contractor = 0
        val a = segment(session.teamA!!.name, true)
        val b = segment(session.teamB!!.name, false)
        fun select(index: Int) {
            contractor = index
            styleSegment(a, index == 0)
            styleSegment(b, index == 1)
        }
        a.setOnClickListener { select(0) }
        b.setOnClickListener { select(1) }
        body.addView(two(a, b))
        body.addView(space(9))
        val bids = GridLayout(this).apply { columnCount = 4 }
        ShalamEngineV3.readyBids(settings.shalamMinBid).forEach { bid ->
            bids.addView(small(if (bid == 165) "شلم" else fa(bid)) { shalamDialog(session, contractor, bid, null) }, GridLayout.LayoutParams().apply {
                width = 0; height = dp(50); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        body.addView(bids)
        body.addView(liveTools(session))
        body.addView(roundList(session))
        body.addView(space(8))
        body.addView(primary("پایان و نتیجه") { finish(session) })
        render(body)
    }

    private fun shalamDialog(session: V3Session, contractor: Int, bid: Int, editIndex: Int?) {
        val old = editIndex?.let { session.rounds[it] }
        val oldOpp = old?.meta?.get("opponentActual") ?: "0"
        val opponent = numberField("امتیاز واقعی حریف ۰ تا ۱۶۵", oldOpp, false)
        val note = field("یادداشت داور (اختیاری)", old?.meta?.get("judgeNote") ?: "")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(2)); addView(opponent); addView(space(7)); addView(note)
        }
        AlertDialog.Builder(this)
            .setTitle(if (editIndex == null) "ثبت تعهد ${fa(bid)}" else "ویرایش دست ${fa(editIndex + 1)}")
            .setView(box)
            .setPositiveButton(if (editIndex == null) "ثبت" else "ذخیره") { _, _ ->
                val opp = DivanText.latin(opponent.text.toString()).toIntOrNull()
                if (opp == null || opp !in 0..165) { toast("امتیاز حریف باید بین ۰ تا ۱۶۵ باشد"); return@setPositiveButton }
                val result = ShalamEngineV3.score(bid, opp, settings, declaredShalam = bid == 165)
                val scores = if (contractor == 0) mutableListOf(result.contractScore, result.opponentScore) else mutableListOf(result.opponentScore, result.contractScore)
                val meta = mutableMapOf(
                    "contract" to bid.toString(), "contractTeam" to contractor.toString(), "opponentActual" to opp.toString(),
                    "judgeNote" to note.text.toString().trim()
                )
                val title = buildString {
                    append(if (bid == 165) "شلم" else "تعهد $bid")
                    if (result.yasaApplied) append(" • یاسا")
                    val n = note.text.toString().trim(); if (n.isNotBlank()) append(" • $n")
                }
                val updated = V3Round(scores, title, meta, old?.createdAt ?: System.currentTimeMillis(), if (old != null) System.currentTimeMillis() else null)
                if (editIndex == null) {
                    session.rounds += updated
                    save(session) { shalam(session) }
                } else {
                    session.rounds[editIndex] = updated
                    scope.launch { repo.addAudit(session.id, editIndex, "EDIT", old, updated); repo.save(session); shalam(session) }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // endregion

    // region Negative

    private fun menfi(session: V3Session) {
        backAction = { leaveLive(session) }
        val body = page()
        body.addView(liveHeader(session))
        val hidden = settings.menfiHideMode == 0 && !session.revealed
        body.addView(teamScoreboard(session, hidden))
        body.addView(info("حکم ${session.menfiTrump}", "دست ${fa(session.rounds.size + 1)} از ${fa(session.menfiHandsTarget)}"))
        if (!hidden) body.addView(gapCard(session))
        body.addView(section("اعلام‌های دست"))
        body.addView(info("قانون‌سنجی خودکار", "بعد از انتخاب دو اعلام، فقط نتیجه‌هایی که با ۱۳ دست ممکن‌اند فعال می‌شوند. امتیاز خام دستی وارد نمی‌شود."))

        var bidA = settings.menfiMinBid
        var bidB = settings.menfiMinBid
        val a = segment("${session.teamA!!.name}: ${fa(bidA)}", true)
        val b = segment("${session.teamB!!.name}: ${fa(bidB)}", false)
        a.setOnClickListener { chooseBid { bidA = it; a.text = "${session.teamA!!.name}: ${fa(it)}" } }
        b.setOnClickListener { chooseBid { bidB = it; b.text = "${session.teamB!!.name}: ${fa(it)}" } }
        body.addView(two(a, b))
        body.addView(space(10))
        body.addView(primary("انتخاب نتیجه و ثبت دست") { menfiOutcomeDialog(session, bidA, bidB, null) })
        body.addView(space(7))
        body.addView(outline(if (session.revealed) "پنهان کردن مجموع" else "نمایش مجموع برای داور") {
            session.revealed = !session.revealed
            save(session) { menfi(session) }
        })
        body.addView(liveTools(session))
        body.addView(roundList(session))
        body.addView(space(8))
        body.addView(primary("بررسی پایان مسابقه") { menfiFinishCheck(session) })
        render(body)
    }

    private fun chooseBid(action: (Int) -> Unit) {
        val values = (settings.menfiMinBid.coerceAtLeast(1)..13).toList()
        AlertDialog.Builder(this).setTitle("عدد اعلام").setItems(values.map { fa(it) }.toTypedArray()) { _, i -> action(values[i]) }.show()
    }

    private fun menfiOutcomeDialog(session: V3Session, initialBidA: Int, initialBidB: Int, editIndex: Int?) {
        val old = editIndex?.let { session.rounds[it] }
        var bidA = old?.meta?.get("bidA")?.toIntOrNull() ?: initialBidA
        var bidB = old?.meta?.get("bidB")?.toIntOrNull() ?: initialBidB
        var selected = old?.meta?.get("outcome")?.let { MenfiOutcomeV4.fromKey(it) }

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(4)) }
        val bidsLabel = text("", 13f, DivanTheme.gold, true, Gravity.CENTER)
        val ruleLabel = text("", 11f, DivanTheme.muted, false, Gravity.CENTER)
        val preview = text("یک نتیجه را انتخاب کن", 13f, DivanTheme.ivory, true, Gravity.CENTER)
        val outcomes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val note = field("یادداشت داور (اختیاری)", old?.meta?.get("judgeNote") ?: "").apply { setSingleLine(false); minLines = 2; maxLines = 4 }

        fun outcomeTitle(outcome: MenfiOutcomeV4): String = when (outcome) {
            MenfiOutcomeV4.BOTH_MADE -> "هر دو گروه گرفتند"
            MenfiOutcomeV4.A_FAILED_B_MADE -> "${session.teamA!!.name} منفی • ${session.teamB!!.name} مثبت"
            MenfiOutcomeV4.A_MADE_B_FAILED -> "${session.teamA!!.name} مثبت • ${session.teamB!!.name} منفی"
            MenfiOutcomeV4.BOTH_FAILED -> "هر دو گروه منفی شدند"
        }

        fun refresh() {
            bidsLabel.text = "اعلام‌ها: ${session.teamA!!.name} ${fa(bidA)}  •  ${session.teamB!!.name} ${fa(bidB)}"
            val sum = bidA + bidB
            ruleLabel.text = when {
                sum <= 13 -> "جمع ${fa(sum)}: هر دو می‌توانند موفق شوند؛ هر دو منفی ممکن نیست."
                sum == 14 -> "جمع ۱۴: دقیقاً یکی از دو گروه می‌تواند موفق شود."
                else -> "جمع ${fa(sum)}: هر دو موفق نمی‌شوند؛ هر دو منفی از جمع ۱۵ به بالا ممکن است."
            }
            outcomes.removeAllViews()
            val options = MenfiRulesV4.outcomeOptions(bidA, bidB, settings)
            if (selected != null && options.first { it.outcome == selected }.enabled.not()) selected = null
            options.forEach { option ->
                val title = outcomeTitle(option.outcome)
                val sub = if (option.enabled) "قابل انتخاب" else option.reason
                val button = outcomeCard(title, sub, option.enabled, selected == option.outcome) {
                    selected = option.outcome
                    val score = MenfiRulesV4.score(bidA, bidB, option.outcome, settings)
                    preview.text = "پیش‌نمایش امتیاز: ${session.teamA!!.name} ${signed(score.teamAScore)}  |  ${session.teamB!!.name} ${signed(score.teamBScore)}"
                    refresh()
                }
                outcomes.addView(button, spaced(6))
            }
            selected?.let {
                val score = MenfiRulesV4.score(bidA, bidB, it, settings)
                preview.text = "پیش‌نمایش امتیاز: ${session.teamA!!.name} ${signed(score.teamAScore)}  |  ${session.teamB!!.name} ${signed(score.teamBScore)}"
            } ?: run { preview.text = "یک نتیجهٔ معتبر را انتخاب کن" }
        }

        val bidAButton = outline("اعلام ${session.teamA!!.name}") { chooseBid { bidA = it; refresh() } }
        val bidBButton = outline("اعلام ${session.teamB!!.name}") { chooseBid { bidB = it; refresh() } }
        box.addView(two(bidAButton, bidBButton))
        box.addView(space(7)); box.addView(bidsLabel); box.addView(space(3)); box.addView(ruleLabel)
        box.addView(space(9)); box.addView(outcomes); box.addView(space(7)); box.addView(preview); box.addView(space(8)); box.addView(note)
        refresh()

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (editIndex == null) "نتیجه دست" else "ویرایش دست ${fa(editIndex + 1)}")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(if (editIndex == null) "ثبت نهایی" else "ذخیره تغییر") { _, _ -> }
            .setNegativeButton("انصراف", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val outcome = selected
                if (outcome == null) { toast("اول نتیجهٔ دست را انتخاب کن"); return@setOnClickListener }
                if (!MenfiRulesV4.isFeasible(bidA, bidB, outcome, settings)) { toast("این نتیجه با اعلام‌ها ممکن نیست"); return@setOnClickListener }
                val result = MenfiRulesV4.score(bidA, bidB, outcome, settings)
                val judgeNote = note.text.toString().trim()
                val human = outcomeTitle(outcome)
                val meta = mutableMapOf(
                    "rulesVersion" to "4",
                    "bidA" to bidA.toString(),
                    "bidB" to bidB.toString(),
                    "outcome" to outcome.key,
                    "madeA" to result.teamAMade.toString(),
                    "madeB" to result.teamBMade.toString(),
                    "judgeNote" to judgeNote
                )
                val round = V3Round(
                    scores = mutableListOf(result.teamAScore, result.teamBScore),
                    note = buildString { append("اعلام $bidA/$bidB • $human"); if (judgeNote.isNotBlank()) append(" • $judgeNote") },
                    meta = meta,
                    createdAt = old?.createdAt ?: System.currentTimeMillis(),
                    editedAt = if (old != null) System.currentTimeMillis() else null
                )
                if (editIndex == null) {
                    session.rounds += round
                    if (settings.menfiHideMode == 2) session.revealed = true
                    save(session) { menfi(session) }
                } else {
                    session.rounds[editIndex] = round
                    scope.launch {
                        repo.addAudit(session.id, editIndex, "EDIT", old, round)
                        repo.save(session)
                        menfi(session)
                    }
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun outcomeCard(title: String, subtitle: String, enabled: Boolean, selected: Boolean, action: () -> Unit): View =
        panel(
            fill = if (selected) DivanTheme.surfaceSoft else if (enabled) DivanTheme.surface else DivanTheme.disabled,
            stroke = if (selected) DivanTheme.gold else if (enabled) DivanTheme.line else DivanTheme.disabled,
            radius = 15
        ).apply {
            alpha = if (enabled) 1f else .55f
            isEnabled = enabled
            setPadding(dp(11), dp(9), dp(11), dp(9))
            addView(text(if (selected) "✓ $title" else title, 13f, if (enabled) DivanTheme.ivory else DivanTheme.muted, true))
            addView(text(subtitle, 10f, if (enabled) DivanTheme.goldSoft else DivanTheme.muted))
            if (enabled) setOnClickListener { tap(it); action() }
        }

    private fun menfiFinishCheck(session: V3Session) {
        if (session.rounds.size < session.menfiHandsTarget) {
            toast("${fa(session.menfiHandsTarget - session.rounds.size)} دست باقی مانده")
            return
        }
        session.revealed = true
        val totals = teamTotals(session)
        if (totals.first != totals.second) { finish(session); return }

        fun addExtra(count: Int) {
            session.menfiHandsTarget += count
            save(session) { menfi(session) }
        }
        when (settings.menfiTieExtraMode) {
            2 -> addExtra(2)
            3 -> addExtra(3)
            else -> AlertDialog.Builder(this)
                .setTitle("مسابقه مساوی شد")
                .setMessage("چند دست اضافه شود؟")
                .setPositiveButton("۲ دست") { _, _ -> addExtra(2) }
                .setNegativeButton("۳ دست") { _, _ -> addExtra(3) }
                .show()
        }
    }

    // endregion

    // region Hezartaii live

    private fun hezartaii(session: V3Session) {
        backAction = { leaveLive(session) }
        val body = page()
        body.addView(liveHeader(session))
        val hide = settings.hezartaiiHideUntilEnd && !session.finished
        body.addView(ranking(session, hide))
        body.addView(section("دور ${fa(session.rounds.size + 1)} از ${fa(settings.hezartaiiRounds)}"))
        body.addView(info("${fa(session.players.size)} بازیکن", "صفر به‌طور خودکار ${signed(settings.hezartaiiZeroPenalty)} ثبت می‌شود. بالاترین مجموع برنده است."))

        val inputs = mutableListOf<EditText>()
        session.players.forEachIndexed { index, player ->
            val input = numberField("امتیاز", "0", true)
            inputs += input
            body.addView(scoreEntryRow(index, player, input), spaced(4))
        }
        body.addView(space(8))
        body.addView(primary("ثبت دور برای همه ${fa(session.players.size)} نفر") {
            val raw = inputs.map { DivanText.latin(it.text.toString()).toIntOrNull() }
            if (raw.any { it == null }) { toast("امتیاز همه بازیکن‌ها را وارد کن"); return@primary }
            val scores = raw.map { HezartaiiRulesV4.normalize(it!!, settings) }.toMutableList()
            session.rounds += V3Round(scores, "دور ${session.rounds.size + 1}", mutableMapOf("rulesVersion" to "4"))
            if (session.rounds.size >= settings.hezartaiiRounds) finish(session) else save(session) { hezartaii(session) }
        })
        body.addView(liveTools(session))
        body.addView(roundList(session))
        body.addView(space(8))
        body.addView(outline("پایان زودتر و نمایش نتیجه") { finish(session) })
        render(body)
    }

    private fun scoreEntryRow(index: Int, player: V3Player, input: EditText): View = panel(horizontal = true).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(7), dp(4), dp(7), dp(4))
        addView(text(fa(index + 1), 10f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(28), dp(46)))
        addView(SealAvatarView(this@DivanFinalActivity).apply { label = player.name; variant = player.avatar }, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(spaceH(6))
        addView(text(player.name, 12f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, dp(46), 1f))
        addView(input, LinearLayout.LayoutParams(dp(104), dp(46)))
    }

    // endregion

    // region Scores / edit / results

    private fun teamTotals(session: V3Session): Pair<Int, Int> =
        session.rounds.sumOf { it.scores.getOrElse(0) { 0 } } to session.rounds.sumOf { it.scores.getOrElse(1) { 0 } }

    private fun teamScoreboard(session: V3Session, hidden: Boolean): View {
        val totals = teamTotals(session)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(scoreTile(session.teamA!!.name, session.teamA!!.avatar, if (hidden) "•••" else signed(totals.first), DivanTheme.emerald), LinearLayout.LayoutParams(0, dp(148), 1f))
            addView(spaceH(7))
            addView(scoreTile(session.teamB!!.name, session.teamB!!.avatar, if (hidden) "•••" else signed(totals.second), DivanTheme.crimson), LinearLayout.LayoutParams(0, dp(148), 1f))
        }
    }

    private fun scoreTile(name: String, avatar: Int, score: String, accent: Int): View = panel(DivanTheme.surfaceHigh, accent, 20).apply {
        gravity = Gravity.CENTER
        setPadding(dp(7), dp(8), dp(7), dp(8))
        addView(SealAvatarView(this@DivanFinalActivity).apply { label = name; variant = avatar }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(text(name, 11f, DivanTheme.ivory, true, Gravity.CENTER))
        addView(text(score, 25f, DivanTheme.gold, true, Gravity.CENTER))
    }

    private fun gapCard(session: V3Session): View {
        val totals = teamTotals(session)
        return info("فاصله امتیاز", ScoreGapV3.teamGap(session.teamA!!.name, totals.first, session.teamB!!.name, totals.second))
    }

    private fun ranking(session: V3Session, hidden: Boolean): View = panel().apply {
        setPadding(dp(10), dp(10), dp(10), dp(10))
        addView(text("رتبه‌بندی", 14f, DivanTheme.gold, true, Gravity.CENTER))
        addView(space(6))
        if (hidden) {
            addView(text("امتیازها تا پایان پنهان‌اند.", 11f, DivanTheme.muted, false, Gravity.CENTER))
        } else {
            HezartaiiRulesV4.ranking(session.players, session.rounds).forEachIndexed { index, pair ->
                addView(LinearLayout(this@DivanFinalActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3))
                    addView(text(fa(index + 1), 11f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(28), dp(40)))
                    addView(SealAvatarView(this@DivanFinalActivity).apply { label = pair.first.name; variant = pair.first.avatar }, LinearLayout.LayoutParams(dp(38), dp(38)))
                    addView(spaceH(6))
                    addView(text(pair.first.name, 12f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, dp(40), 1f))
                    addView(text(signed(pair.second), 12f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(86), dp(40)))
                })
            }
        }
    }

    private fun liveTools(session: V3Session): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(section("ابزار داور"))
        addView(two(
            outline("↶ حذف آخرین ثبت") { undoLast(session) },
            outline("اشتراک وضعیت") { share(session, false) }
        ))
    }

    private fun undoLast(session: V3Session) {
        if (session.rounds.isEmpty()) { toast("رکوردی برای حذف نیست"); return }
        confirm("حذف آخرین ثبت", "آخرین دست/دور حذف شود؟ این تغییر در Audit Log ثبت می‌شود.") {
            val index = session.rounds.lastIndex
            val before = session.rounds.removeAt(index)
            scope.launch {
                repo.addAudit(session.id, index, "UNDO", before, null)
                repo.save(session)
                live(session)
            }
        }
    }

    private fun roundList(session: V3Session): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(section("دست‌ها و دورهای ثبت‌شده"))
        if (session.rounds.isEmpty()) addView(text("هنوز چیزی ثبت نشده.", 11f, DivanTheme.muted, false, Gravity.CENTER))
        session.rounds.forEachIndexed { index, round ->
            val summary = if (session.game == V3GameType.HEZARTAII && round.scores.size > 8) {
                "${fa(round.scores.size)} امتیاز ثبت شده • ${round.note}"
            } else {
                "${round.scores.joinToString(" | ") { signed(it) }}\n${round.note}"
            }
            addView(panel(horizontal = true).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(9), dp(7), dp(9), dp(7))
                addView(text(fa(index + 1), 11f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(30), dp(44)))
                addView(text(summary, 11f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(textButton("ویرایش") { editRound(session, index) })
            }, spaced(5))
        }
    }

    private fun editRound(session: V3Session, index: Int) {
        when (session.game) {
            V3GameType.MENFI -> {
                val old = session.rounds[index]
                val bidA = old.meta["bidA"]?.toIntOrNull() ?: settings.menfiMinBid
                val bidB = old.meta["bidB"]?.toIntOrNull() ?: settings.menfiMinBid
                menfiOutcomeDialog(session, bidA, bidB, index)
            }
            V3GameType.SHALAM -> {
                val old = session.rounds[index]
                val contractor = old.meta["contractTeam"]?.toIntOrNull() ?: 0
                val bid = old.meta["contract"]?.toIntOrNull() ?: settings.shalamMinBid
                shalamDialog(session, contractor, bid, index)
            }
            V3GameType.HEZARTAII -> editHezartaiiRound(session, index)
        }
    }

    private fun editHezartaiiRound(session: V3Session, index: Int) {
        val old = session.rounds[index]
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(5), dp(12), dp(4)) }
        val inputs = session.players.mapIndexed { i, p ->
            numberField(p.name, old.scores.getOrElse(i) { 0 }.toString(), true).also { input ->
                box.addView(two(text(p.name, 11f, DivanTheme.ivory, true), input), spaced(3))
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("ویرایش دور ${fa(index + 1)}")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("ذخیره") { _, _ -> }
            .setNegativeButton("انصراف", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = inputs.map { DivanText.latin(it.text.toString()).toIntOrNull() }
                if (raw.any { it == null }) { toast("همه امتیازها باید عدد باشند"); return@setOnClickListener }
                val updated = old.copy(
                    scores = raw.map { HezartaiiRulesV4.normalize(it!!, settings) }.toMutableList(),
                    note = "دور ${index + 1} • اصلاح",
                    editedAt = System.currentTimeMillis()
                )
                session.rounds[index] = updated
                scope.launch { repo.addAudit(session.id, index, "EDIT", old, updated); repo.save(session); hezartaii(session) }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun finish(session: V3Session) {
        if (session.rounds.isEmpty()) { toast("حداقل یک دست/دور ثبت کن"); return }
        session.finished = true
        session.revealed = true
        save(session) { result(session) }
    }

    private fun result(session: V3Session) {
        backAction = { home() }
        val body = page()
        body.addView(topbar("نتیجه نهایی") { home() })
        if (session.game == V3GameType.HEZARTAII) {
            val rank = HezartaiiRulesV4.ranking(session.players, session.rounds)
            body.addView(resultHero(rank.firstOrNull()?.first?.name ?: "—", "قهرمان هزارتایی", rank.firstOrNull()?.first?.avatar ?: 0))
            body.addView(ranking(session, false))
            body.addView(section("فاصله تا صدر"))
            val first = rank.firstOrNull()?.second ?: 0
            rank.forEachIndexed { i, p ->
                body.addView(text(if (i == 0) "${p.first.name} صدرنشین است." else "${p.first.name}: ${fa(first - p.second)} امتیاز تا صدر", 11f, DivanTheme.ivory, false, Gravity.CENTER))
            }
        } else {
            val totals = teamTotals(session)
            val winner = when {
                totals.first == totals.second -> "مساوی"
                totals.first > totals.second -> session.teamA!!.name
                else -> session.teamB!!.name
            }
            val avatar = when (winner) {
                session.teamA!!.name -> session.teamA!!.avatar
                session.teamB!!.name -> session.teamB!!.avatar
                else -> 0
            }
            body.addView(resultHero(winner, "نتیجه ${session.game.title}", avatar))
            body.addView(teamScoreboard(session, false))
            body.addView(gapCard(session))
        }
        body.addView(roundList(session))
        body.addView(space(8))
        body.addView(primary("اشتراک نتیجه") { share(session, true) })
        body.addView(space(7))
        body.addView(outline("بازگشت به خانه") { home() })
        render(body)
    }

    private fun resultHero(winner: String, subtitle: String, avatar: Int): View = panel(DivanTheme.surfaceHigh, DivanTheme.gold, 24).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(20), dp(14), dp(20))
        addView(SealAvatarView(this@DivanFinalActivity).apply { label = winner; variant = avatar }, LinearLayout.LayoutParams(dp(94), dp(94)))
        addView(space(7)); addView(text(winner, 24f, DivanTheme.ivory, true, Gravity.CENTER)); addView(text(subtitle, 11f, DivanTheme.gold, true, Gravity.CENTER))
    }

    private fun share(session: V3Session, final: Boolean) {
        val value = buildString {
            append("دیوان امتیاز — ${session.game.title}\n")
            if (session.game == V3GameType.HEZARTAII) {
                HezartaiiRulesV4.ranking(session.players, session.rounds).forEachIndexed { i, p -> append("${i + 1}. ${p.first.name}: ${p.second}\n") }
            } else {
                val t = teamTotals(session)
                append("${session.teamA!!.name}: ${t.first}\n${session.teamB!!.name}: ${t.second}\n")
            }
            append(if (final) "نتیجه نهایی" else "وضعیت فعلی")
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, value) }, "اشتراک"))
    }

    // endregion

    // region History / audit

    private fun history(game: V3GameType? = null, state: Boolean? = null) {
        backAction = { home() }
        scope.launch {
            var list = repo.history()
            game?.let { g -> list = list.filter { it.game == g } }
            state?.let { done -> list = list.filter { it.finished == done } }
            val body = page()
            body.addView(topbar("تاریخچه") { home() })
            body.addView(two(
                outline(game?.title ?: "همه بازی‌ها") { historyGameFilter() },
                outline(when (state) { true -> "تمام‌شده"; false -> "در جریان"; null -> "همه وضعیت‌ها" }) { historyStateFilter(game) }
            ))
            body.addView(space(8))
            if (list.isEmpty()) body.addView(info("تاریخچه خالی است", "بازی جدید شروع کن یا فیلترها را تغییر بده."))
            list.forEach { session ->
                body.addView(panel().apply {
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    addView(LinearLayout(this@DivanFinalActivity).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        addView(SealAvatarView(this@DivanFinalActivity).apply { label = session.game.title; variant = session.game.ordinal }, LinearLayout.LayoutParams(dp(54), dp(54)))
                        addView(spaceH(8))
                        addView(LinearLayout(this@DivanFinalActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(text("${session.game.title} • ${if (session.finished) "پایان‌یافته" else "در جریان"}", 13f, DivanTheme.ivory, true))
                            addView(text(sessionSummary(session), 10f, DivanTheme.muted))
                            addView(text(date(session.updatedAt), 9f, DivanTheme.goldSoft))
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    })
                    addView(space(6))
                    addView(two(
                        outline(if (session.finished) "مشاهده نتیجه" else "ادامه") { if (session.finished) result(session) else live(session) },
                        outline("Audit Log") { audit(session) }
                    ))
                    addView(textButton("حذف بازی", DivanTheme.danger) {
                        confirm("حذف بازی", "این بازی برای همیشه حذف شود؟") { scope.launch { repo.deleteSession(session.id); history(game, state) } }
                    })
                }, spaced())
            }
            render(body)
        }
    }

    private fun historyGameFilter() {
        val values = arrayOf("همه", "شلم", "منفی", "هزارتایی")
        AlertDialog.Builder(this).setTitle("نوع بازی").setItems(values) { _, i -> history(if (i == 0) null else V3GameType.values()[i - 1], null) }.show()
    }

    private fun historyStateFilter(game: V3GameType?) {
        AlertDialog.Builder(this).setTitle("وضعیت").setItems(arrayOf("همه", "در جریان", "تمام‌شده")) { _, i -> history(game, when (i) { 1 -> false; 2 -> true; else -> null }) }.show()
    }

    private fun audit(session: V3Session) {
        backAction = { history() }
        scope.launch {
            val logs = repo.audit(session.id)
            val body = page(); body.addView(topbar("گزارش تغییرات") { history() }); body.addView(info(session.game.title, "ویرایش و Undo با زمان ذخیره می‌شوند."))
            if (logs.isEmpty()) body.addView(text("تغییری ثبت نشده.", 11f, DivanTheme.muted, false, Gravity.CENTER))
            logs.forEach { log ->
                body.addView(panel().apply {
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    addView(text("${log.action} • رکورد ${fa(log.roundIndex + 1)}", 12f, DivanTheme.gold, true))
                    addView(text(date(log.timestamp), 9f, DivanTheme.muted))
                }, spaced(5))
            }
            render(body)
        }
    }

    // endregion

    // region Teams

    private fun teams() {
        backAction = { home() }
        scope.launch {
            val bank = repo.teams()
            val games = repo.history().filter { it.finished && it.game != V3GameType.HEZARTAII }
            val body = page(); body.addView(topbar("گروه‌ها") { home() }); body.addView(lead("بانک گروه‌های ثابت", "گروه را یک‌بار بساز؛ در شلم، منفی و لیگ با یک لمس انتخاب کن.")); body.addView(primary("+ گروه جدید") { addTeamDialog() }); body.addView(space(8))
            if (bank.isEmpty()) body.addView(info("بانک گروه خالی است", "اولین گروه را بساز."))
            bank.forEach { team ->
                val related = games.filter { it.teamA?.name == team.name || it.teamB?.name == team.name }
                val wins = related.count { session ->
                    val t = teamTotals(session)
                    (session.teamA?.name == team.name && t.first > t.second) || (session.teamB?.name == team.name && t.second > t.first)
                }
                body.addView(panel(horizontal = true).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(9), dp(7), dp(9), dp(7))
                    addView(SealAvatarView(this@DivanFinalActivity).apply { label = team.name; variant = team.avatar }, LinearLayout.LayoutParams(dp(60), dp(60)))
                    addView(spaceH(8))
                    addView(LinearLayout(this@DivanFinalActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(team.name, 13f, DivanTheme.ivory, true))
                        addView(text("${team.member1} • ${team.member2}", 10f, DivanTheme.muted))
                        addView(text("${fa(wins)} برد از ${fa(related.size)} بازی", 10f, DivanTheme.gold))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(textButton("حذف", DivanTheme.danger) { confirm("حذف گروه", "تاریخچه بازی‌ها باقی می‌ماند.") { scope.launch { repo.deleteTeam(team.id); teams() } } })
                }, spaced())
            }
            render(body)
        }
    }

    private fun addTeamDialog() {
        var avatar = 0
        val name = field("نام گروه", "")
        val p1 = field("یار اول", "")
        val p2 = field("یار دوم", "")
        val avatarView = SealAvatarView(this).apply { label = "گروه"; variant = avatar }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(2))
            avatarView.setOnClickListener { avatar = (avatar + 1) % 16; avatarView.variant = avatar }
            addView(avatarView, LinearLayout.LayoutParams(dp(76), dp(76))); addView(space(6)); addView(name); addView(space(5)); addView(p1); addView(space(5)); addView(p2)
        }
        AlertDialog.Builder(this).setTitle("گروه جدید").setView(box).setPositiveButton("ذخیره") { _, _ ->
            val n = name.text.toString().trim(); if (n.isBlank()) { toast("نام گروه لازم است"); return@setPositiveButton }
            scope.launch { repo.saveTeam(V3TeamEntity(name = n, member1 = p1.clean(""), member2 = p2.clean(""), avatar = avatar)); teams() }
        }.setNegativeButton("انصراف", null).show()
    }

    // endregion

    // region League

    private fun league() {
        backAction = { home() }
        val saved = V3LeagueStore.load(this)
        if (saved == null) leagueStart() else leagueBoard(saved)
    }

    private fun leagueStart() {
        scope.launch {
            val bank = repo.teams()
            val body = page(); body.addView(topbar("لیگ") { home() }); body.addView(lead("لیگ دیوان", "حداقل ۳ گروه؛ همه با همه. حالت مصاف تا ۲ برد یا امتیاز تجمعی."))
            if (bank.size < 3) {
                body.addView(info("حداقل ۳ گروه لازم است", "الان ${fa(bank.size)} گروه در بانک داری.")); body.addView(space(7)); body.addView(primary("ساخت گروه") { teams() })
            } else {
                body.addView(info("${fa(bank.size)} گروه آماده", "برای شروع، نوع لیگ را انتخاب کن."))
                body.addView(space(8))
                body.addView(primary("ساخت لیگ") {
                    AlertDialog.Builder(this@DivanFinalActivity).setTitle("نوع لیگ")
                        .setItems(arrayOf("مصاف دوبه‌دو؛ هر مصاف تا ۲ برد", "تجمعی؛ تا تعداد دور یا امتیاز هدف")) { _, index ->
                            val mode = if (index == 0) V3LeagueMode.BEST_OF_THREE else V3LeagueMode.CUMULATIVE
                            val state = V3LeagueState(mode = mode, teams = bank.map { V3LeagueTeam(it.id, it.name, it.avatar) }.toMutableList())
                            if (mode == V3LeagueMode.BEST_OF_THREE) {
                                state.rebuildMatches(); V3LeagueStore.save(this@DivanFinalActivity, state); leagueBoard(state)
                            } else configureCumulativeLeague(state)
                        }.show()
                })
            }
            render(body)
        }
    }

    private fun configureCumulativeLeague(state: V3LeagueState) {
        val games = numberField("حداکثر تعداد دور", state.targetGames.toString(), false)
        val target = numberField("امتیاز هدف", state.targetScore.toString(), false)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(7), dp(14), dp(2)); addView(games); addView(space(6)); addView(target) }
        AlertDialog.Builder(this).setTitle("قانون لیگ تجمعی").setView(box).setPositiveButton("شروع") { _, _ ->
            state.targetGames = DivanText.latin(games.text.toString()).toIntOrNull()?.coerceIn(1, 999) ?: 10
            state.targetScore = DivanText.latin(target.text.toString()).toIntOrNull()?.coerceIn(1, 100000) ?: 150
            V3LeagueStore.save(this, state); leagueBoard(state)
        }.setNegativeButton("انصراف", null).show()
    }

    private fun leagueBoard(state: V3LeagueState) {
        backAction = { home() }
        val body = page(); body.addView(topbar("لیگ دیوان") { home() }); body.addView(lead(if (state.finished) "لیگ تمام شد" else "جدول زنده", if (state.mode == V3LeagueMode.BEST_OF_THREE) "Round-robin • هر مصاف تا دو برد" else "تا ${fa(state.targetGames)} دور یا ${fa(state.targetScore)} امتیاز"))
        body.addView(section("جدول"))
        state.ranking().forEachIndexed { index, team ->
            val stats = if (state.mode == V3LeagueMode.BEST_OF_THREE) "${fa(team.matchWins)} برد مصاف • ${fa(team.gameWins)} برد بازی" else "${signed(team.cumulativeScore)} امتیاز"
            body.addView(panel(horizontal = true).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(9), dp(7), dp(9), dp(7))
                addView(text(fa(index + 1), 12f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(30), dp(46)))
                addView(SealAvatarView(this@DivanFinalActivity).apply { label = team.name; variant = team.avatar }, LinearLayout.LayoutParams(dp(46), dp(46)))
                addView(spaceH(7)); addView(text("${team.name}\n$stats", 11f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }, spaced(5))
        }

        if (!state.finished && state.mode == V3LeagueMode.BEST_OF_THREE) {
            body.addView(section("مصاف‌ها"))
            state.matches.forEachIndexed { index, match ->
                val a = state.teams.first { it.id == match.teamAId }
                val b = state.teams.first { it.id == match.teamBId }
                body.addView(panel().apply {
                    setPadding(dp(9), dp(8), dp(9), dp(8))
                    addView(text("${a.name} ${fa(match.winsA)} — ${fa(match.winsB)} ${b.name}", 12f, DivanTheme.ivory, true, Gravity.CENTER))
                    if (!match.completed) {
                        addView(space(5)); addView(two(
                            outline("برد ${a.name}") { state.registerMatchGame(index, a.id); V3LeagueStore.save(this@DivanFinalActivity, state); leagueBoard(state) },
                            outline("برد ${b.name}") { state.registerMatchGame(index, b.id); V3LeagueStore.save(this@DivanFinalActivity, state); leagueBoard(state) }
                        ))
                    }
                }, spaced(5))
            }
        }

        if (!state.finished && state.mode == V3LeagueMode.CUMULATIVE) {
            body.addView(section("دور ${fa(state.cumulativeRounds + 1)}"))
            val inputs = state.teams.map { numberField(it.name, "0", true) }
            inputs.forEachIndexed { i, input -> body.addView(two(text(state.teams[i].name, 11f, DivanTheme.ivory, true), input), spaced(3)) }
            body.addView(primary("ثبت دور لیگ") {
                val raw = inputs.map { DivanText.latin(it.text.toString()).toIntOrNull() }
                if (raw.any { it == null }) { toast("امتیاز همه گروه‌ها را وارد کن"); return@primary }
                state.registerCumulativeRound(raw.map { it!! }); V3LeagueStore.save(this, state); leagueBoard(state)
            })
        }

        if (state.finished) body.addView(info("قهرمان لیگ", state.ranking().firstOrNull()?.name ?: "—"))
        body.addView(space(10))
        body.addView(textButton("حذف لیگ و شروع دوباره", DivanTheme.danger) { confirm("حذف لیگ", "جدول فعلی پاک شود؟") { V3LeagueStore.clear(this); league() } })
        render(body)
    }

    // endregion

    // region Settings / backup

    private fun settingsScreen() {
        backAction = { saveSettings(); home() }
        val body = page(); body.addView(topbar("تنظیمات") { saveSettings(); home() }); body.addView(lead("قانون قابل تنظیم", "اعداد پیش‌فرض را می‌توانی تغییر بدهی؛ موتور اعتبارسنجی نتیجه‌های غیرممکن را همچنان رد می‌کند."))
        body.addView(section("عمومی"))
        body.addView(toggle("بازخورد لمسی", settings.haptic) { settings.haptic = it })
        body.addView(toggle("روشن ماندن صفحه", settings.keepScreenAwake) { settings.keepScreenAwake = it; applyWindowSettings() })
        body.addView(toggle("اعداد فارسی", settings.persianDigits) { settings.persianDigits = it })
        body.addView(toggle("متن درشت", settings.largeText) { settings.largeText = it })

        body.addView(section("شلم"))
        body.addView(stepper("امتیاز هدف", settings.shalamTarget, 100, 5000, 10) { settings.shalamTarget = it })
        body.addView(stepper("حداقل حراج", settings.shalamMinBid, 5, 160, 5) { settings.shalamMinBid = it })
        body.addView(stepper("ضریب شلم", settings.shalamMultiplier, 1, 5, 1) { settings.shalamMultiplier = it })
        body.addView(toggle("یاسا", settings.shalamYasaEnabled) { settings.shalamYasaEnabled = it })
        body.addView(stepper("آستانه یاسا", settings.shalamYasaThreshold, 0, 165, 5) { settings.shalamYasaThreshold = it })
        body.addView(toggle("سرشلم", settings.shalamSarShalamEnabled) { settings.shalamSarShalamEnabled = it })
        body.addView(toggle("جوکر", settings.shalamWithJoker) { settings.shalamWithJoker = it })

        body.addView(section("منفی"))
        body.addView(stepper("تعداد دست مسابقه", settings.menfiHands, 1, 50, 1) { settings.menfiHands = it })
        body.addView(stepper("حداقل اعلام", settings.menfiMinBid, 1, 10, 1) { settings.menfiMinBid = it })
        body.addView(stepper("برد پایه", settings.menfiBaseWin, -500, 500, 5) { settings.menfiBaseWin = it })
        body.addView(stepper("باخت پایه", settings.menfiBaseLoss, -500, 500, 5) { settings.menfiBaseLoss = it })
        body.addView(stepper("پله برد ۴ تا ۱۰", settings.menfiWinStep, 0, 100, 1) { settings.menfiWinStep = it })
        body.addView(stepper("پله جریمه ۴ تا ۱۰", settings.menfiLossStep, 0, 100, 1) { settings.menfiLossStep = it })
        body.addView(stepper("برد ۱۱", settings.menfiWin11, -1000, 1000, 5) { settings.menfiWin11 = it })
        body.addView(stepper("باخت ۱۱", settings.menfiLoss11, -1000, 1000, 5) { settings.menfiLoss11 = it })
        body.addView(stepper("برد ۱۲", settings.menfiWin12, -1000, 1000, 5) { settings.menfiWin12 = it })
        body.addView(stepper("باخت ۱۲", settings.menfiLoss12, -1000, 1000, 5) { settings.menfiLoss12 = it })
        body.addView(stepper("برد ۱۳", settings.menfiWin13, -1000, 1000, 5) { settings.menfiWin13 = it })
        body.addView(stepper("باخت ۱۳", settings.menfiLoss13, -1000, 1000, 5) { settings.menfiLoss13 = it })
        body.addView(cycle("نمایش مجموع", arrayOf("پنهان تا درخواست", "فقط داور", "زنده"), settings.menfiHideMode.coerceIn(0, 2)) { settings.menfiHideMode = it })
        body.addView(cycle("تساوی", arrayOf("هر بار بپرس", "۲ دست اضافه", "۳ دست اضافه"), when (settings.menfiTieExtraMode) { 2 -> 1; 3 -> 2; else -> 0 }) { settings.menfiTieExtraMode = when (it) { 1 -> 2; 2 -> 3; else -> 0 } })

        body.addView(section("هزارتایی"))
        body.addView(info("تعداد بازیکن", "حداقل ۵ و حداکثر ۶۰ نفر ثابت است؛ برای جلوگیری از بازی ناقص این محدوده قابل شکستن نیست."))
        body.addView(stepper("تعداد دور", settings.hezartaiiRounds, 1, 100, 1) { settings.hezartaiiRounds = it })
        body.addView(stepper("جریمه صفر", settings.hezartaiiZeroPenalty, -1000, 0, 10) { settings.hezartaiiZeroPenalty = it })
        body.addView(toggle("پنهان کردن امتیاز تا پایان", settings.hezartaiiHideUntilEnd) { settings.hezartaiiHideUntilEnd = it })

        body.addView(section("داده‌ها"))
        body.addView(outline("خروجی کامل JSON") { exportBackup() })
        body.addView(space(6)); body.addView(outline("بازیابی JSON") { importBackup() })
        body.addView(space(10)); body.addView(primary("ذخیره تنظیمات") { saveSettings(); home() })
        render(body)
    }

    private fun saveSettings() {
        settings.hezartaiiMinPlayers = HezartaiiRulesV4.MIN_PLAYERS
        V3SettingsStore.save(this, settings)
        applyWindowSettings()
    }

    private fun exportBackup() {
        scope.launch {
            pendingExport = repo.exportJson(settings)
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "divan-emtiaz-v4.json")
            }, EXPORT_REQUEST)
        }
    }

    private fun importBackup() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }, IMPORT_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            EXPORT_REQUEST -> {
                val raw = pendingExport ?: return
                runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(raw) } }
                    .onSuccess { toast("بکاپ ذخیره شد") }.onFailure { toast("ذخیره بکاپ ناموفق بود") }
                pendingExport = null
            }
            IMPORT_REQUEST -> scope.launch {
                runCatching {
                    val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("empty")
                    repo.importJson(raw)
                }.onSuccess {
                    settings = it; settings.hezartaiiMinPlayers = 5; V3SettingsStore.save(this@DivanFinalActivity, settings); toast("بازیابی کامل شد"); home()
                }.onFailure { toast("فایل بکاپ معتبر نیست") }
            }
        }
    }

    // endregion

    // region UI primitives

    private fun render(content: View) {
        val root = FrameLayout(this).apply { setBackgroundColor(DivanTheme.bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(content) }
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(14), dp(13), dp(14), dp(34))
    }

    private fun topbar(title: String, back: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(9))
        addView(textButton("‹") { back() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(text(title, 19f, DivanTheme.ivory, true, Gravity.CENTER), LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(SealAvatarView(this@DivanFinalActivity).apply { label = "د"; variant = 0 }, LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun lead(title: String, subtitle: String): View = panel(DivanTheme.surfaceHigh, DivanTheme.line, 20).apply {
        setPadding(dp(13), dp(14), dp(13), dp(14)); addView(text(title, 17f, DivanTheme.ivory, true, Gravity.CENTER)); addView(space(4)); addView(text(subtitle, 11f, DivanTheme.muted, false, Gravity.CENTER)); addView(space(7)); addView(DivanDivider(this@DivanFinalActivity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)))
    }

    private fun section(title: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; addView(space(14)); addView(text(title, 14f, DivanTheme.gold, true)); addView(space(6))
    }

    private fun info(title: String, subtitle: String): View = panel().apply {
        setPadding(dp(11), dp(10), dp(11), dp(10)); addView(text(title, 12f, DivanTheme.gold, true, Gravity.CENTER)); addView(space(2)); addView(text(subtitle, 10f, DivanTheme.muted, false, Gravity.CENTER))
    }

    private fun actionCard(badge: String, title: String, subtitle: String, accent: Int, avatar: Int, action: () -> Unit): View = panel(DivanTheme.surface, accent, 20).apply {
        setPadding(dp(11), dp(11), dp(11), dp(11)); setOnClickListener { tap(it); action() }
        addView(LinearLayout(this@DivanFinalActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(SealAvatarView(this@DivanFinalActivity).apply { label = badge; variant = avatar }, LinearLayout.LayoutParams(dp(68), dp(68)))
            addView(spaceH(10))
            addView(LinearLayout(this@DivanFinalActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(title, 16f, DivanTheme.ivory, true)); addView(space(2)); addView(text(subtitle, 10f, DivanTheme.muted)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text("شروع", 10f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(52), dp(40)))
        })
    }.also { it.layoutParams = spaced() }

    private fun panel(fill: Int = DivanTheme.surface, stroke: Int = DivanTheme.line, radius: Int = 18, horizontal: Boolean = false): LinearLayout = LinearLayout(this).apply {
        orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        background = divanShape(this@DivanFinalActivity, fill, stroke, radius)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, gravityValue: Int = Gravity.RIGHT): TextView = TextView(this).apply {
        text = value; textSize = size + if (settings.largeText) 1.5f else 0f; setTextColor(color); gravity = gravityValue; useDivanTypography(bold); maxLines = 8; setPadding(dp(2), dp(2), dp(2), dp(2))
    }

    private fun baseButton(title: String, fill: Int, color: Int, stroke: Int, action: () -> Unit): Button = Button(this).apply {
        text = title; isAllCaps = false; textSize = 12f; setTextColor(color); useDivanTypography(true); gravity = Gravity.CENTER; minHeight = dp(50); minimumHeight = dp(50); background = divanShape(this@DivanFinalActivity, fill, stroke, 14); setPadding(dp(7), 0, dp(7), 0); setOnClickListener { tap(it); action() }
    }

    private fun primary(title: String, action: () -> Unit): Button = baseButton(title, DivanTheme.gold, DivanTheme.bg, DivanTheme.gold, action)
    private fun outline(title: String, action: () -> Unit): Button = baseButton(title, DivanTheme.surface, DivanTheme.ivory, DivanTheme.goldSoft, action)
    private fun small(title: String, action: () -> Unit): Button = baseButton(title, DivanTheme.surfaceHigh, DivanTheme.ivory, DivanTheme.line, action).apply { minWidth = 0; minimumWidth = 0; textSize = 10f }
    private fun textButton(title: String, color: Int = DivanTheme.gold, action: () -> Unit): Button = baseButton(title, Color.TRANSPARENT, color, Color.TRANSPARENT, action).apply { minWidth = dp(44); minimumWidth = dp(44) }
    private fun segment(title: String, active: Boolean): Button = baseButton(title, if (active) DivanTheme.gold else DivanTheme.surfaceHigh, if (active) DivanTheme.bg else DivanTheme.ivory, if (active) DivanTheme.gold else DivanTheme.line) { }
    private fun styleSegment(button: Button, active: Boolean) { button.background = divanShape(this, if (active) DivanTheme.gold else DivanTheme.surfaceHigh, if (active) DivanTheme.gold else DivanTheme.line, 14); button.setTextColor(if (active) DivanTheme.bg else DivanTheme.ivory) }

    private fun field(hint: String, initial: String): EditText = EditText(this).apply {
        this.hint = hint; setText(initial); setTextColor(DivanTheme.ivory); setHintTextColor(DivanTheme.muted); textSize = 12f; useDivanTypography(); setSingleLine(true); background = divanShape(this@DivanFinalActivity, DivanTheme.surfaceHigh, DivanTheme.line, 12); setPadding(dp(9), 0, dp(9), 0); minHeight = dp(48)
    }

    private fun numberField(hint: String, initial: String, allowSigned: Boolean): EditText = field(hint, initial).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or if (allowSigned) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
        gravity = Gravity.CENTER; textDirection = View.TEXT_DIRECTION_LTR
    }

    private fun toggle(title: String, initial: Boolean, action: (Boolean) -> Unit): View = panel(horizontal = true).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(6)); addView(text(title, 12f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, dp(46), 1f)); addView(Switch(this@DivanFinalActivity).apply { isChecked = initial; setOnCheckedChangeListener { _, v -> action(v) } })
    }.also { it.layoutParams = spaced(5) }

    private fun stepper(title: String, initial: Int, min: Int, max: Int, step: Int, action: (Int) -> Unit): View {
        var value = initial.coerceIn(min, max)
        val valueText = text(fa(value), 14f, DivanTheme.gold, true, Gravity.CENTER)
        fun change(delta: Int) { value = (value + delta).coerceIn(min, max); valueText.text = fa(value); action(value) }
        return panel().apply {
            setPadding(dp(10), dp(7), dp(10), dp(7)); addView(text(title, 11f, DivanTheme.ivory, true)); addView(space(4)); addView(LinearLayout(this@DivanFinalActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; addView(small("−") { change(-step) }, LinearLayout.LayoutParams(dp(70), dp(46))); addView(valueText, LinearLayout.LayoutParams(dp(102), dp(46))); addView(small("+") { change(step) }, LinearLayout.LayoutParams(dp(70), dp(46)))
            })
        }.also { it.layoutParams = spaced(5) }
    }

    private fun cycle(title: String, values: Array<String>, initial: Int, action: (Int) -> Unit): View {
        var index = initial.coerceIn(0, values.lastIndex)
        val value = text(values[index], 11f, DivanTheme.gold, true, Gravity.CENTER)
        return panel(horizontal = true).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(6)); addView(text(title, 12f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, dp(46), 1f)); addView(value, LinearLayout.LayoutParams(dp(158), dp(46))); setOnClickListener { index = (index + 1) % values.size; value.text = values[index]; action(index) }
        }.also { it.layoutParams = spaced(5) }
    }

    private fun two(a: View, b: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(spaceH(6)); addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun space(v: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(v)) }
    private fun spaceH(v: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(v), 1) }
    private fun spaced(bottom: Int = 7) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottom) }
    private fun dp(value: Int): Int = this.dp(value)

    // endregion

    // region Helpers

    private fun save(session: V3Session, after: (() -> Unit)? = null) {
        scope.launch {
            repo.save(session)
            saveLabel?.text = "● ذخیره شد • ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
            after?.invoke()
        }
    }

    private fun sessionSummary(session: V3Session): String = if (session.game == V3GameType.HEZARTAII) {
        "${fa(session.players.size)} بازیکن • ${fa(session.rounds.size)} دور"
    } else {
        val t = teamTotals(session); "${session.teamA!!.name} ${signed(t.first)} | ${session.teamB!!.name} ${signed(t.second)}"
    }

    private fun EditText.clean(fallback: String): String = text.toString().trim().ifBlank { fallback }
    private fun fa(value: Any): String = DivanText.fa(value, settings.persianDigits)
    private fun signed(value: Int): String = DivanText.signed(value, settings.persianDigits)
    private fun date(ts: Long): String = SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale("fa", "IR")).format(Date(ts))
    private fun tap(view: View) { if (settings.haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("تأیید") { _, _ -> action() }.setNegativeButton("انصراف", null).show()
    }

    private fun choose(title: String, values: Array<String>, action: (String) -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setItems(values) { _, index -> action(values[index]) }.show()
    }

    // endregion
}
