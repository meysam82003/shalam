package com.meysam.divanemtiaz

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
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
import android.widget.ImageView
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

class V3Activity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repo: V3Repository
    private lateinit var settings: V3Settings
    private var activeSession: V3Session? = null
    private var backAction: (() -> Unit)? = null
    private var pendingExport: String? = null

    companion object {
        private const val EXPORT_REQUEST = 301
        private const val IMPORT_REQUEST = 302
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = V3Repository(this)
        settings = V3SettingsStore.load(this)
        window.statusBarColor = RoyalPalette.midnight
        window.navigationBarColor = RoyalPalette.midnight
        applyWindowSettings()
        showHome()
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

    private fun showHome() {
        activeSession = null
        backAction = null
        val page = page()
        page.addView(hero(R.drawable.royal_hero_home, "دیوان امتیاز", "شلم • منفی • هزارتایی"))
        page.addView(section("شروع داوری"))
        V3GameType.values().forEach { game -> page.addView(gameCard(game), spaced()) }
        page.addView(primaryButton("شروع بازی جدید") { showGameSelection() })
        page.addView(gap(10))
        page.addView(secondaryButton("تاریخچه و ادامه بازی‌ها") { showHistory() })
        page.addView(gap(10))
        page.addView(secondaryButton("مدیریت گروه‌ها") { showTeams() })
        page.addView(gap(10))
        page.addView(secondaryButton("لیگ گروه‌ها") { showLeague() })
        page.addView(gap(10))
        page.addView(secondaryButton("تنظیمات و پشتیبان‌گیری") { showSettings() })
        page.addView(gap(24))
        page.addView(text("نسخهٔ ۳.۰ • بومی اندروید • آفلاین • بدون تبلیغ", 12f, RoyalPalette.muted, false, Gravity.CENTER))
        render(page)
    }

    private fun showGameSelection() {
        backAction = { showHome() }
        val page = page()
        page.addView(toolbar("انتخاب بازی") { showHome() })
        page.addView(text("نوع داوری را انتخاب کنید", 14f, RoyalPalette.muted, false, Gravity.CENTER))
        page.addView(gap(14))
        V3GameType.values().forEach { game -> page.addView(gameCard(game), spaced()) }
        render(page)
    }

    private fun gameCard(game: V3GameType): View = panel().apply {
        setPadding(dp(10), dp(10), dp(10), dp(14))
        addView(GameArtView(this@V3Activity, legacy(game)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(136)))
        addView(gap(9))
        addView(text(game.title, 23f, RoyalPalette.paleGold, true, Gravity.CENTER))
        val subtitle = when (game) {
            V3GameType.SHALAM -> "تعهد، یاسا، شلم و سرشلم"
            V3GameType.MENFI -> "حکم ثابت، ثبت پنهان و امتیاز قابل‌تنظیم"
            V3GameType.HEZARTAII -> "۵ نفر به بالا، انفرادی و رتبه‌بندی زنده"
        }
        addView(text(subtitle, 12f, RoyalPalette.muted, false, Gravity.CENTER))
        setOnClickListener { tap(it); showSetup(game) }
    }

    private fun legacy(game: V3GameType): GameType = when (game) {
        V3GameType.SHALAM -> GameType.SHALAM
        V3GameType.MENFI -> GameType.MENFI
        V3GameType.HEZARTAII -> GameType.HEZARTAII
    }

    private fun showSetup(game: V3GameType) {
        if (game == V3GameType.HEZARTAII) showHezartaiiSetup() else showTeamGameSetup(game)
    }

    private fun showTeamGameSetup(game: V3GameType) {
        backAction = { showGameSelection() }
        var avatarA = 0
        var avatarB = 3
        var trump = "پیک"
        val page = page()
        page.addView(toolbar("آماده‌سازی ${game.title}") { showGameSelection() })
        page.addView(hero(if (game == V3GameType.SHALAM) R.drawable.royal_hero_shalam else R.drawable.royal_hero_menfi, game.title, "دو گروه، هر گروه دو یار ثابت"))

        val teamAName = input("نام گروه اول", "شیران پارس")
        val memberA1 = input("بازیکن اول", "")
        val memberA2 = input("بازیکن دوم", "")
        val avatarAView = AvatarCropView(this, avatarA)
        page.addView(teamSetupCard("گروه اول", teamAName, memberA1, memberA2, avatarAView,
            chooseAvatar = { pickAvatar { avatarA = it; avatarAView.setAvatar(it) } },
            chooseSaved = { chooseSavedTeam { t -> teamAName.setText(t.name); memberA1.setText(t.member1); memberA2.setText(t.member2); avatarA = t.avatar; avatarAView.setAvatar(avatarA) } }
        ))
        page.addView(gap(12))

        val teamBName = input("نام گروه دوم", "پارس‌بانان")
        val memberB1 = input("بازیکن اول", "")
        val memberB2 = input("بازیکن دوم", "")
        val avatarBView = AvatarCropView(this, avatarB)
        page.addView(teamSetupCard("گروه دوم", teamBName, memberB1, memberB2, avatarBView,
            chooseAvatar = { pickAvatar { avatarB = it; avatarBView.setAvatar(it) } },
            chooseSaved = { chooseSavedTeam { t -> teamBName.setText(t.name); memberB1.setText(t.member1); memberB2.setText(t.member2); avatarB = t.avatar; avatarBView.setAvatar(avatarB) } }
        ))

        if (game == V3GameType.MENFI) {
            page.addView(section("حکم ثابت کل مسابقه"))
            val trumpText = text("حکم: $trump", 16f, RoyalPalette.paleGold, true, Gravity.CENTER)
            page.addView(trumpText)
            page.addView(gap(8))
            page.addView(secondaryButton("انتخاب خال حکم") {
                val items = arrayOf("پیک", "دل", "خشت", "گشنیز")
                AlertDialog.Builder(this).setTitle("حکم ثابت").setItems(items) { _, which ->
                    trump = items[which]; trumpText.text = "حکم: $trump"
                }.show()
            })
        }

        page.addView(gap(18))
        page.addView(secondaryButton("ذخیره هر دو گروه در بانک") {
            scope.launch {
                val a = V3TeamEntity(name = teamAName.clean("گروه اول"), member1 = memberA1.clean(""), member2 = memberA2.clean(""), avatar = avatarA)
                val b = V3TeamEntity(name = teamBName.clean("گروه دوم"), member1 = memberB1.clean(""), member2 = memberB2.clean(""), avatar = avatarB)
                repo.saveTeam(a); repo.saveTeam(b); toast("گروه‌ها ذخیره شدند")
            }
        })
        page.addView(gap(10))
        page.addView(primaryButton("شروع داوری ${game.title}") {
            val session = V3Session(
                game = game,
                teamA = V3Team(teamAName.clean("گروه اول"), memberA1.clean(""), memberA2.clean(""), avatarA),
                teamB = V3Team(teamBName.clean("گروه دوم"), memberB1.clean(""), memberB2.clean(""), avatarB),
                menfiTrump = trump,
                menfiHandsTarget = settings.menfiHands,
                settingsSnapshot = settings.toJson().toString()
            )
            activeSession = session
            saveAndShow(session)
        })
        render(page)
    }

    private fun teamSetupCard(
        title: String,
        name: EditText,
        member1: EditText,
        member2: EditText,
        avatar: AvatarCropView,
        chooseAvatar: () -> Unit,
        chooseSaved: () -> Unit
    ): View = panel().apply {
        setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(text(title, 17f, RoyalPalette.gold, true, Gravity.CENTER))
        addView(gap(8))
        val row = LinearLayout(this@V3Activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            avatar.setOnClickListener { chooseAvatar() }
            addView(avatar, LinearLayout.LayoutParams(dp(86), dp(100)))
            addView(gapHorizontal(10))
            addView(LinearLayout(this@V3Activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(name); addView(gap(6)); addView(member1); addView(gap(6)); addView(member2)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(row)
        addView(gap(8))
        addView(secondaryButton("انتخاب از گروه‌های ذخیره‌شده") { chooseSaved() })
    }

    private fun showHezartaiiSetup() {
        backAction = { showGameSelection() }
        val page = page()
        page.addView(toolbar("آماده‌سازی هزارتایی") { showGameSelection() })
        page.addView(hero(R.drawable.royal_hero_hezartaii, "هزارتایی", "رقابت انفرادی؛ بدون تیم"))
        page.addView(infoPanel("حداقل ${fa(settings.hezartaiiMinPlayers)} بازیکن", "هر بازیکن مستقل ثبت می‌شود و سقف تعداد بازیکن نداریم."))
        page.addView(gap(12))

        val names = mutableListOf<EditText>()
        val avatars = mutableListOf<Int>()
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun addPlayer(index: Int) {
            val name = input("نام بازیکن ${fa(index + 1)}", "بازیکن ${fa(index + 1)}")
            var avatarIndex = index % 16
            names += name; avatars += avatarIndex
            val avatar = AvatarCropView(this, avatarIndex)
            holder.addView(panel(true).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10))
                avatar.setOnClickListener {
                    pickAvatar { selected -> avatarIndex = selected; avatars[index] = selected; avatar.setAvatar(selected) }
                }
                addView(avatar, LinearLayout.LayoutParams(dp(70), dp(82)))
                addView(gapHorizontal(10)); addView(name, LinearLayout.LayoutParams(0, dp(54), 1f))
            }, spaced())
        }
        repeat(settings.hezartaiiMinPlayers.coerceAtLeast(5)) { addPlayer(it) }
        page.addView(holder)
        page.addView(secondaryButton("+ افزودن بازیکن") { addPlayer(names.size) })
        page.addView(gap(14))
        page.addView(primaryButton("شروع هزارتایی") {
            if (names.size < settings.hezartaiiMinPlayers) { toast("حداقل ${fa(settings.hezartaiiMinPlayers)} بازیکن لازم است"); return@primaryButton }
            val players = names.mapIndexed { index, e -> V3Player(e.clean("بازیکن ${index + 1}"), avatars[index]) }.toMutableList()
            val session = V3Session(game = V3GameType.HEZARTAII, players = players, settingsSnapshot = settings.toJson().toString())
            activeSession = session
            saveAndShow(session)
        })
        render(page)
    }

    private fun chooseSavedTeam(selected: (V3TeamEntity) -> Unit) {
        scope.launch {
            val teams = repo.teams()
            if (teams.isEmpty()) { toast("هنوز گروهی ذخیره نشده"); return@launch }
            AlertDialog.Builder(this@V3Activity)
                .setTitle("انتخاب گروه")
                .setItems(teams.map { it.name }.toTypedArray()) { _, which -> selected(teams[which]) }
                .show()
        }
    }

    private fun saveAndShow(session: V3Session) {
        scope.launch { repo.save(session); showLive(session) }
    }

    private fun showLive(session: V3Session) {
        activeSession = session
        when (session.game) {
            V3GameType.SHALAM -> showShalam(session)
            V3GameType.MENFI -> showMenfi(session)
            V3GameType.HEZARTAII -> showHezartaii(session)
        }
    }

    private fun showShalam(session: V3Session) {
        backAction = { askLeave(session) }
        val page = page()
        page.addView(toolbar("داوری زندهٔ شلم") { askLeave(session) })
        page.addView(teamScoreHeader(session, false))
        val totals = teamTotals(session)
        page.addView(infoPanel("فاصله امتیاز", ScoreGapV3.teamGap(session.teamA!!.name, totals.first, session.teamB!!.name, totals.second)))
        if (totals.first >= settings.shalamTarget || totals.second >= settings.shalamTarget) {
            page.addView(gap(8)); page.addView(infoPanel("امتیاز هدف رسیده است", "می‌توانید بازی را پایان دهید یا طبق قانون محلی ادامه دهید."))
        }
        page.addView(section("دست ${fa(session.rounds.size + 1)} — حاکم و تعهد"))
        var contractTeam = 0
        val a = choiceButton(session.teamA!!.name, true)
        val b = choiceButton(session.teamB!!.name, false)
        fun select(index: Int) { contractTeam = index; styleChoice(a, index == 0); styleChoice(b, index == 1) }
        a.setOnClickListener { select(0) }; b.setOnClickListener { select(1) }
        page.addView(twoColumn(a, b))
        page.addView(gap(10))
        val grid = GridLayout(this).apply { columnCount = 4 }
        ShalamEngineV3.readyBids(settings.shalamMinBid).forEach { bid ->
            val label = if (bid == 165) "شلم" else fa(bid)
            grid.addView(compactButton(label) { showShalamEntry(session, contractTeam, bid) }, GridLayout.LayoutParams().apply {
                width = 0; height = dp(52); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        page.addView(grid)
        page.addView(roundHistory(session))
        page.addView(gap(12))
        page.addView(primaryButton("پایان بازی و نتیجه") { finishSession(session) })
        render(page)
    }

    private fun showShalamEntry(session: V3Session, contractTeam: Int, bid: Int) {
        backAction = { showShalam(session) }
        val page = page()
        page.addView(toolbar("ثبت نتیجهٔ دست شلم") { showShalam(session) })
        page.addView(hero(R.drawable.royal_hero_shalam, if (bid == 165) "شلم" else "تعهد ${fa(bid)}", if (contractTeam == 0) session.teamA!!.name else session.teamB!!.name))
        val opp = numericInput("امتیاز واقعی تیم مقابل از ۰ تا ۱۶۵", "0", false)
        page.addView(infoPanel("امتیاز حریف را وارد کنید", "امتیاز حاکم خودکار از ۱۶۵ منهای امتیاز حریف محاسبه می‌شود."))
        page.addView(gap(10)); page.addView(opp)
        val sar = CheckBox(this).apply {
            text = "این دست سرشلم بوده است (صرفاً ثبت در گزارش)"; setTextColor(RoyalPalette.cream); preparePersianText(this@V3Activity)
            visibility = if (settings.shalamSarShalamEnabled && bid == 165) View.VISIBLE else View.GONE
        }
        page.addView(gap(10)); page.addView(sar)
        page.addView(gap(14))
        page.addView(primaryButton("محاسبه و ثبت") {
            val opponent = toLatin(opp.text.toString()).toIntOrNull()
            if (opponent == null || opponent !in 0..165) { toast("امتیاز حریف باید بین ۰ تا ۱۶۵ باشد"); return@primaryButton }
            val result = ShalamEngineV3.score(bid, opponent, settings, declaredShalam = bid == 165)
            val scores = if (contractTeam == 0) mutableListOf(result.contractScore, result.opponentScore) else mutableListOf(result.opponentScore, result.contractScore)
            val meta = mutableMapOf(
                "contract" to bid.toString(), "contractTeam" to contractTeam.toString(), "opponentActual" to opponent.toString(),
                "contractActual" to result.contractActual.toString(), "yasa" to result.yasaApplied.toString(), "shalam" to result.shalamApplied.toString(), "sarshalam" to sar.isChecked.toString()
            )
            val note = buildString {
                append(if (bid == 165) "شلم" else "تعهد $bid")
                if (result.yasaApplied) append(" • یاسا")
                if (sar.isChecked) append(" • سرشلم")
            }
            session.rounds += V3Round(scores, note, meta)
            autoSave(session); showShalam(session)
        })
        render(page)
    }

    private fun showMenfi(session: V3Session) {
        backAction = { askLeave(session) }
        val page = page()
        page.addView(toolbar("داوری زندهٔ منفی") { askLeave(session) })
        val hidden = settings.menfiHideMode == 0 && !session.revealed
        page.addView(teamScoreHeader(session, hidden))
        page.addView(infoPanel("حکم ثابت: ${session.menfiTrump}", "دست ${fa(session.rounds.size + 1)} از ${fa(session.menfiHandsTarget)}"))
        if (!hidden) {
            val totals = teamTotals(session)
            page.addView(gap(8)); page.addView(infoPanel("فاصله امتیاز", ScoreGapV3.teamGap(session.teamA!!.name, totals.first, session.teamB!!.name, totals.second)))
        }
        page.addView(section("ثبت اعلام و تریک واقعی"))
        var bidA = settings.menfiMinBid
        var bidB = settings.menfiMinBid
        val bidAText = text("اعلام ${session.teamA!!.name}: ${fa(bidA)}", 14f, RoyalPalette.paleGold, true, Gravity.CENTER)
        val bidBText = text("اعلام ${session.teamB!!.name}: ${fa(bidB)}", 14f, RoyalPalette.paleGold, true, Gravity.CENTER)
        page.addView(twoColumn(
            secondaryButton("انتخاب اعلام گروه اول") { pickMenfiBid { bidA = it; bidAText.text = "اعلام ${session.teamA!!.name}: ${fa(it)}" } },
            secondaryButton("انتخاب اعلام گروه دوم") { pickMenfiBid { bidB = it; bidBText.text = "اعلام ${session.teamB!!.name}: ${fa(it)}" } }
        ))
        page.addView(twoColumn(bidAText, bidBText))
        page.addView(gap(10))
        val tricksA = numericInput("تریک واقعی ${session.teamA!!.name}", "0", false)
        val tricksB = numericInput("تریک واقعی ${session.teamB!!.name}", "0", false)
        page.addView(twoColumn(tricksA, tricksB))
        page.addView(gap(12))
        page.addView(primaryButton("ثبت دست") {
            val ta = toLatin(tricksA.text.toString()).toIntOrNull()
            val tb = toLatin(tricksB.text.toString()).toIntOrNull()
            if (ta == null || tb == null || ta !in 0..13 || tb !in 0..13) { toast("تریک واقعی باید بین ۰ تا ۱۳ باشد"); return@primaryButton }
            val result = MenfiEngineV3.score(bidA, ta, bidB, tb, settings)
            session.rounds += V3Round(
                mutableListOf(result.teamAScore, result.teamBScore),
                "اعلام ${bidA}/${bidB} • تریک ${ta}/${tb}",
                mutableMapOf("bidA" to bidA.toString(), "bidB" to bidB.toString(), "tricksA" to ta.toString(), "tricksB" to tb.toString())
            )
            session.revealed = settings.menfiHideMode == 2
            autoSave(session); showMenfi(session)
        })
        page.addView(gap(10))
        if (!session.revealed) page.addView(secondaryButton("نمایش نتایج برای راوی") { session.revealed = true; autoSave(session); showMenfi(session) })
        else page.addView(secondaryButton("پنهان‌کردن دوباره مجموع") { session.revealed = false; autoSave(session); showMenfi(session) })

        page.addView(roundHistory(session))
        page.addView(gap(12))
        page.addView(primaryButton("بررسی پایان مسابقه") { checkMenfiFinish(session) })
        render(page)
    }

    private fun pickMenfiBid(selected: (Int) -> Unit) {
        val values = (settings.menfiMinBid..13).toList()
        AlertDialog.Builder(this).setTitle("عدد اعلام").setItems(values.map { fa(it) }.toTypedArray()) { _, which -> selected(values[which]) }.show()
    }

    private fun checkMenfiFinish(session: V3Session) {
        if (session.rounds.size < session.menfiHandsTarget) {
            toast("هنوز ${fa(session.menfiHandsTarget - session.rounds.size)} دست باقی مانده")
            return
        }
        session.revealed = true
        val (a, b) = teamTotals(session)
        if (a != b) { finishSession(session); return }
        val chooseExtra: (Int) -> Unit = { games ->
            session.menfiHandsTarget += games * settings.menfiHands
            autoSave(session); showMenfi(session)
        }
        when (settings.menfiTieExtraMode) {
            2 -> chooseExtra(2)
            3 -> chooseExtra(3)
            else -> AlertDialog.Builder(this).setTitle("مساوی شد")
                .setMessage("برای ادامه ۲ بازی یا ۳ بازی جدید اضافه شود؟ هر بازی با همان تعداد دست تنظیم‌شده محاسبه می‌شود.")
                .setPositiveButton("۲ بازی") { _, _ -> chooseExtra(2) }
                .setNegativeButton("۳ بازی") { _, _ -> chooseExtra(3) }
                .show()
        }
    }

    private fun showHezartaii(session: V3Session) {
        backAction = { askLeave(session) }
        val page = page()
        page.addView(toolbar("داوری هزارتایی") { askLeave(session) })
        page.addView(hero(R.drawable.royal_hero_hezartaii, "هزارتایی", "دور ${fa(session.rounds.size + 1)} از ${fa(settings.hezartaiiRounds)}"))
        val hide = settings.hezartaiiHideUntilEnd && !session.finished
        page.addView(individualRanking(session, hide))
        page.addView(section("امتیاز این دور"))
        page.addView(infoPanel("جریمهٔ صفر: ${signed(settings.hezartaiiZeroPenalty)}", "اگر بازیکن صفر وارد شود، جریمه خودکار جایگزین می‌شود."))
        page.addView(gap(10))
        val inputs = mutableListOf<EditText>()
        session.players.forEach { player ->
            val input = numericInput(player.name, "0", true); inputs += input
            page.addView(panel(true).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(AvatarCropView(this@V3Activity, player.avatar), LinearLayout.LayoutParams(dp(58), dp(68)))
                addView(gapHorizontal(8)); addView(text(player.name, 14f, RoyalPalette.cream, true), LinearLayout.LayoutParams(0, dp(50), 1f))
                addView(input, LinearLayout.LayoutParams(dp(112), dp(50)))
            }, spaced())
        }
        page.addView(primaryButton("ثبت دور") {
            val raw = inputs.map { toLatin(it.text.toString()).toIntOrNull() }
            if (raw.any { it == null }) { toast("امتیاز همه بازیکنان را وارد کنید"); return@primaryButton }
            val scores = raw.map { HezartaiiEngineV3.normalize(it!!, settings) }.toMutableList()
            session.rounds += V3Round(scores, "دور ${session.rounds.size + 1}")
            if (session.rounds.size >= settings.hezartaiiRounds) {
                session.finished = true; scope.launch { repo.save(session); showFinal(session) }
            } else { autoSave(session); showHezartaii(session) }
        })
        page.addView(roundHistory(session))
        page.addView(gap(12))
        page.addView(secondaryButton("پایان زودتر و محاسبه نتیجه") { finishSession(session) })
        render(page)
    }

    private fun roundHistory(session: V3Session): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(section("دست‌ها / دورهای ثبت‌شده"))
        if (session.rounds.isEmpty()) addView(text("هنوز چیزی ثبت نشده است.", 13f, RoyalPalette.muted, false, Gravity.CENTER))
        session.rounds.forEachIndexed { index, round ->
            addView(panel(true).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(9), dp(9), dp(9), dp(9))
                addView(text(fa(index + 1), 14f, RoyalPalette.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(30), dp(40)))
                val scoreText = round.scores.joinToString(" | ") { signed(it) }
                addView(text("$scoreText\n${round.note}", 13f, RoyalPalette.cream, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(smallButton("ویرایش") { editRound(session, index) })
                addView(gapHorizontal(5))
                addView(smallButton("حذف", true) { deleteRound(session, index) })
            }, spaced())
        }
    }

    private fun editRound(session: V3Session, index: Int) {
        val before = session.rounds[index].copy(scores = session.rounds[index].scores.toMutableList(), meta = session.rounds[index].meta.toMutableMap())
        val inputs = before.scores.map { numericInput("امتیاز", it.toString(), true) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(8))
            inputs.forEachIndexed { i, e ->
                val name = if (session.game == V3GameType.HEZARTAII) session.players.getOrNull(i)?.name ?: "بازیکن ${i + 1}" else if (i == 0) session.teamA?.name ?: "گروه اول" else session.teamB?.name ?: "گروه دوم"
                addView(text(name, 12f, RoyalPalette.muted)); addView(e); addView(gap(6))
            }
        }
        AlertDialog.Builder(this).setTitle("اصلاح رکورد ${fa(index + 1)}").setView(body)
            .setPositiveButton("ذخیره") { _, _ ->
                val values = inputs.map { toLatin(it.text.toString()).toIntOrNull() }
                if (values.any { it == null }) { toast("عدد نامعتبر است"); return@setPositiveButton }
                val after = before.copy(scores = values.map { it!! }.toMutableList(), note = before.note + " • اصلاح‌شده", editedAt = System.currentTimeMillis())
                session.rounds[index] = after
                scope.launch { repo.addAudit(session.id, index, "EDIT", before, after); repo.save(session); showLive(session) }
            }.setNegativeButton("انصراف", null).show()
    }

    private fun deleteRound(session: V3Session, index: Int) {
        confirm("حذف رکورد", "این رکورد حذف و مجموع‌ها دوباره محاسبه شود؟") {
            val before = session.rounds[index].copy(scores = session.rounds[index].scores.toMutableList(), meta = session.rounds[index].meta.toMutableMap())
            session.rounds.removeAt(index)
            scope.launch { repo.addAudit(session.id, index, "DELETE", before, null); repo.save(session); showLive(session) }
        }
    }

    private fun finishSession(session: V3Session) {
        if (session.rounds.isEmpty()) { toast("حداقل یک رکورد ثبت کنید"); return }
        session.finished = true; session.revealed = true
        scope.launch { repo.save(session); showFinal(session) }
    }

    private fun showFinal(session: V3Session) {
        backAction = { showHome() }
        val page = page()
        page.addView(toolbar("نتیجهٔ نهایی") { showHome() })
        if (session.game == V3GameType.HEZARTAII) {
            val ranking = HezartaiiEngineV3.ranking(session.players, session.rounds)
            val winner = ranking.firstOrNull()?.first?.name ?: "—"
            page.addView(hero(R.drawable.royal_hero_results, "برنده: $winner", "هزارتایی"))
            page.addView(individualRanking(session, false))
            page.addView(section("فاصله‌ها"))
            ScoreGapV3.individualGap(ranking).forEach { page.addView(text(it, 13f, RoyalPalette.cream, false, Gravity.CENTER)) }
        } else {
            val totals = teamTotals(session)
            val winner = when {
                totals.first == totals.second -> "مساوی"
                session.game == V3GameType.MENFI && totals.first < totals.second -> session.teamA!!.name
                session.game == V3GameType.MENFI -> session.teamB!!.name
                totals.first > totals.second -> session.teamA!!.name
                else -> session.teamB!!.name
            }
            page.addView(hero(R.drawable.royal_hero_results, "برنده: $winner", session.game.title))
            page.addView(teamScoreHeader(session, false))
            page.addView(gap(8)); page.addView(infoPanel("فاصله امتیاز", ScoreGapV3.teamGap(session.teamA!!.name, totals.first, session.teamB!!.name, totals.second)))
        }
        page.addView(roundHistory(session))
        page.addView(gap(14)); page.addView(primaryButton("بازگشت به خانه") { showHome() })
        render(page)
    }

    private fun showHistory() {
        backAction = { showHome() }
        val loading = page(); loading.addView(toolbar("تاریخچه") { showHome() }); loading.addView(infoPanel("در حال بارگذاری", "سوابق از پایگاه‌داده محلی Room خوانده می‌شود.")); render(loading)
        scope.launch {
            val history = repo.history()
            val page = page(); page.addView(toolbar("تاریخچه و بایگانی") { showHome() })
            if (history.isEmpty()) page.addView(infoPanel("تاریخچه خالی است", "بازی‌های نیمه‌تمام و تمام‌شده اینجا ذخیره می‌شوند."))
            history.forEach { session ->
                page.addView(panel().apply {
                    setPadding(dp(13), dp(13), dp(13), dp(13))
                    addView(text("${session.game.title} • ${if (session.finished) "پایان‌یافته" else "در جریان"}", 16f, RoyalPalette.gold, true))
                    val summary = if (session.game == V3GameType.HEZARTAII) "${fa(session.players.size)} بازیکن • ${fa(session.rounds.size)} دور" else {
                        val t = teamTotals(session); "${session.teamA!!.name}: ${signed(t.first)} | ${session.teamB!!.name}: ${signed(t.second)}"
                    }
                    addView(text(summary, 14f, RoyalPalette.cream, true)); addView(text(date(session.updatedAt), 11f, RoyalPalette.muted))
                    addView(gap(8))
                    addView(twoColumn(
                        smallButton(if (session.finished) "مشاهده" else "ادامه") { activeSession = session; if (session.finished) showFinal(session) else showLive(session) },
                        smallButton("گزارش تغییرات") { showAudit(session) }
                    ))
                    addView(gap(6)); addView(dangerButton("حذف این بازی") { confirm("حذف بازی", "این بازی برای همیشه حذف شود؟") { scope.launch { repo.deleteSession(session.id); showHistory() } } })
                }, spaced())
            }
            render(page)
        }
    }

    private fun showAudit(session: V3Session) {
        scope.launch {
            val logs = repo.audit(session.id)
            val page = page(); backAction = { showHistory() }
            page.addView(toolbar("گزارش تغییرات") { showHistory() })
            page.addView(infoPanel(session.game.title, "هر اصلاح یا حذف رکورد با زمان و نوع تغییر ثبت می‌شود."))
            if (logs.isEmpty()) page.addView(text("هنوز تغییری ثبت نشده است.", 13f, RoyalPalette.muted, false, Gravity.CENTER))
            logs.forEach { log -> page.addView(panel().apply {
                setPadding(dp(12), dp(10), dp(12), dp(10)); addView(text("${log.action} • رکورد ${fa(log.roundIndex + 1)}", 14f, RoyalPalette.gold, true)); addView(text(date(log.timestamp), 11f, RoyalPalette.muted))
            }, spaced()) }
            render(page)
        }
    }

    private fun askLeave(session: V3Session) {
        confirm("ذخیره و خروج", "بازی ذخیره می‌شود و بعداً از تاریخچه ادامه می‌دهید.") { autoSave(session); showHome() }
    }

    private fun autoSave(session: V3Session) { scope.launch { repo.save(session) } }

    private fun showTeams() {
        backAction = { showHome() }
        scope.launch {
            val teams = repo.teams(); val history = repo.history().filter { it.finished }
            val page = page(); page.addView(toolbar("مدیریت گروه‌ها") { showHome() })
            page.addView(hero(R.drawable.royal_hero_home, "گروه‌های ثابت", "نام، دو یار، آواتار و آمار"))
            page.addView(primaryButton("+ ساخت گروه جدید") { addTeamDialog() }); page.addView(gap(12))
            teams.forEach { team ->
                val relevant = history.filter { it.game != V3GameType.HEZARTAII && (it.teamA?.name == team.name || it.teamB?.name == team.name) }
                var wins = 0
                relevant.forEach { s ->
                    val (a, b) = teamTotals(s)
                    val won = if (s.game == V3GameType.MENFI) {
                        (s.teamA?.name == team.name && a < b) || (s.teamB?.name == team.name && b < a)
                    } else {
                        (s.teamA?.name == team.name && a > b) || (s.teamB?.name == team.name && b > a)
                    }
                    if (won) wins++
                }
                page.addView(panel(true).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10))
                    addView(AvatarCropView(this@V3Activity, team.avatar), LinearLayout.LayoutParams(dp(74), dp(86)))
                    addView(gapHorizontal(10)); addView(LinearLayout(this@V3Activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(team.name, 16f, RoyalPalette.paleGold, true)); addView(text("${team.member1} • ${team.member2}", 12f, RoyalPalette.muted)); addView(text("برد ${fa(wins)} از ${fa(relevant.size)} بازی", 12f, RoyalPalette.turquoise, true))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(smallButton("حذف", true) { confirm("حذف گروه", "گروه حذف شود؟ تاریخچه بازی‌ها باقی می‌ماند.") { scope.launch { repo.deleteTeam(team.id); showTeams() } } })
                }, spaced())
            }
            render(page)
        }
    }

    private fun addTeamDialog() {
        val name = input("نام گروه", "")
        val m1 = input("بازیکن اول", "")
        val m2 = input("بازیکن دوم", "")
        var avatar = 0
        val avatarView = AvatarCropView(this, avatar)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)); gravity = Gravity.CENTER
            avatarView.setOnClickListener { pickAvatar { avatar = it; avatarView.setAvatar(it) } }
            addView(avatarView, LinearLayout.LayoutParams(dp(100), dp(116))); addView(gap(8)); addView(name); addView(gap(6)); addView(m1); addView(gap(6)); addView(m2)
        }
        AlertDialog.Builder(this).setTitle("گروه جدید").setView(body).setPositiveButton("ذخیره") { _, _ ->
            val n = name.clean("")
            if (n.isBlank()) { toast("نام گروه لازم است"); return@setPositiveButton }
            scope.launch { repo.saveTeam(V3TeamEntity(name = n, member1 = m1.clean(""), member2 = m2.clean(""), avatar = avatar)); showTeams() }
        }.setNegativeButton("انصراف", null).show()
    }

    private fun showLeague() {
        backAction = { showHome() }
        val state = V3LeagueStore.load(this)
        if (state == null) showLeagueEmpty() else showLeagueState(state)
    }

    private fun showLeagueEmpty() {
        scope.launch {
            val teams = repo.teams()
            val page = page(); page.addView(toolbar("لیگ گروه‌ها") { showHome() })
            page.addView(hero(R.drawable.royal_hero_results, "لیگ دیوان", "۳ گروه یا بیشتر"))
            if (teams.size < 3) {
                page.addView(infoPanel("حداقل ۳ گروه لازم است", "ابتدا در مدیریت گروه‌ها سه گروه یا بیشتر بسازید.")); page.addView(gap(10)); page.addView(primaryButton("مدیریت گروه‌ها") { showTeams() })
            } else {
                page.addView(infoPanel("${fa(teams.size)} گروه آماده‌اند", "می‌توانید همه گروه‌های ذخیره‌شده را وارد لیگ کنید."))
                page.addView(gap(10)); page.addView(primaryButton("ساخت لیگ جدید") { chooseLeagueMode(teams) })
            }
            render(page)
        }
    }

    private fun chooseLeagueMode(teams: List<V3TeamEntity>) {
        AlertDialog.Builder(this).setTitle("نوع لیگ")
            .setItems(arrayOf("دوربین دوبه‌دو؛ هر مصاف تا ۲ برد", "تجمعی؛ تا N دور یا امتیاز هدف")) { _, which ->
                val state = V3LeagueState(
                    teams = teams.map { V3LeagueTeam(it.id, it.name, it.avatar) }.toMutableList(),
                    mode = if (which == 0) V3LeagueMode.BEST_OF_THREE else V3LeagueMode.CUMULATIVE
                )
                if (state.mode == V3LeagueMode.BEST_OF_THREE) state.rebuildMatches()
                V3LeagueStore.save(this, state); showLeagueState(state)
            }.show()
    }

    private fun showLeagueState(state: V3LeagueState) {
        backAction = { showHome() }
        val page = page(); page.addView(toolbar(state.name) { showHome() })
        page.addView(hero(R.drawable.royal_hero_results, "جدول لیگ", if (state.mode == V3LeagueMode.BEST_OF_THREE) "مصاف‌های دوبه‌دو تا دو برد" else "تجمعی تا سقف"))
        page.addView(section("رتبه‌بندی"))
        state.ranking().forEachIndexed { index, t ->
            val value = if (state.mode == V3LeagueMode.BEST_OF_THREE) "برد مصاف ${fa(t.matchWins)} • برد بازی ${fa(t.gameWins)}" else "${signed(t.cumulativeScore)} امتیاز"
            page.addView(panel(true).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(9), dp(9), dp(9), dp(9)); addView(text(fa(index + 1), 18f, RoyalPalette.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(38), dp(44))); addView(AvatarCropView(this@V3Activity, t.avatar), LinearLayout.LayoutParams(dp(52), dp(60))); addView(gapHorizontal(8)); addView(text("${t.name}\n$value", 13f, RoyalPalette.cream, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }, spaced())
        }
        if (state.mode == V3LeagueMode.BEST_OF_THREE) {
            page.addView(section("مصاف‌ها"))
            state.matches.forEachIndexed { index, m ->
                val a = state.teams.first { it.id == m.teamAId }; val b = state.teams.first { it.id == m.teamBId }
                page.addView(panel().apply {
                    setPadding(dp(10), dp(10), dp(10), dp(10)); addView(text("${a.name} ${fa(m.winsA)} — ${fa(m.winsB)} ${b.name}", 15f, RoyalPalette.paleGold, true, Gravity.CENTER))
                    if (!m.completed) {
                        addView(gap(6)); addView(twoColumn(
                            smallButton("برد ${a.name}") { state.registerMatchGame(index, a.id); V3LeagueStore.save(this@V3Activity, state); showLeagueState(state) },
                            smallButton("برد ${b.name}") { state.registerMatchGame(index, b.id); V3LeagueStore.save(this@V3Activity, state); showLeagueState(state) }
                        ))
                    } else addView(text("مصاف تمام شد", 12f, RoyalPalette.turquoise, true, Gravity.CENTER))
                }, spaced())
            }
        } else {
            page.addView(section("حالت تجمعی"))
            page.addView(infoPanel("دور ${fa(state.cumulativeRounds)} از ${fa(state.targetGames)}", "پایان با رسیدن یک گروه به ${fa(state.targetScore)} امتیاز یا تکمیل تعداد دورها؛ هرکدام زودتر."))
            if (!state.finished) {
                val inputs = state.teams.map { numericInput(it.name, "0", true) }
                inputs.forEachIndexed { i, e -> page.addView(twoColumn(text(state.teams[i].name, 13f, RoyalPalette.cream, true), e), spaced()) }
                page.addView(primaryButton("ثبت دور لیگ") {
                    val values = inputs.map { toLatin(it.text.toString()).toIntOrNull() }
                    if (values.any { it == null }) { toast("همه امتیازها را وارد کنید"); return@primaryButton }
                    state.registerCumulativeRound(values.map { it!! }); V3LeagueStore.save(this, state); showLeagueState(state)
                })
            }
        }
        if (state.finished) { page.addView(gap(10)); page.addView(infoPanel("لیگ پایان یافت", "قهرمان: ${state.ranking().firstOrNull()?.name ?: "—"}")) }
        page.addView(gap(12)); page.addView(dangerButton("حذف و ساخت لیگ جدید") { confirm("حذف لیگ", "اطلاعات لیگ فعلی پاک شود؟") { V3LeagueStore.clear(this); showLeague() } })
        render(page)
    }

    private fun showSettings() {
        backAction = { saveSettingsAndHome() }
        val page = page(); page.addView(toolbar("تنظیمات") { saveSettingsAndHome() })
        page.addView(hero(R.drawable.royal_hero_settings, "تنظیمات دیوان", "تمام اعداد قابل تغییرند"))
        page.addView(section("عمومی"))
        page.addView(switchSetting("بازخورد لمسی", settings.haptic) { settings.haptic = it })
        page.addView(switchSetting("روشن ماندن صفحه", settings.keepScreenAwake) { settings.keepScreenAwake = it; applyWindowSettings() })
        page.addView(switchSetting("اعداد فارسی", settings.persianDigits) { settings.persianDigits = it })
        page.addView(switchSetting("متن درشت", settings.largeText) { settings.largeText = it })

        page.addView(section("شلم"))
        page.addView(numberSetting("امتیاز هدف", settings.shalamTarget, 100, 5000, 10) { settings.shalamTarget = it })
        page.addView(numberSetting("حداقل عدد حراج", settings.shalamMinBid, 5, 160, 5) { settings.shalamMinBid = it })
        page.addView(switchSetting("بازی با جوکر", settings.shalamWithJoker) { settings.shalamWithJoker = it })
        page.addView(switchSetting("برد فقط به اندازه تعهد", settings.shalamAwardContractOnly) { settings.shalamAwardContractOnly = it })
        page.addView(numberSetting("ضریب شلم", settings.shalamMultiplier, 1, 5, 1) { settings.shalamMultiplier = it })
        page.addView(switchSetting("قانون یاسا", settings.shalamYasaEnabled) { settings.shalamYasaEnabled = it })
        page.addView(numberSetting("آستانه یاسا", settings.shalamYasaThreshold, 0, 165, 5) { settings.shalamYasaThreshold = it })
        page.addView(switchSetting("سرشلم", settings.shalamSarShalamEnabled) { settings.shalamSarShalamEnabled = it })

        page.addView(section("منفی / حکم ثابت"))
        page.addView(numberSetting("تعداد دست هر بازی", settings.menfiHands, 1, 30, 1) { settings.menfiHands = it })
        page.addView(numberSetting("حداقل اعلام", settings.menfiMinBid, 1, 10, 1) { settings.menfiMinBid = it })
        page.addView(numberSetting("امتیاز پایه برد", settings.menfiBaseWin, -100, 200, 5) { settings.menfiBaseWin = it })
        page.addView(numberSetting("امتیاز پایه باخت", settings.menfiBaseLoss, -200, 100, 5) { settings.menfiBaseLoss = it })
        page.addView(numberSetting("افزایش پله‌ای برد", settings.menfiWinStep, 0, 100, 1) { settings.menfiWinStep = it })
        page.addView(numberSetting("افزایش پله‌ای جریمه", settings.menfiLossStep, 0, 100, 1) { settings.menfiLossStep = it })
        page.addView(numberSetting("برد اعلام ۱۱", settings.menfiWin11, -500, 500, 5) { settings.menfiWin11 = it })
        page.addView(numberSetting("باخت اعلام ۱۱", settings.menfiLoss11, -500, 500, 5) { settings.menfiLoss11 = it })
        page.addView(numberSetting("برد اعلام ۱۲", settings.menfiWin12, -500, 500, 5) { settings.menfiWin12 = it })
        page.addView(numberSetting("باخت اعلام ۱۲", settings.menfiLoss12, -500, 500, 5) { settings.menfiLoss12 = it })
        page.addView(numberSetting("برد اعلام ۱۳", settings.menfiWin13, -500, 500, 5) { settings.menfiWin13 = it })
        page.addView(numberSetting("باخت اعلام ۱۳", settings.menfiLoss13, -500, 500, 5) { settings.menfiLoss13 = it })
        page.addView(cycleSetting("نمایش امتیاز منفی", arrayOf("پنهان تا دکمه نمایش", "فقط داور", "نمایش زنده"), settings.menfiHideMode) { settings.menfiHideMode = it })
        page.addView(cycleSetting("تساوی", arrayOf("پرسش در لحظه", "۲ بازی جدید", "۳ بازی جدید"), when (settings.menfiTieExtraMode) { 2 -> 1; 3 -> 2; else -> 0 }) {
            settings.menfiTieExtraMode = when (it) { 1 -> 2; 2 -> 3; else -> 0 }
        })

        page.addView(section("هزارتایی"))
        page.addView(numberSetting("حداقل بازیکن", settings.hezartaiiMinPlayers, 5, 30, 1) { settings.hezartaiiMinPlayers = it })
        page.addView(numberSetting("تعداد دور", settings.hezartaiiRounds, 1, 50, 1) { settings.hezartaiiRounds = it })
        page.addView(numberSetting("جریمه صفر", settings.hezartaiiZeroPenalty, -500, 0, 10) { settings.hezartaiiZeroPenalty = it })
        page.addView(switchSetting("پنهان‌کردن امتیاز تا پایان", settings.hezartaiiHideUntilEnd) { settings.hezartaiiHideUntilEnd = it })

        page.addView(section("پشتیبان‌گیری و انتقال"))
        page.addView(secondaryButton("خروجی JSON از همه داده‌ها") { exportBackup() })
        page.addView(gap(8)); page.addView(secondaryButton("بازیابی از فایل JSON") { importBackup() })
        page.addView(gap(16)); page.addView(primaryButton("ذخیره تنظیمات") { saveSettingsAndHome() })
        render(page)
    }

    private fun saveSettingsAndHome() { V3SettingsStore.save(this, settings); applyWindowSettings(); showHome() }

    private fun exportBackup() {
        scope.launch {
            pendingExport = repo.exportJson(settings)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "divan-emtiaz-backup-v3.json") }
            startActivityForResult(intent, EXPORT_REQUEST)
        }
    }

    private fun importBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }
        startActivityForResult(intent, IMPORT_REQUEST)
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
                    .onSuccess { toast("بکاپ ذخیره شد") }.onFailure { toast("خطا در ذخیره بکاپ") }
                pendingExport = null
            }
            IMPORT_REQUEST -> {
                scope.launch {
                    runCatching {
                        val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("فایل خالی است")
                        repo.importJson(raw)
                    }.onSuccess { imported -> settings = imported; V3SettingsStore.save(this@V3Activity, settings); toast("بازیابی کامل شد"); showHome() }
                        .onFailure { toast("بازیابی ناموفق: ${it.message ?: "فایل نامعتبر"}") }
                }
            }
        }
    }

    private fun teamTotals(session: V3Session): Pair<Int, Int> =
        session.rounds.sumOf { it.scores.getOrElse(0) { 0 } } to session.rounds.sumOf { it.scores.getOrElse(1) { 0 } }

    private fun teamScoreHeader(session: V3Session, hidden: Boolean): View {
        val (a, b) = teamTotals(session)
        return twoColumn(
            scoreCard(session.teamA!!.name, session.teamA!!.avatar, if (hidden) "•••" else signed(a), RoyalPalette.green),
            scoreCard(session.teamB!!.name, session.teamB!!.avatar, if (hidden) "•••" else signed(b), RoyalPalette.crimson)
        )
    }

    private fun individualRanking(session: V3Session, hidden: Boolean): View = panel().apply {
        setPadding(dp(10), dp(10), dp(10), dp(10))
        addView(text("رتبه‌بندی", 17f, RoyalPalette.gold, true, Gravity.CENTER))
        if (hidden) addView(text("امتیازها تا پایان پنهان هستند.", 13f, RoyalPalette.muted, false, Gravity.CENTER))
        else {
            HezartaiiEngineV3.ranking(session.players, session.rounds).forEachIndexed { index, pair ->
                addView(LinearLayout(this@V3Activity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(5), dp(4), dp(5))
                    addView(text(fa(index + 1), 14f, RoyalPalette.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(32), dp(40)))
                    addView(AvatarCropView(this@V3Activity, pair.first.avatar), LinearLayout.LayoutParams(dp(44), dp(52)))
                    addView(gapHorizontal(8)); addView(text(pair.first.name, 13f, RoyalPalette.cream, true), LinearLayout.LayoutParams(0, dp(44), 1f)); addView(text(signed(pair.second), 15f, RoyalPalette.paleGold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(86), dp(44)))
                })
            }
        }
    }

    private fun scoreCard(name: String, avatar: Int, score: String, fill: Int): View = panel().apply {
        gravity = Gravity.CENTER; background = royalShape(fill, RoyalPalette.gold, 18f, 1, this@V3Activity); setPadding(dp(8), dp(9), dp(8), dp(9))
        addView(AvatarCropView(this@V3Activity, avatar), LinearLayout.LayoutParams(dp(62), dp(72))); addView(gap(4)); addView(text(name, 13f, RoyalPalette.cream, true, Gravity.CENTER)); addView(text(score, 24f, RoyalPalette.paleGold, true, Gravity.CENTER))
    }

    private fun pickAvatar(onSelected: (Int) -> Unit) {
        val grid = GridLayout(this).apply { columnCount = 4; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val dialog = AlertDialog.Builder(this).setTitle("انتخاب آواتار سه‌بعدی").setView(grid).setNegativeButton("بستن", null).create()
        repeat(16) { index -> grid.addView(AvatarCropView(this, index).apply { setOnClickListener { onSelected(index); dialog.dismiss() } }, GridLayout.LayoutParams().apply { width = dp(72); height = dp(86); setMargins(dp(3), dp(3), dp(3), dp(3)) }) }
        dialog.show()
    }

    private fun hero(resource: Int, title: String, subtitle: String): View = FrameLayout(this).apply {
        background = royalShape(RoyalPalette.navy, RoyalPalette.gold, 22f, 1, this@V3Activity)
        addView(AtlasCropView(this@V3Activity, resource, Rect(0, 0, 640, 400), 22f, false), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(View(this@V3Activity).apply { setBackgroundColor(Color.argb(132, 2, 15, 31)) }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(LinearLayout(this@V3Activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(18), dp(18), dp(18), dp(18)); addView(text("♛", 38f, RoyalPalette.paleGold, true, Gravity.CENTER)); addView(text(title, 28f, RoyalPalette.paleGold, true, Gravity.CENTER)); addView(text(subtitle, 13f, RoyalPalette.cream, false, Gravity.CENTER))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply { bottomMargin = dp(14) } }

    private fun toolbar(title: String, back: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(smallButton("›") { back() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(text(title, 21f, RoyalPalette.paleGold, true, Gravity.CENTER), LinearLayout.LayoutParams(0, dp(58), 1f))
        addView(text("◆", 17f, RoyalPalette.turquoise, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(48), dp(48)))
    }

    private fun section(title: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; addView(gap(12)); addView(RoyalDivider(this@V3Activity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18))); addView(text(title, 17f, RoyalPalette.gold, true, Gravity.CENTER)); addView(gap(8))
    }

    private fun infoPanel(title: String, subtitle: String): View = panel().apply {
        setPadding(dp(13), dp(12), dp(13), dp(12)); addView(text(title, 15f, RoyalPalette.paleGold, true, Gravity.CENTER)); addView(gap(3)); addView(text(subtitle, 12f, RoyalPalette.muted, false, Gravity.CENTER).apply { maxLines = 5 })
    }

    private fun switchSetting(title: String, checked: Boolean, changed: (Boolean) -> Unit): View = panel(true).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); addView(text(title, 14f, RoyalPalette.cream, true), LinearLayout.LayoutParams(0, dp(48), 1f)); addView(Switch(this@V3Activity).apply { isChecked = checked; setOnCheckedChangeListener { _, value -> changed(value) } })
    }

    private fun numberSetting(title: String, value: Int, min: Int, max: Int, step: Int, changed: (Int) -> Unit): View {
        val state = intArrayOf(value)
        val label = text(fa(value), 17f, RoyalPalette.paleGold, true, Gravity.CENTER)
        fun refresh() { label.text = fa(state[0]); changed(state[0]) }
        return panel().apply {
            setPadding(dp(12), dp(10), dp(12), dp(10)); addView(text(title, 14f, RoyalPalette.cream, true)); addView(gap(6)); addView(LinearLayout(this@V3Activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                addView(smallButton("−") { state[0] = (state[0] - step).coerceAtLeast(min); refresh() }); addView(label, LinearLayout.LayoutParams(dp(100), dp(44))); addView(smallButton("+") { state[0] = (state[0] + step).coerceAtMost(max); refresh() })
            })
        }
    }

    private fun cycleSetting(title: String, options: Array<String>, initial: Int, changed: (Int) -> Unit): View {
        var index = initial.coerceIn(0, options.lastIndex)
        val value = text(options[index], 13f, RoyalPalette.paleGold, true, Gravity.CENTER)
        return panel(true).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); addView(text(title, 14f, RoyalPalette.cream, true), LinearLayout.LayoutParams(0, dp(48), 1f)); addView(value, LinearLayout.LayoutParams(dp(160), dp(48))); setOnClickListener { index = (index + 1) % options.size; value.text = options[index]; changed(index) }
        }
    }

    private fun panel(horizontal: Boolean = false): LinearLayout = LinearLayout(this).apply {
        orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL; background = royalShape(Color.argb(238, 6, 31, 55), RoyalPalette.gold, 17f, 1, this@V3Activity)
    }

    private fun twoColumn(first: View, second: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; addView(first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(gapHorizontal(8)); addView(second, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(34)); layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun render(page: View) {
        val root = FrameLayout(this).apply { setBackgroundColor(RoyalPalette.midnight) }
        root.addView(ImageView(this).apply { setImageResource(R.drawable.royal_background); scaleType = ImageView.ScaleType.CENTER_CROP; alpha = .5f }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply { setBackgroundColor(Color.argb(92, 1, 12, 26)) }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(page) }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, gravityValue: Int = Gravity.RIGHT): TextView = TextView(this).apply {
        text = value; textSize = size + if (settings.largeText) 1f else 0f; setTextColor(color); gravity = gravityValue; preparePersianText(this@V3Activity, bold); maxLines = 5; setPadding(dp(3), dp(2), dp(3), dp(2))
    }

    private fun baseButton(title: String, selected: Boolean, danger: Boolean, action: () -> Unit): Button = Button(this).apply {
        text = title; isAllCaps = false; textSize = 14f; minHeight = dp(50); maxLines = 2; preparePersianText(this@V3Activity, true); gravity = Gravity.CENTER
        setTextColor(if (selected) RoyalPalette.midnight else RoyalPalette.cream); background = if (danger) royalShape(RoyalPalette.crimson, RoyalPalette.gold, 15f, 1, this@V3Activity) else royalGradient(this@V3Activity, selected)
        setPadding(dp(8), dp(3), dp(8), dp(3)); setOnClickListener { tap(it); action() }
    }

    private fun primaryButton(title: String, action: () -> Unit) = baseButton(title, true, false, action)
    private fun secondaryButton(title: String, action: () -> Unit) = baseButton(title, false, false, action)
    private fun dangerButton(title: String, action: () -> Unit) = baseButton(title, false, true, action)
    private fun compactButton(title: String, action: () -> Unit) = baseButton(title, false, false, action).apply { textSize = 12f; minWidth = 0; minimumWidth = 0 }
    private fun smallButton(title: String, danger: Boolean = false, action: () -> Unit) = baseButton(title, false, danger, action).apply { textSize = 11f; minWidth = dp(48); minimumWidth = dp(48) }
    private fun choiceButton(title: String, selected: Boolean) = baseButton(title, selected, false) { }.also { styleChoice(it, selected) }
    private fun styleChoice(button: Button, selected: Boolean) { button.background = royalGradient(this, selected); button.setTextColor(if (selected) RoyalPalette.midnight else RoyalPalette.cream) }

    private fun input(hint: String, initial: String): EditText = EditText(this).apply {
        this.hint = hint; setText(initial); setTextColor(RoyalPalette.cream); setHintTextColor(RoyalPalette.muted); textSize = 14f; setSingleLine(true); preparePersianText(this@V3Activity); background = royalShape(Color.rgb(4, 24, 45), RoyalPalette.gold, 13f, 1, this@V3Activity); setPadding(dp(10), dp(4), dp(10), dp(4)); minHeight = dp(50)
    }

    private fun numericInput(hint: String, initial: String, signed: Boolean): EditText = input(hint, initial).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0; gravity = Gravity.CENTER; textDirection = View.TEXT_DIRECTION_LTR
    }

    private fun EditText.clean(fallback: String): String = text.toString().trim().ifBlank { fallback }
    private fun gap(value: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(value)) }
    private fun gapHorizontal(value: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(value), 1) }
    private fun spaced() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun tap(view: View) { if (settings.haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun fa(value: Any): String = PersianText.digits(value, settings.persianDigits)
    private fun signed(value: Int): String = PersianText.signed(value, settings.persianDigits)
    private fun toLatin(value: String): String { var result = value; "۰۱۲۳۴۵۶۷۸۹".forEachIndexed { index, char -> result = result.replace(char, '0' + index) }; return result }
    private fun date(timestamp: Long): String = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa", "IR")).format(Date(timestamp))

    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("تأیید") { _, _ -> action() }.setNegativeButton("انصراف", null).show()
    }
}
