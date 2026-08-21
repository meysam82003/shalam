package com.meysam.divanemtiaz

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var settings: AppSettings
    private var currentScreen = "home"
    private var activeSession: GameSession? = null
    private var backAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = GameStore.loadSettings(this)
        window.statusBarColor = RoyalPalette.midnight
        window.navigationBarColor = RoyalPalette.midnight
        applyWindowSettings()
        showHome()
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
        currentScreen = "home"
        activeSession = null
        backAction = null
        val page = page()
        page.addView(hero(
            R.drawable.royal_hero_home,
            Rect(0, 0, 640, 400),
            "دیوان امتیاز",
            "شلم • منفی • هزارتایی"
        ))
        page.addView(section("انتخاب بازی"))
        GameType.values().forEach { game -> page.addView(gameCard(game), spaced()) }
        page.addView(gap(10))
        page.addView(primaryButton("شروع بازی") { showGameSelection() })
        page.addView(gap(10))
        page.addView(secondaryButton("تاریخچه و ادامهٔ بازی‌ها") { showHistory() })
        page.addView(gap(10))
        page.addView(secondaryButton("تنظیمات حرفه‌ای") { showSettings() })
        page.addView(gap(26))
        page.addView(text("نسخهٔ ۲.۰.۰  •  آفلاین  •  بدون تبلیغ", 12f, RoyalPalette.muted, false, Gravity.CENTER))
        render(page)
    }

    private fun showGameSelection() {
        currentScreen = "games"
        backAction = { showHome() }
        val page = page()
        page.addView(toolbar("انتخاب بازی") { showHome() })
        page.addView(text("نوع داوری را انتخاب کنید", 14f, RoyalPalette.muted, false, Gravity.CENTER))
        page.addView(gap(14))
        GameType.values().forEach { game -> page.addView(gameCard(game), spaced()) }
        render(page)
    }

    private fun gameCard(game: GameType): View = panel().apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(14))
        addView(GameArtView(this@MainActivity, game), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(136)
        ))
        addView(gap(10))
        addView(text(game.title, 23f, RoyalPalette.paleGold, true, Gravity.CENTER))
        addView(text(game.subtitle, 13f, RoyalPalette.muted, false, Gravity.CENTER))
        setOnClickListener { tap(it); showSetup(game) }
    }

    private fun showSetup(game: GameType) {
        currentScreen = "setup"
        backAction = { showGameSelection() }
        var avatarA = 0
        var avatarB = 3
        val page = page()
        page.addView(toolbar("آماده‌سازی ${game.title}") { showGameSelection() })
        page.addView(text(
            if (game == GameType.HEZARTAII) "نام بازیکنان را وارد کنید" else "نام و نشان دو تیم را تنظیم کنید",
            14f, RoyalPalette.muted, false, Gravity.CENTER
        ))
        page.addView(gap(16))

        val nameA = input(if (game == GameType.HEZARTAII) "بازیکن اول" else "تیم اول", "شیران پارس")
        val avatarViewA = AvatarCropView(this, avatarA)
        page.addView(teamEditor(if (game == GameType.HEZARTAII) "بازیکن اول" else "تیم اول", nameA, avatarViewA) {
            pickAvatar { index -> avatarA = index; avatarViewA.setAvatar(index) }
        })
        page.addView(gap(12))

        val nameB = input(if (game == GameType.HEZARTAII) "بازیکن دوم" else "تیم دوم", "پارس‌بانان")
        val avatarViewB = AvatarCropView(this, avatarB)
        page.addView(teamEditor(if (game == GameType.HEZARTAII) "بازیکن دوم" else "تیم دوم", nameB, avatarViewB) {
            pickAvatar { index -> avatarB = index; avatarViewB.setAvatar(index) }
        })
        page.addView(gap(18))
        page.addView(primaryButton("شروع داوری ${game.title}") {
            val session = GameSession(
                game,
                nameA.text.toString().trim().ifBlank { "تیم اول" },
                nameB.text.toString().trim().ifBlank { "تیم دوم" },
                avatarA,
                avatarB
            )
            activeSession = session
            showLiveGame(session)
        })
        render(page)
    }

    private fun teamEditor(title: String, name: EditText, avatar: AvatarCropView, choose: () -> Unit): View = panel(true).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        avatar.setOnClickListener { choose() }
        addView(avatar, LinearLayout.LayoutParams(dp(92), dp(108)))
        addView(gapHorizontal(12))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 13f, RoyalPalette.gold, true))
            addView(gap(5))
            addView(name)
            addView(gap(6))
            addView(text("برای تغییر آواتار، تصویر را لمس کنید", 11f, RoyalPalette.muted))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun pickAvatar(onSelected: (Int) -> Unit) {
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(RoyalPalette.navy)
        }
        val dialog = AlertDialog.Builder(this).setTitle("انتخاب آواتار سه‌بعدی").setView(grid).setNegativeButton("بستن", null).create()
        repeat(16) { index ->
            grid.addView(AvatarCropView(this, index).apply {
                setOnClickListener { onSelected(index); dialog.dismiss() }
            }, GridLayout.LayoutParams().apply {
                width = dp(72); height = dp(86); setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        dialog.show()
    }

    private fun showLiveGame(session: GameSession) {
        activeSession = session
        when (session.game) {
            GameType.SHALAM -> showShalam(session)
            GameType.MENFI -> showMenfi(session)
            GameType.HEZARTAII -> showHezartaii(session)
        }
    }

    private fun showShalam(session: GameSession) {
        currentScreen = "shalam"
        backAction = { askLeave(session) }
        val page = page()
        page.addView(toolbar("داوری زندهٔ شلم") { askLeave(session) })
        page.addView(scoreHeader(session))
        page.addView(section("دست ${fa(session.rounds.size + 1)} — تیم حاکم"))
        var contractTeam = 1
        val teamAButton = choiceButton(session.teamA, true)
        val teamBButton = choiceButton(session.teamB, false)
        val contractRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(teamAButton, LinearLayout.LayoutParams(0, dp(56), 1f))
            addView(gapHorizontal(8))
            addView(teamBButton, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        fun selectTeam(team: Int) {
            contractTeam = team
            styleChoice(teamAButton, team == 1)
            styleChoice(teamBButton, team == 2)
        }
        teamAButton.setOnClickListener { selectTeam(1) }
        teamBButton.setOnClickListener { selectTeam(2) }
        page.addView(contractRow)
        page.addView(section("امتیاز خوانده‌شده"))
        page.addView(text("پاس یا عدد آماده را انتخاب کنید؛ نتیجه در مرحلهٔ بعد ثبت می‌شود.", 12f, RoyalPalette.muted, false, Gravity.CENTER))
        page.addView(gap(8))
        val grid = GridLayout(this).apply { columnCount = 4 }
        ShalamEngine.readyBids.forEach { bid ->
            grid.addView(compactButton(bid.title) {
                if (bid.kind == ShalamBidKind.PASS) {
                    session.rounds.add(ScoreRound(0, 0, "پاس", contractTeam = contractTeam, contract = bid.title))
                    autoSave(session)
                    showShalam(session)
                } else showShalamResult(session, contractTeam, bid)
            }, GridLayout.LayoutParams().apply {
                width = 0; height = dp(54); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        page.addView(grid)
        page.addView(roundHistory(session))
        page.addView(gap(12))
        page.addView(secondaryButton("پایان بازی و ثبت نتیجه") { finishGame(session) })
        render(page)
    }

    private fun showShalamResult(session: GameSession, contractTeam: Int, bid: ShalamBid) {
        currentScreen = "shalam_result"
        backAction = { showShalam(session) }
        val page = page()
        page.addView(toolbar("ثبت نتیجهٔ دست") { showShalam(session) })
        page.addView(hero(R.drawable.royal_hero_shalam, Rect(0, 0, 640, 400), "محاسبهٔ شلم", "تعهد، گرفتهٔ حریف، نتیجه"))
        page.addView(scoreHeader(session))
        page.addView(section("تعهد ${fa(bid.title)} — ${if (contractTeam == 1) session.teamA else session.teamB}"))
        page.addView(infoPanel("امتیاز تیم حریف را وارد کنید", "امتیاز تیم حاکم به‌صورت خودکار از ۱۶۵ محاسبه می‌شود."))
        page.addView(gap(12))
        val opponentInput = numericInput("امتیاز حریف از ۰ تا ۱۶۵", "0", signed = false)
        val actual = text("امتیاز واقعی حاکم: ${fa(165)}", 17f, RoyalPalette.cream, true, Gravity.CENTER)
        val preview = text("نتیجهٔ قابل ثبت", 21f, RoyalPalette.paleGold, true, Gravity.CENTER)
        val state = intArrayOf(0)
        fun update(value: Int) {
            val safe = value.coerceIn(0, 165)
            state[0] = safe
            val result = ShalamEngine.calculate(bid, safe, settings.shalamAwardContractOnly, settings.shalamValue)
            actual.text = "امتیاز واقعی حاکم: ${fa(result.actualContractPoints)} = ${fa(165)} − ${fa(safe)}"
            val contractName = if (contractTeam == 1) session.teamA else session.teamB
            val opponentName = if (contractTeam == 1) session.teamB else session.teamA
            preview.text = "$contractName: ${signed(result.contractScore)}   |   $opponentName: ${signed(result.opponentScore)}"
            preview.setTextColor(if (result.succeeded) RoyalPalette.turquoise else RoyalPalette.danger)
        }
        opponentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                update(toLatin(s?.toString().orEmpty()).toIntOrNull() ?: 0)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        page.addView(opponentInput)
        page.addView(gap(10))
        page.addView(stepper("تنظیم سریع امتیاز حریف", state, 0, 165, 5) {
            opponentInput.setText(it.toString()); opponentInput.setSelection(opponentInput.length())
        })
        page.addView(gap(14))
        page.addView(panel().apply {
            setPadding(dp(14), dp(14), dp(14), dp(14)); gravity = Gravity.CENTER
            addView(actual); addView(gap(6)); addView(preview)
        })
        page.addView(gap(16))
        page.addView(primaryButton("تأیید و ثبت این دست") {
            val result = ShalamEngine.calculate(bid, state[0], settings.shalamAwardContractOnly, settings.shalamValue)
            val round = if (contractTeam == 1) {
                ScoreRound(result.contractScore, result.opponentScore, "تعهد ${bid.title}", result.actualContractPoints, state[0], 1, bid.title)
            } else {
                ScoreRound(result.opponentScore, result.contractScore, "تعهد ${bid.title}", state[0], result.actualContractPoints, 2, bid.title)
            }
            session.rounds.add(round)
            autoSave(session)
            showShalam(session)
        })
        update(0)
        render(page)
    }

    private fun showMenfi(session: GameSession) {
        currentScreen = "menfi"
        backAction = { askLeave(session) }
        val page = page()
        page.addView(toolbar("داوری زندهٔ منفی") { askLeave(session) })
        page.addView(scoreHeader(session, settings.menfiHiddenUntilReveal))
        page.addView(section("دست ${fa(session.rounds.size + 1)} از ${fa(settings.menfiHands)}"))
        page.addView(infoPanel("ثبت عدد هر دو تیم", "اعداد آماده از ۳ تا ۱۳ هستند. نتیجه فقط بعد از انتخاب حالت نهایی ثبت می‌شود."))
        page.addView(gap(14))
        var numberA: Int? = null
        var numberB: Int? = null
        val statusA = text("ثبت نشده", 13f, RoyalPalette.muted, true, Gravity.CENTER)
        val statusB = text("ثبت نشده", 13f, RoyalPalette.muted, true, Gravity.CENTER)
        val aButton = secondaryButton("ثبت عدد ${session.teamA}") {
            pickMenfiNumber(session.teamA) { numberA = it; statusA.text = "ثبت شد ✓"; statusA.setTextColor(RoyalPalette.turquoise) }
        }
        val bButton = secondaryButton("ثبت عدد ${session.teamB}") {
            pickMenfiNumber(session.teamB) { numberB = it; statusB.text = "ثبت شد ✓"; statusB.setTextColor(RoyalPalette.turquoise) }
        }
        page.addView(twoColumn(
            panel().apply { gravity = Gravity.CENTER; addView(aButton); addView(gap(6)); addView(statusA) },
            panel().apply { gravity = Gravity.CENTER; addView(bButton); addView(gap(6)); addView(statusB) }
        ))
        page.addView(gap(16))
        page.addView(primaryButton("نمایش حالت‌های نتیجه") {
            if (numberA == null || numberB == null) toast("ابتدا عدد هر دو تیم را ثبت کنید")
            else showMenfiOutcomes(session, numberA!!, numberB!!)
        })
        page.addView(roundHistory(session))
        page.addView(gap(12))
        page.addView(secondaryButton("پایان بازی و ثبت نتیجه") { finishGame(session) })
        render(page)
    }

    private fun pickMenfiNumber(team: String, selected: (Int) -> Unit) {
        val grid = GridLayout(this).apply { columnCount = 4; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val dialog = AlertDialog.Builder(this).setTitle("عدد $team").setView(grid).setNegativeButton("انصراف", null).create()
        MenfiEngine.readyNumbers.forEach { number ->
            grid.addView(compactButton(fa(number)) { selected(number); dialog.dismiss() }, GridLayout.LayoutParams().apply {
                width = dp(68); height = dp(52); setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        dialog.show()
    }

    private fun showMenfiOutcomes(session: GameSession, numberA: Int, numberB: Int, editIndex: Int? = null) {
        currentScreen = "menfi_result"
        backAction = { showMenfi(session) }
        val page = page()
        page.addView(toolbar("انتخاب نتیجهٔ دست") { showMenfi(session) })
        page.addView(hero(R.drawable.royal_hero_menfi, Rect(0, 0, 640, 400), "نتیجهٔ پنهان", "عددها ثبت شدند"))
        page.addView(infoPanel(
            "${session.teamA}: ${fa(numberA)}   |   ${session.teamB}: ${fa(numberB)}",
            "حالت رخ‌داده را انتخاب کنید؛ تا پیش از تأیید هیچ امتیازی ثبت نمی‌شود."
        ))
        page.addView(gap(12))
        MenfiEngine.outcomes(numberA, numberB).forEachIndexed { index, outcome ->
            val detail = when (index) {
                0 -> "هر دو تیم گرفتند"
                1 -> "${session.teamA} گرفت؛ ${session.teamB} نگرفت"
                else -> "${session.teamA} نگرفت؛ ${session.teamB} گرفت"
            }
            page.addView(panel().apply {
                setPadding(dp(16), dp(14), dp(16), dp(14))
                addView(text("${signed(outcome.teamAScore)}   |   ${signed(outcome.teamBScore)}", 23f, RoyalPalette.paleGold, true, Gravity.CENTER))
                addView(text(detail, 13f, RoyalPalette.muted, false, Gravity.CENTER))
                setOnClickListener {
                    val round = ScoreRound(outcome.teamAScore, outcome.teamBScore, detail, numberA, numberB)
                    if (editIndex == null) session.rounds.add(round) else session.rounds[editIndex] = round
                    autoSave(session)
                    showMenfi(session)
                }
            }, spaced())
        }
        render(page)
    }

    private fun showHezartaii(session: GameSession) {
        currentScreen = "hezartaii"
        backAction = { askLeave(session) }
        val page = page()
        page.addView(toolbar("داوری هزارتایی") { askLeave(session) })
        page.addView(hero(R.drawable.royal_hero_hezartaii, Rect(0, 0, 640, 400), "هزارتایی", "رقابت امتیازی انفرادی"))
        page.addView(scoreHeader(session))
        page.addView(section("دور ${fa(session.rounds.size + 1)} از ${fa(settings.hezartaiiRounds)}"))
        page.addView(infoPanel("ثبت امتیاز بازیکنان", "صفر با جریمهٔ ${signed(settings.hezartaiiZeroPenalty)} ثبت می‌شود و امتیازهای منفی نیز مجازند."))
        page.addView(gap(12))
        val inputA = numericInput(session.teamA, "0", true)
        val inputB = numericInput(session.teamB, "0", true)
        page.addView(twoColumn(inputA, inputB))
        page.addView(gap(12))
        page.addView(primaryButton("ثبت امتیاز این دور") {
            val rawA = toLatin(inputA.text.toString()).toIntOrNull()
            val rawB = toLatin(inputB.text.toString()).toIntOrNull()
            if (rawA == null || rawB == null) toast("امتیاز هر دو بازیکن را وارد کنید")
            else {
                val a = if (rawA == 0) settings.hezartaiiZeroPenalty else rawA
                val b = if (rawB == 0) settings.hezartaiiZeroPenalty else rawB
                session.rounds.add(ScoreRound(a, b, "دور ${session.rounds.size + 1}", rawA, rawB))
                autoSave(session)
                showHezartaii(session)
            }
        })
        page.addView(roundHistory(session))
        page.addView(gap(12))
        page.addView(secondaryButton("پایان بازی و ثبت نتیجه") { finishGame(session) })
        render(page)
    }

    private fun scoreHeader(session: GameSession, hidden: Boolean = false): View {
        val scoreA = ScoreEngine.total(session.rounds, true)
        val scoreB = ScoreEngine.total(session.rounds, false)
        return twoColumn(
            scoreCard(session.teamA, session.avatarA, if (hidden) "•••" else signed(scoreA), RoyalPalette.green),
            scoreCard(session.teamB, session.avatarB, if (hidden) "•••" else signed(scoreB), RoyalPalette.crimson)
        )
    }

    private fun scoreCard(name: String, avatar: Int, score: String, fill: Int): View = panel().apply {
        gravity = Gravity.CENTER
        background = royalShape(fill, RoyalPalette.gold, 18f, 1, this@MainActivity)
        addView(AvatarCropView(this@MainActivity, avatar), LinearLayout.LayoutParams(dp(64), dp(72)))
        addView(gap(5))
        addView(text(name, 13f, RoyalPalette.cream, true, Gravity.CENTER).apply { maxLines = 2 })
        addView(text(score, 25f, RoyalPalette.paleGold, true, Gravity.CENTER))
    }

    private fun roundHistory(session: GameSession): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(section("دست‌های ثبت‌شده"))
        if (session.rounds.isEmpty()) {
            addView(text("هنوز دستی ثبت نشده است.", 13f, RoyalPalette.muted, false, Gravity.CENTER))
        } else session.rounds.forEachIndexed { index, round ->
            addView(panel(true).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                addView(text(fa(index + 1), 14f, RoyalPalette.gold, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(30), dp(38)))
                addView(text("${signed(round.teamA)}   |   ${signed(round.teamB)}\n${round.note}", 14f, RoyalPalette.cream, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(smallButton("ویرایش") {
                    if (session.game == GameType.MENFI && round.sourceA != null && round.sourceB != null) {
                        showMenfiOutcomes(session, round.sourceA, round.sourceB, index)
                    } else editRound(session, index)
                })
                addView(smallButton("حذف", true) {
                    confirm("حذف دست", "این دست از جمع امتیازها حذف شود؟") {
                        session.rounds.removeAt(index); autoSave(session); showLiveGame(session)
                    }
                })
            }, spaced())
        }
    }

    private fun editRound(session: GameSession, index: Int) {
        val round = session.rounds[index]
        val a = numericInput(session.teamA, round.teamA.toString(), true)
        val b = numericInput(session.teamB, round.teamB.toString(), true)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(4))
            addView(a); addView(gap(8)); addView(b)
        }
        AlertDialog.Builder(this).setTitle("ویرایش دست ${fa(index + 1)}").setView(body)
            .setPositiveButton("ذخیره") { _, _ ->
                val newA = toLatin(a.text.toString()).toIntOrNull()
                val newB = toLatin(b.text.toString()).toIntOrNull()
                if (newA != null && newB != null) {
                    session.rounds[index] = round.copy(teamA = newA, teamB = newB, note = "ویرایش‌شده")
                    autoSave(session); showLiveGame(session)
                }
            }.setNegativeButton("انصراف", null).show()
    }

    private fun finishGame(session: GameSession) {
        if (session.rounds.isEmpty()) { toast("حداقل یک دست ثبت کنید"); return }
        GameStore.saveSession(this, session, true)
        showFinalResult(session)
    }

    private fun showFinalResult(session: GameSession) {
        currentScreen = "result"
        backAction = { showHome() }
        val a = ScoreEngine.total(session.rounds, true)
        val b = ScoreEngine.total(session.rounds, false)
        val winner = when (ScoreEngine.winner(session.game, a, b)) { 1 -> session.teamA; 2 -> session.teamB; else -> "مساوی" }
        val page = page()
        page.addView(toolbar("نتیجهٔ نهایی") { showHome() })
        page.addView(hero(R.drawable.royal_hero_results, Rect(0, 0, 640, 400), "برنده: $winner", session.game.title))
        page.addView(twoColumn(
            scoreCard(session.teamA, session.avatarA, signed(a), RoyalPalette.green),
            scoreCard(session.teamB, session.avatarB, signed(b), RoyalPalette.crimson)
        ))
        page.addView(roundHistory(session))
        page.addView(gap(16))
        page.addView(primaryButton("بازگشت به خانه") { showHome() })
        render(page)
    }

    private fun askLeave(session: GameSession) {
        confirm("ذخیره و خروج", "بازی در تاریخچه ذخیره می‌شود و بعداً می‌توانید ادامه دهید.") {
            GameStore.saveSession(this, session, false); showHome()
        }
    }

    private fun autoSave(session: GameSession) = GameStore.saveSession(this, session, false)

    private fun showHistory() {
        currentScreen = "history"
        backAction = { showHome() }
        val history = GameStore.loadHistory(this)
        val page = page()
        page.addView(toolbar("تاریخچهٔ بازی‌ها") { showHome() })
        page.addView(hero(R.drawable.royal_hero_history, Rect(0, 0, 640, 400), "دیوان بازی‌ها", "ادامه، ویرایش و نتیجه"))
        if (history.isEmpty()) page.addView(infoPanel("تاریخچه خالی است", "پس از ثبت اولین دست، بازی شما اینجا ذخیره می‌شود."))
        history.forEach { record ->
            page.addView(panel().apply {
                setPadding(dp(14), dp(14), dp(14), dp(14))
                addView(text("${record.game.title}  •  ${if (record.finished) "پایان‌یافته" else "در جریان"}", 16f, RoyalPalette.gold, true))
                addView(text("${record.teamA}: ${signed(record.scoreA)}   |   ${record.teamB}: ${signed(record.scoreB)}", 17f, RoyalPalette.cream, true))
                addView(text("${fa(record.rounds.size)} دست  •  ${date(record.timestamp)}", 12f, RoyalPalette.muted))
                addView(gap(8))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(smallButton(if (record.finished) "مشاهده" else "ادامه") {
                        val session = GameStore.toSession(record); activeSession = session; showLiveGame(session)
                    }, LinearLayout.LayoutParams(0, dp(46), 1f))
                    addView(gapHorizontal(8))
                    addView(smallButton("حذف", true) {
                        confirm("حذف بازی", "این بازی برای همیشه از تاریخچه حذف شود؟") {
                            GameStore.deleteHistory(this@MainActivity, record.id); showHistory()
                        }
                    }, LinearLayout.LayoutParams(0, dp(46), 1f))
                })
            }, spaced())
        }
        if (history.isNotEmpty()) {
            page.addView(gap(10)); page.addView(dangerButton("پاک‌کردن کل تاریخچه") {
                confirm("پاک‌کردن تاریخچه", "تمام بازی‌های ذخیره‌شده حذف شوند؟") { GameStore.clearHistory(this); showHistory() }
            })
        }
        render(page)
    }

    private fun showSettings() {
        currentScreen = "settings"
        backAction = { showHome() }
        val page = page()
        page.addView(toolbar("تنظیمات حرفه‌ای") { saveAndHome() })
        page.addView(hero(R.drawable.royal_hero_settings, Rect(0, 0, 640, 400), "تنظیمات دیوان", "کنترل کامل قوانین و تجربه"))
        page.addView(section("عمومی"))
        page.addView(settingSwitch("بازخورد لمسی", "لرزش کوتاه هنگام ثبت", settings.haptic) { settings.haptic = it })
        page.addView(settingSwitch("روشن ماندن صفحه", "مناسب داوری طولانی", settings.keepScreenAwake) { settings.keepScreenAwake = it; applyWindowSettings() })
        page.addView(settingSwitch("اعداد فارسی", "نمایش همهٔ امتیازها با رقم فارسی", settings.persianDigits) { settings.persianDigits = it })
        page.addView(settingSwitch("متن درشت", "افزایش خوانایی بدون بیرون‌زدگی", settings.largeText) { settings.largeText = it })

        page.addView(section("قوانین شلم — مطابق شلم‌شمار"))
        page.addView(settingNumber("امتیاز پایان بازی", settings.shalamTarget, 100, 5000, 50) { settings.shalamTarget = it })
        page.addView(settingNumber("امتیاز شلم", settings.shalamValue, 330, 400, 70) { settings.shalamValue = it })
        page.addView(settingSwitch("محاسبهٔ امتیاز تعهد", "تیم حاکم در برد فقط امتیاز تعهد را بگیرد", settings.shalamAwardContractOnly) { settings.shalamAwardContractOnly = it })
        page.addView(settingSwitch("بازی با جوکر", "فعال‌سازی حالت شلم با جوکر", settings.shalamWithJoker) { settings.shalamWithJoker = it })
        page.addView(settingSwitch("پرسش برای دوبل", "پیش از ثبت دوبل مثبت یا منفی سؤال شود", settings.shalamAskDouble) { settings.shalamAskDouble = it })
        page.addView(settingNumber("پایان بازی با اختلاف", settings.shalamEndDifference, 0, 1000, 50) { settings.shalamEndDifference = it })

        page.addView(section("منفی"))
        page.addView(AtlasCropView(this, R.drawable.royal_hero_menfi_settings, Rect(0, 0, 640, 400), 16f, true), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(140)
        ))
        page.addView(gap(10))
        page.addView(settingNumber("تعداد دست‌ها", settings.menfiHands, 1, 20, 1) { settings.menfiHands = it })
        page.addView(settingSwitch("پنهان‌بودن جمع امتیاز", "تا زمان نمایش نتیجه، جمع دیده نشود", settings.menfiHiddenUntilReveal) { settings.menfiHiddenUntilReveal = it })

        page.addView(section("هزارتایی"))
        page.addView(settingNumber("تعداد دورها", settings.hezartaiiRounds, 1, 30, 1) { settings.hezartaiiRounds = it })
        page.addView(settingNumber("جریمهٔ صفر", settings.hezartaiiZeroPenalty, -200, 0, 10) { settings.hezartaiiZeroPenalty = it })
        page.addView(gap(18))
        page.addView(primaryButton("ذخیرهٔ تنظیمات") { saveAndHome() })
        render(page)
    }

    private fun saveAndHome() { GameStore.saveSettings(this, settings); applyWindowSettings(); showHome() }

    private fun settingSwitch(title: String, subtitle: String, checked: Boolean, changed: (Boolean) -> Unit): View = panel(true).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 15f, RoyalPalette.cream, true)); addView(text(subtitle, 11f, RoyalPalette.muted))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(this@MainActivity).apply {
            isChecked = checked; buttonTintList = null; setOnCheckedChangeListener { _, value -> changed(value) }
        })
    }

    private fun settingNumber(title: String, value: Int, min: Int, max: Int, step: Int, changed: (Int) -> Unit): View {
        val state = intArrayOf(value)
        return panel().apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(text(title, 15f, RoyalPalette.cream, true))
            addView(gap(8))
            addView(stepper("", state, min, max, step) { changed(it) })
        }
    }

    private fun stepper(title: String, state: IntArray, min: Int, max: Int, step: Int, changed: (Int) -> Unit): View {
        val value = text(fa(state[0]), 18f, RoyalPalette.paleGold, true, Gravity.CENTER)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            if (title.isNotBlank()) addView(text(title, 13f, RoyalPalette.muted), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(smallButton("−") { state[0] = (state[0] - step).coerceAtLeast(min); value.text = fa(state[0]); changed(state[0]) })
            addView(value, LinearLayout.LayoutParams(dp(82), dp(44)))
            addView(smallButton("+") { state[0] = (state[0] + step).coerceAtMost(max); value.text = fa(state[0]); changed(state[0]) })
        }
    }

    private fun hero(resource: Int, crop: Rect, title: String, subtitle: String): View = FrameLayout(this).apply {
        background = royalShape(RoyalPalette.navy, RoyalPalette.gold, 22f, 1, this@MainActivity)
        addView(AtlasCropView(this@MainActivity, resource, crop, 22f, false), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        addView(View(this@MainActivity).apply { setBackgroundColor(Color.argb(130, 2, 15, 31)) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(text("♛", 40f, RoyalPalette.paleGold, true, Gravity.CENTER))
            addView(text(title, 29f, RoyalPalette.paleGold, true, Gravity.CENTER))
            addView(text(subtitle, 14f, RoyalPalette.cream, false, Gravity.CENTER))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)).apply { bottomMargin = dp(14) } }

    private fun toolbar(title: String, back: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(smallButton("›") { back() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(text(title, 22f, RoyalPalette.paleGold, true, Gravity.CENTER), LinearLayout.LayoutParams(0, dp(58), 1f))
        addView(text("◆", 17f, RoyalPalette.turquoise, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(48), dp(48)))
    }

    private fun infoPanel(title: String, subtitle: String): View = panel().apply {
        setPadding(dp(14), dp(13), dp(14), dp(13))
        addView(text(title, 15f, RoyalPalette.paleGold, true, Gravity.CENTER))
        addView(gap(3)); addView(text(subtitle, 12f, RoyalPalette.muted, false, Gravity.CENTER).apply { maxLines = 3 })
    }

    private fun section(title: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(gap(14)); addView(RoyalDivider(this@MainActivity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)))
        addView(text(title, 17f, RoyalPalette.gold, true, Gravity.CENTER)); addView(gap(8))
    }

    private fun panel(horizontal: Boolean = false): LinearLayout = LinearLayout(this).apply {
        orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        background = royalShape(Color.argb(236, 6, 31, 55), RoyalPalette.gold, 17f, 1, this@MainActivity)
        clipToPadding = false
    }

    private fun twoColumn(first: View, second: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(gapHorizontal(8))
        addView(second, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(34))
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun render(page: View) {
        val root = FrameLayout(this).apply { setBackgroundColor(RoyalPalette.midnight) }
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.royal_background); scaleType = ImageView.ScaleType.CENTER_CROP; alpha = .48f
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply { setBackgroundColor(Color.argb(95, 1, 12, 26)) }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(page) }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, gravityValue: Int = Gravity.RIGHT): TextView = TextView(this).apply {
        text = value; textSize = size + if (settings.largeText) 1f else 0f; setTextColor(color)
        gravity = gravityValue; preparePersianText(this@MainActivity, bold)
        maxLines = 4; setPadding(dp(3), dp(2), dp(3), dp(2))
    }

    private fun baseButton(title: String, selected: Boolean, danger: Boolean, action: () -> Unit): Button = Button(this).apply {
        text = title; isAllCaps = false; textSize = 15f; minHeight = dp(50); maxLines = 2
        preparePersianText(this@MainActivity, true); gravity = Gravity.CENTER
        setTextColor(if (selected) RoyalPalette.midnight else RoyalPalette.cream)
        background = when { danger -> royalShape(RoyalPalette.crimson, RoyalPalette.gold, 15f, 1, this@MainActivity); else -> royalGradient(this@MainActivity, selected) }
        setPadding(dp(10), dp(4), dp(10), dp(4)); setOnClickListener { tap(it); action() }
    }

    private fun primaryButton(title: String, action: () -> Unit) = baseButton(title, true, false, action)
    private fun secondaryButton(title: String, action: () -> Unit) = baseButton(title, false, false, action)
    private fun dangerButton(title: String, action: () -> Unit) = baseButton(title, false, true, action)
    private fun compactButton(title: String, action: () -> Unit) = baseButton(fa(title), false, false, action).apply { textSize = 13f; minWidth = 0; minimumWidth = 0; setPadding(dp(3), dp(2), dp(3), dp(2)) }
    private fun smallButton(title: String, danger: Boolean = false, action: () -> Unit) = baseButton(title, false, danger, action).apply {
        textSize = 12f; minWidth = dp(48); minimumWidth = dp(48); setPadding(dp(7), dp(2), dp(7), dp(2))
    }

    private fun choiceButton(title: String, selected: Boolean) = baseButton(title, selected, false) { }.also { styleChoice(it, selected) }
    private fun styleChoice(button: Button, selected: Boolean) {
        button.background = royalGradient(this, selected)
        button.setTextColor(if (selected) RoyalPalette.midnight else RoyalPalette.cream)
    }

    private fun input(hint: String, initial: String): EditText = EditText(this).apply {
        this.hint = hint; setText(initial); setTextColor(RoyalPalette.cream); setHintTextColor(RoyalPalette.muted)
        textSize = 15f; setSingleLine(true); preparePersianText(this@MainActivity)
        background = royalShape(Color.rgb(4, 24, 45), RoyalPalette.gold, 13f, 1, this@MainActivity)
        setPadding(dp(12), dp(4), dp(12), dp(4)); minHeight = dp(52)
    }

    private fun numericInput(hint: String, initial: String, signed: Boolean): EditText = input(hint, initial).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
        gravity = Gravity.CENTER; textDirection = View.TEXT_DIRECTION_LTR
    }

    private fun gap(value: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(value)) }
    private fun gapHorizontal(value: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(value), 1) }
    private fun spaced() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun tap(view: View) { if (settings.haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun fa(value: Any): String = PersianText.digits(value, settings.persianDigits)
    private fun signed(value: Int): String = PersianText.signed(value, settings.persianDigits)
    private fun toLatin(value: String): String {
        var result = value
        "۰۱۲۳۴۵۶۷۸۹".forEachIndexed { index, char -> result = result.replace(char, '0' + index) }
        return result
    }

    private fun date(timestamp: Long): String = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa", "IR")).format(Date(timestamp))

    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("تأیید") { _, _ -> action() }.setNegativeButton("انصراف", null).show()
    }
}
