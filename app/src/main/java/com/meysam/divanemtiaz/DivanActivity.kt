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
 * Divan Emtiaz v3 UI.
 * Built from zero; deliberately does not use the legacy RoyalUi or image assets.
 */
class DivanActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repo: V3Repository
    private lateinit var settings: V3Settings
    private var backAction: (() -> Unit)? = null
    private var saveLabel: TextView? = null
    private var pendingExport: String? = null

    companion object {
        private const val EXPORT_REQUEST = 701
        private const val IMPORT_REQUEST = 702
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

    // region Home / navigation

    private fun home() {
        backAction = null
        scope.launch {
            val unfinished = repo.history().firstOrNull { !it.finished }
            val body = column()
            body.addView(brandHeader())
            if (unfinished != null) {
                body.addView(sectionTitle("ادامه سریع"))
                body.addView(actionCard(
                    badge = unfinished.game.title.take(1),
                    title = "${unfinished.game.title} در جریان",
                    subtitle = sessionSummary(unfinished),
                    accent = DivanTheme.warning,
                    actionText = "ادامه بازی"
                ) { live(unfinished) })
            }

            body.addView(sectionTitle("بازی جدید"))
            body.addView(gameCard(V3GameType.SHALAM, "ش", "تعهد، یاسا و شلم", "داوری سریع دو گروه", DivanTheme.emerald))
            body.addView(gameCard(V3GameType.MENFI, "م", "حکم ثابت و امتیاز پنهان", "قوانین و ضرایب کاملاً قابل تنظیم", DivanTheme.crimson))
            body.addView(gameCard(V3GameType.HEZARTAII, "۱۰۰۰", "رقابت انفرادی", "۵ بازیکن یا بیشتر؛ بدون تیم", DivanTheme.blue))

            body.addView(sectionTitle("دیوان"))
            body.addView(menuGrid())
            body.addView(space(28))
            body.addView(label("نسخه ۳.۰ • آفلاین • بدون تبلیغ", 12f, DivanTheme.muted, false, Gravity.CENTER))
            render(body)
        }
    }

    private fun brandHeader(): View = card(DivanTheme.surfaceHigh, DivanTheme.gold, 24).apply {
        setPadding(dp(18), dp(24), dp(18), dp(22))
        addView(LinearLayout(this@DivanActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val seal = SealAvatarView(this@DivanActivity).apply { label = "دیوان"; variant = 4 }
            addView(seal, LinearLayout.LayoutParams(dp(84), dp(84)))
            addView(spaceH(14))
            addView(LinearLayout(this@DivanActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("دیوان امتیاز", 29f, DivanTheme.ivory, true))
                addView(space(4))
                addView(label("داور ساده و دقیق بازی‌های ایرانی", 13f, DivanTheme.muted))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(space(14))
        addView(DivanDivider(this@DivanActivity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)))
        addView(label("امتیازها درشت، مسیر ثبت کوتاه، همه‌چیز ذخیره‌شده", 12f, DivanTheme.goldSoft, false, Gravity.CENTER))
    }

    private fun gameCard(game: V3GameType, badge: String, title: String, subtitle: String, accent: Int): View =
        actionCard(badge, game.title, "$title\n$subtitle", accent, "شروع") { setup(game) }

    private fun menuGrid(): View = GridLayout(this).apply {
        columnCount = 2
        addMenuCell("تاریخچه", "سوابق و ادامه", "س") { history() }
        addMenuCell("گروه‌ها", "بانک گروه‌های ثابت", "گ") { teams() }
        addMenuCell("لیگ", "دوبه‌دو و تجمعی", "ل") { league() }
        addMenuCell("تنظیمات", "قانون و پشتیبان", "ت") { settingsScreen() }
    }

    private fun GridLayout.addMenuCell(title: String, subtitle: String, badge: String, action: () -> Unit) {
        addView(card().apply {
            setPadding(dp(12), dp(13), dp(12), dp(13))
            gravity = Gravity.CENTER
            addView(SealAvatarView(this@DivanActivity).apply { label = badge; variant = childCount }, LinearLayout.LayoutParams(dp(54), dp(54)))
            addView(space(6)); addView(label(title, 15f, DivanTheme.ivory, true, Gravity.CENTER)); addView(label(subtitle, 11f, DivanTheme.muted, false, Gravity.CENTER))
            setOnClickListener { tap(it); action() }
        }, GridLayout.LayoutParams().apply {
            width = 0; height = dp(140); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4))
        })
    }

    // endregion

    // region Setup

    private fun setup(game: V3GameType) {
        if (game == V3GameType.HEZARTAII) setupHezartaii() else setupTeams(game)
    }

    private fun setupTeams(game: V3GameType) {
        backAction = { home() }
        var avatarA = 0
        var avatarB = 1
        var trump = "پیک"
        val body = column()
        body.addView(topbar("آماده‌سازی ${game.title}") { home() })
        body.addView(lead("دو گروه، هر گروه دو یار ثابت", "نام‌ها را یک‌بار ثبت کن؛ بعداً از بانک گروه‌ها آماده انتخاب‌اند."))

        val nameA = field("نام گروه اول", "شیران پارس")
        val a1 = field("یار اول", "")
        val a2 = field("یار دوم", "")
        val sealA = SealAvatarView(this).apply { label = nameA.text.toString(); variant = avatarA }
        body.addView(teamEditor("گروه اول", sealA, nameA, a1, a2,
            avatarAction = { avatarA = (avatarA + 1) % 6; sealA.variant = avatarA },
            bankAction = { chooseTeam { t -> nameA.setText(t.name); a1.setText(t.member1); a2.setText(t.member2); avatarA = t.avatar; sealA.label = t.name; sealA.variant = avatarA } }
        ))
        body.addView(space(10))

        val nameB = field("نام گروه دوم", "پارس‌بانان")
        val b1 = field("یار اول", "")
        val b2 = field("یار دوم", "")
        val sealB = SealAvatarView(this).apply { label = nameB.text.toString(); variant = avatarB }
        body.addView(teamEditor("گروه دوم", sealB, nameB, b1, b2,
            avatarAction = { avatarB = (avatarB + 1) % 6; sealB.variant = avatarB },
            bankAction = { chooseTeam { t -> nameB.setText(t.name); b1.setText(t.member1); b2.setText(t.member2); avatarB = t.avatar; sealB.label = t.name; sealB.variant = avatarB } }
        ))

        val trumpValue = label("حکم: $trump", 15f, DivanTheme.gold, true, Gravity.CENTER)
        if (game == V3GameType.MENFI) {
            body.addView(sectionTitle("حکم ثابت مسابقه")); body.addView(trumpValue)
            body.addView(space(7)); body.addView(outlineButton("تغییر حکم") {
                choose("خال حکم", arrayOf("پیک", "دل", "خشت", "گشنیز")) { value -> trump = value; trumpValue.text = "حکم: $trump" }
            })
        }

        body.addView(space(16))
        body.addView(outlineButton("ذخیره هر دو گروه در بانک") {
            scope.launch {
                repo.saveTeam(V3TeamEntity(name = nameA.clean("گروه اول"), member1 = a1.clean(""), member2 = a2.clean(""), avatar = avatarA))
                repo.saveTeam(V3TeamEntity(name = nameB.clean("گروه دوم"), member1 = b1.clean(""), member2 = b2.clean(""), avatar = avatarB))
                toast("گروه‌ها ذخیره شدند")
            }
        })
        body.addView(space(9))
        body.addView(primaryButton("شروع ${game.title}") {
            val session = V3Session(
                game = game,
                teamA = V3Team(nameA.clean("گروه اول"), a1.clean(""), a2.clean(""), avatarA),
                teamB = V3Team(nameB.clean("گروه دوم"), b1.clean(""), b2.clean(""), avatarB),
                menfiTrump = trump,
                menfiHandsTarget = settings.menfiHands,
                settingsSnapshot = settings.toJson().toString()
            )
            save(session) { live(session) }
        })
        render(body)
    }

    private fun teamEditor(
        title: String,
        seal: SealAvatarView,
        name: EditText,
        p1: EditText,
        p2: EditText,
        avatarAction: () -> Unit,
        bankAction: () -> Unit
    ): View = card().apply {
        setPadding(dp(13), dp(13), dp(13), dp(13))
        addView(label(title, 15f, DivanTheme.gold, true))
        addView(space(10))
        addView(LinearLayout(this@DivanActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            seal.setOnClickListener { avatarAction() }
            addView(seal, LinearLayout.LayoutParams(dp(76), dp(76)))
            addView(spaceH(10))
            addView(name, LinearLayout.LayoutParams(0, dp(52), 1f))
        })
        addView(space(7)); addView(two(p1, p2)); addView(space(8)); addView(outlineButton("انتخاب از بانک گروه‌ها") { bankAction() })
    }

    private fun chooseTeam(action: (V3TeamEntity) -> Unit) {
        scope.launch {
            val list = repo.teams()
            if (list.isEmpty()) { toast("هنوز گروهی ذخیره نشده"); return@launch }
            AlertDialog.Builder(this@DivanActivity).setTitle("بانک گروه‌ها")
                .setItems(list.map { it.name }.toTypedArray()) { _, i -> action(list[i]) }.show()
        }
    }

    private fun setupHezartaii() {
        backAction = { home() }
        val body = column()
        body.addView(topbar("آماده‌سازی هزارتایی") { home() })
        body.addView(lead("رقابت کاملاً انفرادی", "حداقل ${fa(settings.hezartaiiMinPlayers)} بازیکن. هر نفر امتیاز و رتبه مستقل دارد."))
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val names = mutableListOf<EditText>()
        val avatars = mutableListOf<Int>()

        fun addPlayer() {
            val index = names.size
            val input = field("نام بازیکن ${fa(index + 1)}", "بازیکن ${fa(index + 1)}")
            names += input; avatars += index % 6
            val seal = SealAvatarView(this).apply { label = "${index + 1}"; variant = avatars[index] }
            holder.addView(card(horizontal = true).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(9))
                seal.setOnClickListener { avatars[index] = (avatars[index] + 1) % 6; seal.variant = avatars[index] }
                addView(seal, LinearLayout.LayoutParams(dp(58), dp(58))); addView(spaceH(9)); addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
            }, spaced())
        }
        repeat(settings.hezartaiiMinPlayers.coerceAtLeast(5)) { addPlayer() }
        body.addView(holder)
        body.addView(outlineButton("+ افزودن بازیکن") { addPlayer() })
        body.addView(space(10))
        body.addView(primaryButton("شروع هزارتایی") {
            if (names.size < settings.hezartaiiMinPlayers) { toast("حداقل ${fa(settings.hezartaiiMinPlayers)} بازیکن لازم است"); return@primaryButton }
            val players = names.mapIndexed { i, e -> V3Player(e.clean("بازیکن ${i + 1}"), avatars[i]) }.toMutableList()
            val session = V3Session(game = V3GameType.HEZARTAII, players = players, settingsSnapshot = settings.toJson().toString())
            save(session) { live(session) }
        })
        render(body)
    }

    // endregion

    // region Live scoring

    private fun live(session: V3Session) {
        when (session.game) {
            V3GameType.SHALAM -> shalam(session)
            V3GameType.MENFI -> menfi(session)
            V3GameType.HEZARTAII -> hezartaii(session)
        }
    }

    private fun liveTop(session: V3Session, back: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(topbar("${session.game.title} • داوری زنده", back))
        saveLabel = label("● ذخیره خودکار فعال", 11f, DivanTheme.muted, false, Gravity.CENTER)
        addView(saveLabel)
        addView(space(8))
    }

    private fun shalam(session: V3Session) {
        backAction = { leaveLive(session) }
        val body = column(); body.addView(liveTop(session) { leaveLive(session) })
        body.addView(teamScoreboard(session, false))
        body.addView(gapCard(session))
        body.addView(sectionTitle("دست ${fa(session.rounds.size + 1)}"))
        body.addView(label("اول حاکم را انتخاب کن، بعد عدد تعهد.", 12f, DivanTheme.muted, false, Gravity.CENTER))
        body.addView(space(8))
        var contractor = 0
        val aBtn = segment(session.teamA!!.name, true)
        val bBtn = segment(session.teamB!!.name, false)
        fun selected(index: Int) {
            contractor = index
            styleSegment(aBtn, index == 0); styleSegment(bBtn, index == 1)
        }
        aBtn.setOnClickListener { selected(0) }; bBtn.setOnClickListener { selected(1) }
        body.addView(two(aBtn, bBtn)); body.addView(space(10))
        val bids = GridLayout(this).apply { columnCount = 4 }
        ShalamEngineV3.readyBids(settings.shalamMinBid).forEach { bid ->
            bids.addView(smallButton(if (bid == 165) "شلم" else fa(bid)) { shalamResultDialog(session, contractor, bid) }, GridLayout.LayoutParams().apply {
                width = 0; height = dp(50); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        body.addView(bids)
        body.addView(liveTools(session))
        body.addView(rounds(session))
        body.addView(space(10)); body.addView(primaryButton("پایان و نتیجه") { finish(session) })
        render(body)
    }

    private fun shalamResultDialog(session: V3Session, contractor: Int, bid: Int) {
        val opponent = numberField("امتیاز حریف از ۰ تا ۱۶۵", "0", false)
        val sar = CheckBox(this).apply {
            text = "سرشلم در گزارش ثبت شود"; setTextColor(DivanTheme.ivory); useDivanTypography()
            visibility = if (settings.shalamSarShalamEnabled && bid == 165) View.VISIBLE else View.GONE
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(2)); addView(opponent); addView(space(8)); addView(sar) }
        AlertDialog.Builder(this).setTitle(if (bid == 165) "ثبت شلم" else "تعهد ${fa(bid)}")
            .setMessage("فقط امتیاز واقعی تیم مقابل را وارد کن؛ سهم حاکم خودکار حساب می‌شود.")
            .setView(box).setPositiveButton("ثبت") { _, _ ->
                val opp = DivanText.latin(opponent.text.toString()).toIntOrNull()
                if (opp == null || opp !in 0..165) { toast("عدد باید بین ۰ تا ۱۶۵ باشد"); return@setPositiveButton }
                val result = ShalamEngineV3.score(bid, opp, settings, declaredShalam = bid == 165)
                val scores = if (contractor == 0) mutableListOf(result.contractScore, result.opponentScore) else mutableListOf(result.opponentScore, result.contractScore)
                val note = buildString {
                    append(if (bid == 165) "شلم" else "تعهد $bid")
                    if (result.yasaApplied) append(" • یاسا")
                    if (sar.isChecked) append(" • سرشلم")
                }
                session.rounds += V3Round(scores, note, mutableMapOf("contract" to bid.toString(), "contractTeam" to contractor.toString(), "opponentActual" to opp.toString()))
                save(session) { shalam(session) }
            }.setNegativeButton("انصراف", null).show()
    }

    private fun menfi(session: V3Session) {
        backAction = { leaveLive(session) }
        val body = column(); body.addView(liveTop(session) { leaveLive(session) })
        val hidden = settings.menfiHideMode == 0 && !session.revealed
        body.addView(teamScoreboard(session, hidden))
        body.addView(info("حکم ${session.menfiTrump}", "دست ${fa(session.rounds.size + 1)} از ${fa(session.menfiHandsTarget)}"))
        if (!hidden) body.addView(gapCard(session))
        body.addView(sectionTitle("ثبت دست"))

        var bidA = settings.menfiMinBid
        var bidB = settings.menfiMinBid
        val ba = segment("اعلام ${session.teamA!!.name}: ${fa(bidA)}", true)
        val bb = segment("اعلام ${session.teamB!!.name}: ${fa(bidB)}", false)
        ba.setOnClickListener { chooseMenfiBid { bidA = it; ba.text = "اعلام ${session.teamA!!.name}: ${fa(it)}" } }
        bb.setOnClickListener { chooseMenfiBid { bidB = it; bb.text = "اعلام ${session.teamB!!.name}: ${fa(it)}" } }
        body.addView(two(ba, bb)); body.addView(space(8))
        val ta = numberField("تریک واقعی ${session.teamA!!.name}", "0", false)
        val tb = numberField("تریک واقعی ${session.teamB!!.name}", "0", false)
        body.addView(two(ta, tb)); body.addView(space(9))
        body.addView(primaryButton("ثبت دست") {
            val tricksA = DivanText.latin(ta.text.toString()).toIntOrNull()
            val tricksB = DivanText.latin(tb.text.toString()).toIntOrNull()
            if (tricksA == null || tricksB == null || tricksA !in 0..13 || tricksB !in 0..13) { toast("تریک هر گروه باید بین ۰ تا ۱۳ باشد"); return@primaryButton }
            val result = MenfiEngineV3.score(bidA, tricksA, bidB, tricksB, settings)
            session.rounds += V3Round(
                mutableListOf(result.teamAScore, result.teamBScore),
                "اعلام $bidA/$bidB • تریک $tricksA/$tricksB",
                mutableMapOf("bidA" to bidA.toString(), "bidB" to bidB.toString(), "tricksA" to tricksA.toString(), "tricksB" to tricksB.toString())
            )
            if (settings.menfiHideMode == 2) session.revealed = true
            save(session) { menfi(session) }
        })
        body.addView(space(8))
        body.addView(outlineButton(if (session.revealed) "پنهان‌کردن مجموع" else "نمایش مجموع برای داور") {
            session.revealed = !session.revealed; save(session) { menfi(session) }
        })
        body.addView(liveTools(session)); body.addView(rounds(session)); body.addView(space(10))
        body.addView(primaryButton("بررسی پایان مسابقه") { menfiFinishCheck(session) })
        render(body)
    }

    private fun chooseMenfiBid(action: (Int) -> Unit) {
        val values = (settings.menfiMinBid..13).toList()
        AlertDialog.Builder(this).setTitle("عدد اعلام").setItems(values.map { fa(it) }.toTypedArray()) { _, i -> action(values[i]) }.show()
    }

    private fun menfiFinishCheck(session: V3Session) {
        if (session.rounds.size < session.menfiHandsTarget) { toast("${fa(session.menfiHandsTarget - session.rounds.size)} دست باقی مانده"); return }
        session.revealed = true
        val totals = teamTotals(session)
        if (totals.first != totals.second) { finish(session); return }
        fun addGames(count: Int) {
            session.menfiHandsTarget += count * settings.menfiHands
            save(session) { menfi(session) }
        }
        when (settings.menfiTieExtraMode) {
            2 -> addGames(2)
            3 -> addGames(3)
            else -> AlertDialog.Builder(this).setTitle("بازی مساوی شد").setMessage("۲ بازی یا ۳ بازی دیگر؟")
                .setPositiveButton("۲ بازی") { _, _ -> addGames(2) }.setNegativeButton("۳ بازی") { _, _ -> addGames(3) }.show()
        }
    }

    private fun hezartaii(session: V3Session) {
        backAction = { leaveLive(session) }
        val body = column(); body.addView(liveTop(session) { leaveLive(session) })
        val hide = settings.hezartaiiHideUntilEnd && !session.finished
        body.addView(ranking(session, hide))
        body.addView(sectionTitle("دور ${fa(session.rounds.size + 1)} از ${fa(settings.hezartaiiRounds)}"))
        body.addView(info("صفر = ${signed(settings.hezartaiiZeroPenalty)}", "ورودی صفر به جریمه تنظیم‌شده تبدیل می‌شود."))
        val inputs = mutableListOf<EditText>()
        session.players.forEachIndexed { i, p ->
            val e = numberField(p.name, "0", true); inputs += e
            body.addView(card(horizontal = true).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8))
                addView(SealAvatarView(this@DivanActivity).apply { label = p.name; variant = p.avatar }, LinearLayout.LayoutParams(dp(54), dp(54)))
                addView(spaceH(8)); addView(label(p.name, 13f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, dp(48), 1f)); addView(e, LinearLayout.LayoutParams(dp(116), dp(50)))
            }, spaced())
        }
        body.addView(primaryButton("ثبت دور") {
            val values = inputs.map { DivanText.latin(it.text.toString()).toIntOrNull() }
            if (values.any { it == null }) { toast("امتیاز همه بازیکن‌ها را وارد کن"); return@primaryButton }
            val scores = values.map { HezartaiiEngineV3.normalize(it!!, settings) }.toMutableList()
            session.rounds += V3Round(scores, "دور ${session.rounds.size + 1}")
            if (session.rounds.size >= settings.hezartaiiRounds) finish(session) else save(session) { hezartaii(session) }
        })
        body.addView(liveTools(session)); body.addView(rounds(session)); body.addView(space(8)); body.addView(outlineButton("پایان زودتر و نتیجه") { finish(session) })
        render(body)
    }

    private fun liveTools(session: V3Session): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; addView(sectionTitle("ابزار داور"))
        addView(two(
            outlineButton("↶ برگرداندن آخرین ثبت") { undoLast(session) },
            outlineButton("اشتراک وضعیت") { share(session, false) }
        ))
    }

    private fun undoLast(session: V3Session) {
        if (session.rounds.isEmpty()) { toast("چیزی برای برگرداندن نیست"); return }
        confirm("برگرداندن آخرین ثبت", "آخرین دست/دور حذف شود؟ این تغییر در گزارش ثبت می‌شود.") {
            val index = session.rounds.lastIndex
            val before = session.rounds.removeAt(index)
            scope.launch {
                repo.addAudit(session.id, index, "UNDO", before, null)
                repo.save(session)
                live(session)
            }
        }
    }

    private fun leaveLive(session: V3Session) {
        confirm("خروج از بازی", "بازی ذخیره شده و از «ادامه سریع» یا تاریخچه قابل ادامه است.") { save(session) { home() } }
    }

    // endregion

    // region Scores / history / result

    private fun teamTotals(session: V3Session): Pair<Int, Int> =
        session.rounds.sumOf { it.scores.getOrElse(0) { 0 } } to session.rounds.sumOf { it.scores.getOrElse(1) { 0 } }

    private fun teamScoreboard(session: V3Session, hidden: Boolean): View {
        val totals = teamTotals(session)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(scoreTile(session.teamA!!.name, session.teamA!!.avatar, if (hidden) "•••" else signed(totals.first), DivanTheme.emerald), LinearLayout.LayoutParams(0, dp(150), 1f))
            addView(spaceH(8))
            addView(scoreTile(session.teamB!!.name, session.teamB!!.avatar, if (hidden) "•••" else signed(totals.second), DivanTheme.crimson), LinearLayout.LayoutParams(0, dp(150), 1f))
        }
    }

    private fun scoreTile(name: String, avatar: Int, score: String, accent: Int): View = card(DivanTheme.surfaceHigh, accent, 20).apply {
        gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(10))
        addView(SealAvatarView(this@DivanActivity).apply { label = name; variant = avatar }, LinearLayout.LayoutParams(dp(52), dp(52)))
        addView(space(4)); addView(label(name, 12f, DivanTheme.ivory, true, Gravity.CENTER)); addView(label(score, 25f, DivanTheme.gold, true, Gravity.CENTER))
    }

    private fun gapCard(session: V3Session): View {
        val t = teamTotals(session)
        return info("فاصله امتیاز", ScoreGapV3.teamGap(session.teamA!!.name, t.first, session.teamB!!.name, t.second))
    }

    private fun ranking(session: V3Session, hidden: Boolean): View = card().apply {
        setPadding(dp(12), dp(12), dp(12), dp(12)); addView(label("رتبه‌بندی", 15f, DivanTheme.gold, true, Gravity.CENTER)); addView(space(7))
        if (hidden) addView(label("امتیازها تا پایان پنهان‌اند.", 12f, DivanTheme.muted, false, Gravity.CENTER))
        else HezartaiiEngineV3.ranking(session.players, session.rounds).forEachIndexed { index, pair ->
            addView(LinearLayout(this@DivanActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4))
                addView(label(fa(index + 1), 13f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(30), dp(42)))
                addView(SealAvatarView(this@DivanActivity).apply { label = pair.first.name; variant = pair.first.avatar }, LinearLayout.LayoutParams(dp(42), dp(42)))
                addView(spaceH(7)); addView(label(pair.first.name, 13f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, dp(42), 1f)); addView(label(signed(pair.second), 14f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(88), dp(42)))
            })
        }
    }

    private fun rounds(session: V3Session): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; addView(sectionTitle("رکوردها"))
        if (session.rounds.isEmpty()) addView(label("هنوز رکوردی ثبت نشده.", 12f, DivanTheme.muted, false, Gravity.CENTER))
        session.rounds.forEachIndexed { index, round ->
            addView(card(horizontal = true).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8))
                addView(label(fa(index + 1), 13f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(32), dp(44)))
                addView(label("${round.scores.joinToString(" | ") { signed(it) }}\n${round.note}", 12f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(textButton("ویرایش") { editRound(session, index) })
            }, spaced())
        }
    }

    private fun editRound(session: V3Session, index: Int) {
        val old = session.rounds[index].copy(scores = session.rounds[index].scores.toMutableList(), meta = session.rounds[index].meta.toMutableMap())
        val fields = old.scores.mapIndexed { i, v -> numberField(if (session.game == V3GameType.HEZARTAII) session.players.getOrNull(i)?.name ?: "بازیکن" else if (i == 0) session.teamA?.name ?: "گروه اول" else session.teamB?.name ?: "گروه دوم", v.toString(), true) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(4)); fields.forEach { addView(it); addView(space(6)) } }
        AlertDialog.Builder(this).setTitle("ویرایش رکورد ${fa(index + 1)}").setView(box).setPositiveButton("ذخیره") { _, _ ->
            val values = fields.map { DivanText.latin(it.text.toString()).toIntOrNull() }
            if (values.any { it == null }) { toast("یکی از اعداد نامعتبر است"); return@setPositiveButton }
            val updated = old.copy(scores = values.map { it!! }.toMutableList(), note = old.note + " • اصلاح", editedAt = System.currentTimeMillis())
            session.rounds[index] = updated
            scope.launch { repo.addAudit(session.id, index, "EDIT", old, updated); repo.save(session); live(session) }
        }.setNegativeButton("انصراف", null).show()
    }

    private fun finish(session: V3Session) {
        if (session.rounds.isEmpty()) { toast("حداقل یک رکورد لازم است"); return }
        session.finished = true; session.revealed = true
        save(session) { result(session) }
    }

    private fun result(session: V3Session) {
        backAction = { home() }
        val body = column(); body.addView(topbar("نتیجه نهایی") { home() })
        if (session.game == V3GameType.HEZARTAII) {
            val rank = HezartaiiEngineV3.ranking(session.players, session.rounds)
            body.addView(resultHero(rank.firstOrNull()?.first?.name ?: "—", session.game.title))
            body.addView(ranking(session, false)); body.addView(sectionTitle("فاصله تا صدر")); ScoreGapV3.individualGap(rank).forEach { body.addView(label(it, 12f, DivanTheme.ivory, false, Gravity.CENTER)) }
        } else {
            val t = teamTotals(session)
            val winner = when {
                t.first == t.second -> "مساوی"
                session.game == V3GameType.MENFI && t.first < t.second -> session.teamA!!.name
                session.game == V3GameType.MENFI -> session.teamB!!.name
                t.first > t.second -> session.teamA!!.name
                else -> session.teamB!!.name
            }
            body.addView(resultHero(winner, session.game.title)); body.addView(teamScoreboard(session, false)); body.addView(gapCard(session))
        }
        body.addView(rounds(session)); body.addView(space(10)); body.addView(primaryButton("اشتراک نتیجه") { share(session, true) }); body.addView(space(8)); body.addView(outlineButton("بازگشت به خانه") { home() })
        render(body)
    }

    private fun resultHero(winner: String, game: String): View = card(DivanTheme.surfaceHigh, DivanTheme.gold, 24).apply {
        gravity = Gravity.CENTER; setPadding(dp(16), dp(22), dp(16), dp(22)); addView(SealAvatarView(this@DivanActivity).apply { label = winner; variant = 4 }, LinearLayout.LayoutParams(dp(88), dp(88))); addView(space(8)); addView(label("$winner", 25f, DivanTheme.ivory, true, Gravity.CENTER)); addView(label("نتیجه نهایی $game", 12f, DivanTheme.gold, false, Gravity.CENTER))
    }

    private fun share(session: V3Session, final: Boolean) {
        val text = buildString {
            append("دیوان امتیاز — ${session.game.title}\n")
            if (session.game == V3GameType.HEZARTAII) {
                HezartaiiEngineV3.ranking(session.players, session.rounds).forEachIndexed { i, p -> append("${i + 1}. ${p.first.name}: ${p.second}\n") }
            } else {
                val t = teamTotals(session); append("${session.teamA!!.name}: ${t.first}\n${session.teamB!!.name}: ${t.second}\n")
            }
            append(if (final) "نتیجه نهایی" else "وضعیت فعلی")
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "اشتراک نتیجه"))
    }

    // endregion

    // region History / Teams

    private fun history(filter: V3GameType? = null, finishedOnly: Boolean? = null) {
        backAction = { home() }
        scope.launch {
            var items = repo.history()
            filter?.let { g -> items = items.filter { it.game == g } }
            finishedOnly?.let { done -> items = items.filter { it.finished == done } }
            val body = column(); body.addView(topbar("تاریخچه") { home() })
            body.addView(two(
                outlineButton(if (filter == null) "همه بازی‌ها" else filter.title) { historyFilterDialog() },
                outlineButton(when (finishedOnly) { true -> "تمام‌شده"; false -> "در جریان"; null -> "همه وضعیت‌ها" }) { historyStatusDialog(filter) }
            ))
            body.addView(space(10))
            if (items.isEmpty()) body.addView(info("چیزی پیدا نشد", "فیلتر را عوض کن یا یک بازی جدید شروع کن."))
            items.forEach { s ->
                body.addView(card().apply {
                    setPadding(dp(12), dp(11), dp(12), dp(11))
                    addView(LinearLayout(this@DivanActivity).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        addView(SealAvatarView(this@DivanActivity).apply { label = s.game.title; variant = s.game.ordinal }, LinearLayout.LayoutParams(dp(54), dp(54))); addView(spaceH(8))
                        addView(LinearLayout(this@DivanActivity).apply { orientation = LinearLayout.VERTICAL; addView(label("${s.game.title} • ${if (s.finished) "پایان‌یافته" else "در جریان"}", 14f, DivanTheme.ivory, true)); addView(label(sessionSummary(s), 11f, DivanTheme.muted)); addView(label(date(s.updatedAt), 10f, DivanTheme.goldSoft)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    })
                    addView(space(7)); addView(two(outlineButton(if (s.finished) "مشاهده" else "ادامه") { if (s.finished) result(s) else live(s) }, outlineButton("گزارش تغییرات") { audit(s) }))
                    addView(space(6)); addView(textButton("حذف بازی", DivanTheme.danger) { confirm("حذف بازی", "این بازی برای همیشه حذف شود؟") { scope.launch { repo.deleteSession(s.id); history(filter, finishedOnly) } } })
                }, spaced())
            }
            render(body)
        }
    }

    private fun historyFilterDialog() {
        val values = arrayOf("همه", "شلم", "منفی", "هزارتایی")
        AlertDialog.Builder(this).setTitle("نوع بازی").setItems(values) { _, i -> history(if (i == 0) null else V3GameType.values()[i - 1], null) }.show()
    }

    private fun historyStatusDialog(game: V3GameType?) {
        AlertDialog.Builder(this).setTitle("وضعیت").setItems(arrayOf("همه", "در جریان", "تمام‌شده")) { _, i -> history(game, when (i) { 1 -> false; 2 -> true; else -> null }) }.show()
    }

    private fun audit(session: V3Session) {
        scope.launch {
            val logs = repo.audit(session.id); backAction = { history() }
            val body = column(); body.addView(topbar("گزارش تغییرات") { history() }); body.addView(info(session.game.title, "ویرایش، حذف و Undo با زمان ثبت می‌شوند."))
            if (logs.isEmpty()) body.addView(label("تغییری ثبت نشده.", 12f, DivanTheme.muted, false, Gravity.CENTER))
            logs.forEach { l -> body.addView(card().apply { setPadding(dp(12), dp(10), dp(12), dp(10)); addView(label("${l.action} • رکورد ${fa(l.roundIndex + 1)}", 13f, DivanTheme.gold, true)); addView(label(date(l.timestamp), 10f, DivanTheme.muted)) }, spaced()) }
            render(body)
        }
    }

    private fun teams() {
        backAction = { home() }
        scope.launch {
            val list = repo.teams(); val finished = repo.history().filter { it.finished && it.game != V3GameType.HEZARTAII }
            val body = column(); body.addView(topbar("گروه‌ها") { home() }); body.addView(lead("بانک گروه‌های ثابت", "هر گروه دو یار دارد و در شروع شلم یا منفی با یک لمس انتخاب می‌شود.")); body.addView(primaryButton("+ گروه جدید") { addTeamDialog() }); body.addView(space(10))
            if (list.isEmpty()) body.addView(info("بانک خالی است", "اولین گروه را بساز."))
            list.forEach { t ->
                val games = finished.filter { it.teamA?.name == t.name || it.teamB?.name == t.name }
                val wins = games.count { s ->
                    val score = teamTotals(s)
                    if (s.game == V3GameType.MENFI) (s.teamA?.name == t.name && score.first < score.second) || (s.teamB?.name == t.name && score.second < score.first)
                    else (s.teamA?.name == t.name && score.first > score.second) || (s.teamB?.name == t.name && score.second > score.first)
                }
                body.addView(card(horizontal = true).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(9)); addView(SealAvatarView(this@DivanActivity).apply { label = t.name; variant = t.avatar }, LinearLayout.LayoutParams(dp(60), dp(60))); addView(spaceH(9)); addView(LinearLayout(this@DivanActivity).apply { orientation = LinearLayout.VERTICAL; addView(label(t.name, 14f, DivanTheme.ivory, true)); addView(label("${t.member1} • ${t.member2}", 11f, DivanTheme.muted)); addView(label("برد ${fa(wins)} از ${fa(games.size)}", 11f, DivanTheme.gold)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(textButton("حذف", DivanTheme.danger) { confirm("حذف گروه", "تاریخچه بازی‌ها باقی می‌ماند.") { scope.launch { repo.deleteTeam(t.id); teams() } } })
                }, spaced())
            }
            render(body)
        }
    }

    private fun addTeamDialog() {
        val name = field("نام گروه", ""); val p1 = field("یار اول", ""); val p2 = field("یار دوم", "")
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(2)); addView(name); addView(space(6)); addView(p1); addView(space(6)); addView(p2) }
        AlertDialog.Builder(this).setTitle("گروه جدید").setView(box).setPositiveButton("ذخیره") { _, _ ->
            if (name.text.toString().trim().isBlank()) { toast("نام گروه لازم است"); return@setPositiveButton }
            scope.launch { repo.saveTeam(V3TeamEntity(name = name.clean(""), member1 = p1.clean(""), member2 = p2.clean(""), avatar = (System.currentTimeMillis() % 6).toInt())); teams() }
        }.setNegativeButton("انصراف", null).show()
    }

    // endregion

    // region League

    private fun league() {
        backAction = { home() }
        val state = V3LeagueStore.load(this)
        if (state == null) leagueStart() else leagueState(state)
    }

    private fun leagueStart() {
        scope.launch {
            val list = repo.teams(); val body = column(); body.addView(topbar("لیگ") { home() }); body.addView(lead("لیگ دیوان", "حداقل ۳ گروه. حالت دوبه‌دو تا ۲ برد، یا جدول تجمعی."))
            if (list.size < 3) { body.addView(info("حداقل ۳ گروه لازم است", "الان ${fa(list.size)} گروه در بانک داری.")); body.addView(space(8)); body.addView(primaryButton("ساخت گروه") { teams() }) }
            else body.addView(primaryButton("ساخت لیگ با ${fa(list.size)} گروه") {
                choose("نوع لیگ", arrayOf("دوبه‌دو؛ هر مصاف تا ۲ برد", "تجمعی؛ تا تعداد دور یا امتیاز هدف")) { selected ->
                    val state = V3LeagueState(mode = if (selected.startsWith("دوبه")) V3LeagueMode.BEST_OF_THREE else V3LeagueMode.CUMULATIVE, teams = list.map { V3LeagueTeam(it.id, it.name, it.avatar) }.toMutableList())
                    if (state.mode == V3LeagueMode.BEST_OF_THREE) state.rebuildMatches(); V3LeagueStore.save(this@DivanActivity, state); leagueState(state)
                }
            })
            render(body)
        }
    }

    private fun leagueState(state: V3LeagueState) {
        backAction = { home() }
        val body = column(); body.addView(topbar("لیگ دیوان") { home() }); body.addView(lead(if (state.finished) "لیگ تمام شد" else "جدول زنده", if (state.mode == V3LeagueMode.BEST_OF_THREE) "مصاف دوبه‌دو تا دو برد" else "امتیاز تجمعی"))
        body.addView(sectionTitle("جدول"))
        state.ranking().forEachIndexed { i, t ->
            val value = if (state.mode == V3LeagueMode.BEST_OF_THREE) "${fa(t.matchWins)} برد مصاف • ${fa(t.gameWins)} برد بازی" else "${signed(t.cumulativeScore)} امتیاز"
            body.addView(card(horizontal = true).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)); addView(label(fa(i + 1), 14f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(32), dp(44))); addView(SealAvatarView(this@DivanActivity).apply { label = t.name; variant = t.avatar }, LinearLayout.LayoutParams(dp(48), dp(48))); addView(spaceH(8)); addView(label("${t.name}\n$value", 12f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }, spaced())
        }
        if (!state.finished && state.mode == V3LeagueMode.BEST_OF_THREE) {
            body.addView(sectionTitle("مصاف‌ها")); state.matches.forEachIndexed { index, m ->
                val a = state.teams.first { it.id == m.teamAId }; val b = state.teams.first { it.id == m.teamBId }
                body.addView(card().apply {
                    setPadding(dp(10), dp(10), dp(10), dp(10)); addView(label("${a.name} ${fa(m.winsA)} — ${fa(m.winsB)} ${b.name}", 13f, DivanTheme.ivory, true, Gravity.CENTER))
                    if (!m.completed) { addView(space(6)); addView(two(outlineButton("برد ${a.name}") { state.registerMatchGame(index, a.id); V3LeagueStore.save(this@DivanActivity, state); leagueState(state) }, outlineButton("برد ${b.name}") { state.registerMatchGame(index, b.id); V3LeagueStore.save(this@DivanActivity, state); leagueState(state) })) }
                }, spaced())
            }
        }
        if (!state.finished && state.mode == V3LeagueMode.CUMULATIVE) {
            body.addView(sectionTitle("دور ${fa(state.cumulativeRounds + 1)}")); body.addView(info("شرط پایان", "${fa(state.targetGames)} دور یا رسیدن به ${fa(state.targetScore)} امتیاز؛ هرکدام زودتر."))
            val inputs = state.teams.map { numberField(it.name, "0", true) }
            inputs.forEachIndexed { i, e -> body.addView(two(label(state.teams[i].name, 12f, DivanTheme.ivory, true), e), spaced()) }
            body.addView(primaryButton("ثبت دور لیگ") {
                val values = inputs.map { DivanText.latin(it.text.toString()).toIntOrNull() }
                if (values.any { it == null }) { toast("همه امتیازها را وارد کن"); return@primaryButton }
                state.registerCumulativeRound(values.map { it!! }); V3LeagueStore.save(this, state); leagueState(state)
            })
        }
        if (state.finished) body.addView(info("قهرمان", state.ranking().firstOrNull()?.name ?: "—"))
        body.addView(space(12)); body.addView(textButton("حذف لیگ و شروع دوباره", DivanTheme.danger) { confirm("حذف لیگ", "جدول فعلی پاک شود؟") { V3LeagueStore.clear(this); league() } })
        render(body)
    }

    // endregion

    // region Settings / backup

    private fun settingsScreen() {
        backAction = { saveSettings(); home() }
        val body = column(); body.addView(topbar("تنظیمات") { saveSettings(); home() }); body.addView(lead("قانون دست خودت", "تنظیمات محلی‌اند و همراه بکاپ JSON منتقل می‌شوند."))
        body.addView(sectionTitle("عمومی")); body.addView(toggle("بازخورد لمسی", settings.haptic) { settings.haptic = it }); body.addView(toggle("روشن ماندن صفحه", settings.keepScreenAwake) { settings.keepScreenAwake = it; applyWindowSettings() }); body.addView(toggle("اعداد فارسی", settings.persianDigits) { settings.persianDigits = it }); body.addView(toggle("متن درشت", settings.largeText) { settings.largeText = it })
        body.addView(sectionTitle("شلم")); body.addView(stepper("امتیاز هدف", settings.shalamTarget, 100, 5000, 10) { settings.shalamTarget = it }); body.addView(stepper("حداقل حراج", settings.shalamMinBid, 5, 160, 5) { settings.shalamMinBid = it }); body.addView(stepper("ضریب شلم", settings.shalamMultiplier, 1, 5, 1) { settings.shalamMultiplier = it }); body.addView(toggle("یاسا", settings.shalamYasaEnabled) { settings.shalamYasaEnabled = it }); body.addView(stepper("آستانه یاسا", settings.shalamYasaThreshold, 0, 165, 5) { settings.shalamYasaThreshold = it }); body.addView(toggle("سرشلم", settings.shalamSarShalamEnabled) { settings.shalamSarShalamEnabled = it }); body.addView(toggle("برد فقط به اندازه تعهد", settings.shalamAwardContractOnly) { settings.shalamAwardContractOnly = it }); body.addView(toggle("جوکر", settings.shalamWithJoker) { settings.shalamWithJoker = it })
        body.addView(sectionTitle("منفی")); body.addView(stepper("تعداد دست", settings.menfiHands, 1, 30, 1) { settings.menfiHands = it }); body.addView(stepper("حداقل اعلام", settings.menfiMinBid, 1, 10, 1) { settings.menfiMinBid = it }); body.addView(stepper("برد پایه", settings.menfiBaseWin, -100, 300, 5) { settings.menfiBaseWin = it }); body.addView(stepper("باخت پایه", settings.menfiBaseLoss, -300, 100, 5) { settings.menfiBaseLoss = it }); body.addView(stepper("پله برد", settings.menfiWinStep, 0, 100, 1) { settings.menfiWinStep = it }); body.addView(stepper("پله جریمه", settings.menfiLossStep, 0, 100, 1) { settings.menfiLossStep = it }); body.addView(stepper("برد ۱۱", settings.menfiWin11, -500, 500, 5) { settings.menfiWin11 = it }); body.addView(stepper("باخت ۱۱", settings.menfiLoss11, -500, 500, 5) { settings.menfiLoss11 = it }); body.addView(stepper("برد ۱۲", settings.menfiWin12, -500, 500, 5) { settings.menfiWin12 = it }); body.addView(stepper("باخت ۱۲", settings.menfiLoss12, -500, 500, 5) { settings.menfiLoss12 = it }); body.addView(stepper("برد ۱۳", settings.menfiWin13, -500, 500, 5) { settings.menfiWin13 = it }); body.addView(stepper("باخت ۱۳", settings.menfiLoss13, -500, 500, 5) { settings.menfiLoss13 = it }); body.addView(cycle("نمایش امتیاز", arrayOf("پنهان تا درخواست", "فقط داور", "زنده"), settings.menfiHideMode) { settings.menfiHideMode = it }); body.addView(cycle("تساوی", arrayOf("پرسش", "۲ بازی اضافه", "۳ بازی اضافه"), when (settings.menfiTieExtraMode) { 2 -> 1; 3 -> 2; else -> 0 }) { settings.menfiTieExtraMode = when (it) { 1 -> 2; 2 -> 3; else -> 0 } })
        body.addView(sectionTitle("هزارتایی")); body.addView(stepper("حداقل بازیکن", settings.hezartaiiMinPlayers, 5, 30, 1) { settings.hezartaiiMinPlayers = it }); body.addView(stepper("تعداد دور", settings.hezartaiiRounds, 1, 50, 1) { settings.hezartaiiRounds = it }); body.addView(stepper("جریمه صفر", settings.hezartaiiZeroPenalty, -500, 0, 10) { settings.hezartaiiZeroPenalty = it }); body.addView(toggle("پنهان تا پایان", settings.hezartaiiHideUntilEnd) { settings.hezartaiiHideUntilEnd = it })
        body.addView(sectionTitle("داده‌ها")); body.addView(outlineButton("خروجی JSON") { exportBackup() }); body.addView(space(7)); body.addView(outlineButton("بازیابی JSON") { importBackup() }); body.addView(space(12)); body.addView(primaryButton("ذخیره تنظیمات") { saveSettings(); home() })
        render(body)
    }

    private fun saveSettings() { V3SettingsStore.save(this, settings); applyWindowSettings() }

    private fun exportBackup() {
        scope.launch {
            pendingExport = repo.exportJson(settings)
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "divan-emtiaz-v3.json") }, EXPORT_REQUEST)
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
                    val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("فایل خالی")
                    repo.importJson(raw)
                }.onSuccess { settings = it; V3SettingsStore.save(this@DivanActivity, settings); toast("بازیابی کامل شد"); home() }
                    .onFailure { toast("فایل بکاپ معتبر نیست") }
            }
        }
    }

    // endregion

    // region Reusable UI

    private fun render(content: View) {
        val root = FrameLayout(this).apply { setBackgroundColor(DivanTheme.bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(content) }
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(15), dp(14), dp(15), dp(34))
    }

    private fun topbar(title: String, back: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10))
        addView(textButton("‹") { back() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(label(title, 20f, DivanTheme.ivory, true, Gravity.CENTER), LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(SealAvatarView(this@DivanActivity).apply { label = "د"; variant = 4 }, LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun lead(title: String, subtitle: String): View = card(DivanTheme.surfaceHigh, DivanTheme.line, 20).apply {
        setPadding(dp(15), dp(16), dp(15), dp(16)); addView(label(title, 18f, DivanTheme.ivory, true, Gravity.CENTER)); addView(space(5)); addView(label(subtitle, 12f, DivanTheme.muted, false, Gravity.CENTER)); addView(space(8)); addView(DivanDivider(this@DivanActivity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)))
    }

    private fun sectionTitle(title: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; addView(space(15)); addView(label(title, 15f, DivanTheme.gold, true)); addView(space(6))
    }

    private fun info(title: String, subtitle: String): View = card().apply {
        setPadding(dp(12), dp(11), dp(12), dp(11)); addView(label(title, 13f, DivanTheme.gold, true, Gravity.CENTER)); addView(space(3)); addView(label(subtitle, 11f, DivanTheme.muted, false, Gravity.CENTER))
    }

    private fun actionCard(badge: String, title: String, subtitle: String, accent: Int, actionText: String, action: () -> Unit): View = card(DivanTheme.surface, accent, 20).apply {
        setPadding(dp(13), dp(13), dp(13), dp(13)); setOnClickListener { tap(it); action() }
        addView(LinearLayout(this@DivanActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(SealAvatarView(this@DivanActivity).apply { label = badge; variant = badge.hashCode() }, LinearLayout.LayoutParams(dp(66), dp(66))); addView(spaceH(11))
            addView(LinearLayout(this@DivanActivity).apply { orientation = LinearLayout.VERTICAL; addView(label(title, 17f, DivanTheme.ivory, true)); addView(space(3)); addView(label(subtitle, 11f, DivanTheme.muted)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(actionText, 11f, DivanTheme.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(64), dp(42)))
        })
    }.also { it.layoutParams = spaced() }

    private fun card(fill: Int = DivanTheme.surface, stroke: Int = DivanTheme.line, radius: Int = 18, horizontal: Boolean = false): LinearLayout = LinearLayout(this).apply {
        orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL; background = divanShape(this@DivanActivity, fill, stroke, radius)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false, gravityValue: Int = Gravity.RIGHT): TextView = TextView(this).apply {
        text = value; textSize = size + if (settings.largeText) 1.5f else 0f; setTextColor(color); gravity = gravityValue; useDivanTypography(bold); maxLines = 5; setPadding(dp(2), dp(2), dp(2), dp(2))
    }

    private fun button(title: String, fill: Int, textColor: Int, stroke: Int, action: () -> Unit): Button = Button(this).apply {
        text = title; isAllCaps = false; textSize = 13f; setTextColor(textColor); useDivanTypography(true); gravity = Gravity.CENTER; minHeight = dp(52); minimumHeight = dp(52); background = divanShape(this@DivanActivity, fill, stroke, 15); setPadding(dp(9), dp(2), dp(9), dp(2)); setOnClickListener { tap(it); action() }
    }

    private fun primaryButton(title: String, action: () -> Unit): Button = button(title, DivanTheme.gold, DivanTheme.bg, DivanTheme.gold, action)
    private fun outlineButton(title: String, action: () -> Unit): Button = button(title, DivanTheme.surface, DivanTheme.ivory, DivanTheme.goldSoft, action)
    private fun smallButton(title: String, action: () -> Unit): Button = button(title, DivanTheme.surfaceHigh, DivanTheme.ivory, DivanTheme.line, action).apply { minWidth = 0; minimumWidth = 0; textSize = 11f }
    private fun textButton(title: String, color: Int = DivanTheme.gold, action: () -> Unit): Button = button(title, Color.TRANSPARENT, color, Color.TRANSPARENT, action).apply { minWidth = dp(48); minimumWidth = dp(48) }
    private fun segment(title: String, active: Boolean): Button = button(title, if (active) DivanTheme.gold else DivanTheme.surfaceHigh, if (active) DivanTheme.bg else DivanTheme.ivory, if (active) DivanTheme.gold else DivanTheme.line) { }
    private fun styleSegment(button: Button, active: Boolean) { button.background = divanShape(this, if (active) DivanTheme.gold else DivanTheme.surfaceHigh, if (active) DivanTheme.gold else DivanTheme.line, 15); button.setTextColor(if (active) DivanTheme.bg else DivanTheme.ivory) }

    private fun field(hint: String, initial: String): EditText = EditText(this).apply {
        this.hint = hint; setText(initial); setTextColor(DivanTheme.ivory); setHintTextColor(DivanTheme.muted); textSize = 13f; useDivanTypography(); setSingleLine(true); background = divanShape(this@DivanActivity, DivanTheme.surfaceHigh, DivanTheme.line, 13); setPadding(dp(10), 0, dp(10), 0); minHeight = dp(52)
    }

    private fun numberField(hint: String, initial: String, signed: Boolean): EditText = field(hint, initial).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0; gravity = Gravity.CENTER; textDirection = View.TEXT_DIRECTION_LTR
    }

    private fun toggle(title: String, initial: Boolean, action: (Boolean) -> Unit): View = card(horizontal = true).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); addView(label(title, 13f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, dp(48), 1f)); addView(Switch(this@DivanActivity).apply { isChecked = initial; setOnCheckedChangeListener { _, value -> action(value) } })
    }.also { it.layoutParams = spaced() }

    private fun stepper(title: String, initial: Int, min: Int, max: Int, step: Int, action: (Int) -> Unit): View {
        var value = initial
        val valueText = label(fa(value), 15f, DivanTheme.gold, true, Gravity.CENTER)
        fun change(delta: Int) { value = (value + delta).coerceIn(min, max); valueText.text = fa(value); action(value) }
        return card().apply {
            setPadding(dp(12), dp(9), dp(12), dp(9)); addView(label(title, 12f, DivanTheme.ivory, true)); addView(space(5)); addView(LinearLayout(this@DivanActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; addView(smallButton("−") { change(-step) }, LinearLayout.LayoutParams(dp(74), dp(48))); addView(valueText, LinearLayout.LayoutParams(dp(104), dp(48))); addView(smallButton("+") { change(step) }, LinearLayout.LayoutParams(dp(74), dp(48))) })
        }.also { it.layoutParams = spaced() }
    }

    private fun cycle(title: String, values: Array<String>, initial: Int, action: (Int) -> Unit): View {
        var index = initial.coerceIn(0, values.lastIndex)
        val v = label(values[index], 12f, DivanTheme.gold, true, Gravity.CENTER)
        return card(horizontal = true).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); addView(label(title, 13f, DivanTheme.ivory, true), LinearLayout.LayoutParams(0, dp(48), 1f)); addView(v, LinearLayout.LayoutParams(dp(150), dp(48))); setOnClickListener { index = (index + 1) % values.size; v.text = values[index]; action(index) }
        }.also { it.layoutParams = spaced() }
    }

    private fun two(a: View, b: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(spaceH(7)); addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun space(value: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(value)) }
    private fun spaceH(value: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(value), 1) }
    private fun spaced() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
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
    private fun tap(v: View) { if (settings.haptic) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("تأیید") { _, _ -> action() }.setNegativeButton("انصراف", null).show()
    }

    private fun choose(title: String, values: Array<String>, action: (String) -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setItems(values) { _, i -> action(values[i]) }.show()
    }

    // endregion
}
