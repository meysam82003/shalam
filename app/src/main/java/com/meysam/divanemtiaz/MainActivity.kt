package com.meysam.divanemtiaz

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val navy = Color.rgb(7, 23, 40)
    private val navySoft = Color.rgb(12, 42, 58)
    private val teal = Color.rgb(31, 199, 182)
    private val gold = Color.rgb(213, 169, 78)
    private val cream = Color.rgb(247, 239, 217)
    private val muted = Color.rgb(172, 194, 199)
    private val danger = Color.rgb(218, 92, 92)

    private val avatars = intArrayOf(
        R.drawable.avatar_king,
        R.drawable.avatar_queen,
        R.drawable.avatar_guard,
        R.drawable.avatar_scholar
    )

    private var currentScreen = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(6, 19, 31)
        window.navigationBarColor = Color.rgb(6, 19, 31)
        showHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentScreen == "home") super.onBackPressed() else showHome()
    }

    private fun showHome() {
        currentScreen = "home"
        val page = pageColumn()
        page.addView(label("دیوان امتیاز", 34f, gold, true, Gravity.CENTER))
        page.addView(label("امتیازشمار سلطنتی دورهمی‌های شما", 14f, muted, false, Gravity.CENTER))
        page.addView(gap(20))

        page.addView(panel().apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
            addView(label("♛", 48f, gold, true, Gravity.CENTER))
            addView(label("سه بازی، یک دیوان", 22f, cream, true, Gravity.CENTER))
            addView(label("بدون اینترنت • همیشه همراه جمع", 13f, muted, false, Gravity.CENTER))
        })
        page.addView(sectionTitle("انتخاب بازی"))

        GameType.entries.forEach { game ->
            page.addView(gameCard(game), spacedParams())
        }

        page.addView(gap(10))
        page.addView(button("مشاهده تاریخچه بازی‌ها", false) { showHistory() })
        page.addView(gap(10))
        page.addView(button("تنظیمات", false) { showSettings() })
        page.addView(gap(28))
        page.addView(label("نسخه ۱.۰.۰  •  ساخته‌شده برای Android", 12f, muted, false, Gravity.CENTER))
        render(page)
    }

    private fun gameCard(game: GameType): View {
        return panel(horizontal = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(label(game.badge, 34f, teal, true, Gravity.CENTER).apply {
                background = circle(Color.argb(120, 31, 199, 182), gold)
            }, LinearLayout.LayoutParams(dp(62), dp(62)))
            addView(gapHorizontal(14))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(game.title, 22f, cream, true))
                addView(label(game.subtitle, 13f, muted))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label("‹", 34f, gold, false, Gravity.CENTER))
            setOnClickListener { showSetup(game) }
        }
    }

    private fun showSetup(game: GameType) {
        currentScreen = "setup"
        var avatarA = 0
        var avatarB = 1
        val page = pageColumn()
        page.addView(topBar("آماده‌سازی ${game.title}") { showHome() })
        page.addView(label(game.subtitle, 14f, muted, false, Gravity.CENTER))
        page.addView(gap(18))

        val inputA = editText("نام تیم اول", "تیم فیروزه")
        val avatarViewA = avatarView(avatarA, 112)
        page.addView(teamSetupPanel("تیم اول", inputA, avatarViewA) {
            showAvatarPicker { selected ->
                avatarA = selected
                avatarViewA.setImageResource(avatars[selected])
            }
        })
        page.addView(gap(12))

        val inputB = editText("نام تیم دوم", "تیم طلایی")
        val avatarViewB = avatarView(avatarB, 112)
        page.addView(teamSetupPanel("تیم دوم", inputB, avatarViewB) {
            showAvatarPicker { selected ->
                avatarB = selected
                avatarViewB.setImageResource(avatars[selected])
            }
        })
        page.addView(gap(20))

        page.addView(button("شروع داوری ${game.title}", true) {
            val teamA = inputA.text.toString().trim().ifBlank { "تیم فیروزه" }
            val teamB = inputB.text.toString().trim().ifBlank { "تیم طلایی" }
            showScore(GameSession(game, teamA, teamB, avatarA, avatarB))
        })
        page.addView(gap(10))
        page.addView(button("انصراف", false) { showHome() })
        render(page)
    }

    private fun teamSetupPanel(
        title: String,
        input: EditText,
        avatar: ImageView,
        onAvatarClick: () -> Unit
    ): LinearLayout {
        return panel(horizontal = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            avatar.setOnClickListener { onAvatarClick() }
            addView(avatar)
            addView(gapHorizontal(14))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 14f, gold, true))
                addView(gap(6))
                addView(input)
                addView(gap(8))
                addView(label("برای تغییر آواتار روی تصویر بزنید", 11f, muted))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun showAvatarPicker(onSelected: (Int) -> Unit) {
        val grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(navy)
        }
        avatars.indices.forEach { index ->
            grid.addView(avatarView(index, 132).apply {
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }, GridLayout.LayoutParams().apply {
                width = dp(144)
                height = dp(144)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("انتخاب آواتار")
            .setView(grid)
            .setNegativeButton("بستن", null)
            .create()
        grid.childrenList().forEach { child ->
            child.setOnClickListener {
                val index = grid.indexOfChild(child)
                onSelected(index)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showScore(session: GameSession) {
        currentScreen = "score"
        val scoreA = ScoreEngine.total(session.rounds, true)
        val scoreB = ScoreEngine.total(session.rounds, false)
        val page = pageColumn()
        page.addView(topBar(session.game.title) {
            confirm("خروج از بازی", "امتیازهای ثبت‌نشده این بازی از بین می‌رود.") { showHome() }
        })

        if (ScoreEngine.reachedTarget(session.game, scoreA, scoreB)) {
            page.addView(label("★ حد نصاب بازی ثبت شد ★", 14f, navy, true, Gravity.CENTER).apply {
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = rounded(gold, gold, 18)
            })
            page.addView(gap(12))
        }

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(scorePanel(session.teamA, session.avatarA, scoreA), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(gapHorizontal(10))
            addView(scorePanel(session.teamB, session.avatarB, scoreB), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        page.addView(gap(14))
        page.addView(label("دست ${session.rounds.size + 1}", 16f, gold, true, Gravity.CENTER))
        page.addView(gap(8))

        val inputA = scoreInput(session.teamA)
        val inputB = scoreInput(session.teamB)
        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(inputA, LinearLayout.LayoutParams(0, dp(58), 1f))
            addView(gapHorizontal(10))
            addView(inputB, LinearLayout.LayoutParams(0, dp(58), 1f))
        })
        page.addView(gap(12))
        page.addView(button("ثبت امتیاز این دست", true) {
            val valueA = inputA.text.toString().trim().toIntOrNull() ?: 0
            val valueB = inputB.text.toString().trim().toIntOrNull() ?: 0
            if (inputA.text.isBlank() && inputB.text.isBlank()) {
                Toast.makeText(this, "حداقل یک امتیاز وارد کنید", Toast.LENGTH_SHORT).show()
            } else {
                session.rounds.add(RoundScore(valueA, valueB))
                showScore(session)
            }
        })
        page.addView(gap(10))
        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("↶ بازگردانی", false) {
                if (session.rounds.isNotEmpty()) {
                    session.rounds.removeAt(session.rounds.lastIndex)
                    showScore(session)
                } else Toast.makeText(this@MainActivity, "هنوز دستی ثبت نشده", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(gapHorizontal(10))
            addView(button("پایان بازی", false) { finishGame(session) }, LinearLayout.LayoutParams(0, dp(54), 1f))
        })

        if (session.rounds.isNotEmpty()) {
            page.addView(sectionTitle("آخرین دست‌ها"))
            session.rounds.takeLast(5).reversed().forEachIndexed { index, round ->
                val handNumber = session.rounds.size - index
                page.addView(label("دست $handNumber     ${session.teamA}: ${round.teamA}     ${session.teamB}: ${round.teamB}", 13f, cream).apply {
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                    background = rounded(Color.argb(175, 7, 23, 40), Color.argb(130, 213, 169, 78), 14)
                }, spacedParams(6))
            }
        }
        render(page)
    }

    private fun finishGame(session: GameSession) {
        val scoreA = ScoreEngine.total(session.rounds, true)
        val scoreB = ScoreEngine.total(session.rounds, false)
        val winner = ScoreEngine.winner(session.game, scoreA, scoreB)
        val winnerName = when (winner) {
            1 -> session.teamA
            2 -> session.teamB
            else -> "مساوی"
        }
        confirm(
            "پایان بازی",
            "نتیجه ${session.teamA} $scoreA — $scoreB ${session.teamB}\nبرنده: $winnerName"
        ) {
            HistoryStore.add(this, HistoryRecord(
                game = session.game,
                teamA = session.teamA,
                teamB = session.teamB,
                scoreA = scoreA,
                scoreB = scoreB,
                avatarA = session.avatarA,
                avatarB = session.avatarB,
                rounds = session.rounds.size,
                timestamp = System.currentTimeMillis()
            ))
            showHistory()
        }
    }

    private fun showHistory() {
        currentScreen = "history"
        val page = pageColumn()
        page.addView(topBar("تاریخچه بازی‌ها") { showHome() })
        val records = HistoryStore.load(this)
        if (records.isEmpty()) {
            page.addView(gap(50))
            page.addView(label("هنوز بازی‌ای ثبت نشده", 20f, cream, true, Gravity.CENTER))
            page.addView(label("اولین رقابت را از صفحه خانه شروع کنید", 14f, muted, false, Gravity.CENTER))
            page.addView(gap(24))
            page.addView(button("شروع یک بازی", true) { showHome() })
        } else {
            val formatter = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa"))
            records.forEach { record ->
                val winner = ScoreEngine.winner(record.game, record.scoreA, record.scoreB)
                val winnerName = when (winner) { 1 -> record.teamA; 2 -> record.teamB; else -> "مساوی" }
                page.addView(panel().apply {
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(label(record.game.badge, 26f, teal, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(44), dp(44)))
                        addView(label(record.game.title, 18f, gold, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(label(formatter.format(Date(record.timestamp)), 11f, muted))
                    })
                    addView(gap(10))
                    addView(label("${record.teamA}   ${record.scoreA}  —  ${record.scoreB}   ${record.teamB}", 18f, cream, true, Gravity.CENTER))
                    addView(gap(6))
                    addView(label("برنده: $winnerName  •  ${record.rounds} دست", 12f, muted, false, Gravity.CENTER))
                }, spacedParams())
            }
        }
        page.addView(gap(18))
        page.addView(button("بازگشت به خانه", false) { showHome() })
        render(page)
    }

    private fun showSettings() {
        currentScreen = "settings"
        val page = pageColumn()
        page.addView(topBar("تنظیمات") { showHome() })
        val prefs = getSharedPreferences("divan_prefs", Context.MODE_PRIVATE)
        page.addView(panel().apply {
            addView(label("تجربه بازی", 18f, gold, true))
            addView(gap(10))
            addView(CheckBox(this@MainActivity).apply {
                text = "بازخورد لمسی دکمه‌ها"
                textSize = 15f
                setTextColor(cream)
                buttonTintList = android.content.res.ColorStateList.valueOf(teal)
                isChecked = prefs.getBoolean("haptic", true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("haptic", checked).apply() }
            })
            addView(CheckBox(this@MainActivity).apply {
                text = "نمایش پیام رسیدن به حد نصاب"
                textSize = 15f
                setTextColor(cream)
                buttonTintList = android.content.res.ColorStateList.valueOf(teal)
                isChecked = prefs.getBoolean("target_hint", true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("target_hint", checked).apply() }
            })
        })
        page.addView(gap(12))
        page.addView(panel().apply {
            addView(label("درباره دیوان", 18f, gold, true))
            addView(gap(8))
            addView(label("یک امتیازشمار مستقل، آفلاین و فارسی برای شلم، منفی و هزارتایی. اطلاعات بازی فقط روی همین دستگاه ذخیره می‌شود.", 14f, cream))
            addView(gap(6))
            addView(label("نسخه ۱.۰.۰ • حداقل Android 5.0", 12f, muted))
        })
        page.addView(gap(16))
        page.addView(button("پاک‌کردن تاریخچه", false) {
            confirm("پاک‌کردن تاریخچه", "تمام نتایج ذخیره‌شده حذف می‌شوند.") {
                HistoryStore.clear(this)
                Toast.makeText(this, "تاریخچه پاک شد", Toast.LENGTH_SHORT).show()
            }
        }.apply { setTextColor(danger) })
        page.addView(gap(10))
        page.addView(button("بازگشت به خانه", true) { showHome() })
        render(page)
    }

    private fun render(page: LinearLayout) {
        val root = FrameLayout(this)
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.royal_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply { setBackgroundColor(Color.argb(92, 0, 8, 18)) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(page)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun pageColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(18), dp(24), dp(18), dp(34))
    }

    private fun topBar(title: String, onBack: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(button("→", false) { onBack() }, LinearLayout.LayoutParams(dp(58), dp(50)))
        addView(label(title, 24f, gold, true, Gravity.CENTER), LinearLayout.LayoutParams(0, dp(56), 1f))
        addView(Space(this@MainActivity), LinearLayout.LayoutParams(dp(58), dp(1)))
    }

    private fun scorePanel(team: String, avatar: Int, score: Int): LinearLayout = panel().apply {
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(12), dp(8), dp(14))
        addView(avatarView(avatar, 92))
        addView(label(team, 14f, cream, true, Gravity.CENTER))
        addView(label(score.toString(), 32f, teal, true, Gravity.CENTER))
    }

    private fun avatarView(index: Int, size: Int) = ImageView(this).apply {
        setImageResource(avatars[index.coerceIn(avatars.indices)])
        scaleType = ImageView.ScaleType.CENTER_CROP
        contentDescription = "آواتار"
        background = circle(Color.argb(170, 8, 40, 54), gold)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun scoreInput(team: String) = EditText(this).apply {
        hint = "امتیاز $team"
        setHintTextColor(muted)
        setTextColor(cream)
        textSize = 15f
        gravity = Gravity.CENTER
        textDirection = View.TEXT_DIRECTION_RTL
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        setPadding(dp(10), 0, dp(10), 0)
        background = rounded(Color.argb(220, 7, 23, 40), gold, 16)
    }

    private fun editText(hintValue: String, defaultValue: String) = EditText(this).apply {
        hint = hintValue
        setText(defaultValue)
        selectAll()
        setTextColor(cream)
        setHintTextColor(muted)
        textSize = 16f
        gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        textDirection = View.TEXT_DIRECTION_RTL
        singleLine = true
        setPadding(dp(12), 0, dp(12), 0)
        background = rounded(Color.argb(210, 7, 23, 40), Color.argb(180, 213, 169, 78), 14)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
    }

    private fun button(textValue: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 15f
        setTextColor(if (primary) navy else cream)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        gravity = Gravity.CENTER
        stateListAnimator = null
        background = rounded(
            if (primary) teal else Color.argb(210, 7, 23, 40),
            if (primary) gold else Color.argb(180, 213, 169, 78),
            18
        )
        setOnClickListener {
            if (getSharedPreferences("divan_prefs", Context.MODE_PRIVATE).getBoolean("haptic", true)) {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
            onClick()
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        align: Int = Gravity.RIGHT
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = align or Gravity.CENTER_VERTICAL
        textDirection = View.TEXT_DIRECTION_RTL
        setLineSpacing(dp(3).toFloat(), 1f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun sectionTitle(value: String) = label(value, 18f, gold, true).apply {
        setPadding(dp(2), dp(24), dp(2), dp(10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun panel(horizontal: Boolean = false) = LinearLayout(this).apply {
        orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.argb(218, 7, 23, 40), Color.argb(205, 213, 169, 78), 22)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun circle(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun confirm(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("تأیید") { _, _ -> onConfirm() }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun gap(dpValue: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(dpValue))
    }

    private fun gapHorizontal(dpValue: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(dpValue), dp(1))
    }

    private fun spacedParams(bottom: Int = 10) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(bottom) }

    private fun GridLayout.childrenList(): List<View> = (0 until childCount).map { getChildAt(it) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

