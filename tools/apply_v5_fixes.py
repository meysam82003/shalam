from pathlib import Path

p = Path('app/src/main/java/com/meysam/divanemtiaz/DivanFinalActivity.kt')
text = p.read_text(encoding='utf-8')


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if old not in text:
        raise SystemExit(f'missing patch target: {label}')
    text = text.replace(old, new, 1)

# 1) Permanent runtime dp fix (previous APK crash source).
text = text.replace(
    'private fun dp(value: Int): Int = this.dp(value)',
    'private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()'
)
text = text.replace(
    'private fun dp(value: Int): Int = dp(value)',
    'private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()'
)

# 2) Hezartaii: EditText instances are reused while rebuilding the list. Detach
# from their old row first or Android throws "child already has a parent".
replace_once(
    '        addView(draft.input, LinearLayout.LayoutParams(0, dp(48), 1f))',
    '        (draft.input.parent as? ViewGroup)?.removeView(draft.input)\n'
    '        addView(draft.input, LinearLayout.LayoutParams(0, dp(48), 1f))',
    'hezartaii reused input parent'
)

# 3) Shalam uses the recovered ShalamShomar reference engine, while keeping the
# current Divan UI/UX.
replace_once(
    '        body.addView(info("روش ثبت", "اول تیم حاکم، بعد تعهد؛ امتیاز واقعی حریف را وارد کن تا سهم حاکم از ۱۶۵ خودکار حساب شود."))',
    '        val shalamTotal = ShalamReferenceEngine.total(settings)\n'
    '        body.addView(info("روش ثبت", "اول تیم حاکم، بعد تعهد؛ امتیاز واقعی حریف را وارد کن تا سهم حاکم از ${fa(shalamTotal)} خودکار حساب شود."))',
    'shalam dynamic total description'
)
replace_once(
    '        ShalamEngineV3.readyBids(settings.shalamMinBid).forEach { bid ->',
    '        ShalamReferenceEngine.readyBids(settings).forEach { bid ->',
    'shalam reference bids'
)
replace_once(
    '            bids.addView(small(if (bid == 165) "شلم" else fa(bid)) { shalamDialog(session, contractor, bid, null) }, GridLayout.LayoutParams().apply {',
    '            bids.addView(small(if (bid == shalamTotal) "شلم" else fa(bid)) { shalamDialog(session, contractor, bid, null) }, GridLayout.LayoutParams().apply {',
    'dynamic shalam button'
)
replace_once(
    '        val old = editIndex?.let { session.rounds[it] }\n        val oldOpp = old?.meta?.get("opponentActual") ?: "0"\n        val opponent = numberField("امتیاز واقعی حریف ۰ تا ۱۶۵", oldOpp, false)',
    '        val old = editIndex?.let { session.rounds[it] }\n        val total = ShalamReferenceEngine.total(settings)\n        val oldOpp = old?.meta?.get("opponentActual") ?: "0"\n        val opponent = numberField("امتیاز واقعی حریف ۰ تا ${fa(total)}", oldOpp, false)',
    'shalam dialog dynamic total'
)
replace_once(
    '                if (opp == null || opp !in 0..165) { toast("امتیاز حریف باید بین ۰ تا ۱۶۵ باشد"); return@setPositiveButton }\n                val result = ShalamEngineV3.score(bid, opp, settings, declaredShalam = bid == 165)',
    '                if (opp == null || opp !in 0..total) { toast("امتیاز حریف باید بین ۰ تا ${fa(total)} باشد"); return@setPositiveButton }\n                val result = ShalamReferenceEngine.score(bid, opp, settings, declaredShalam = bid == total)',
    'shalam reference scoring'
)
replace_once(
    '                    append(if (bid == 165) "شلم" else "تعهد $bid")',
    '                    append(if (bid == total) "شلم" else "تعهد $bid")',
    'dynamic shalam title'
)

# 4) Menfi result preview and stored round description must explain THIS hand.
marker = '''        fun outcomeTitle(outcome: MenfiOutcomeV4): String = when (outcome) {
            MenfiOutcomeV4.BOTH_MADE -> "هر دو گروه گرفتند"
            MenfiOutcomeV4.A_FAILED_B_MADE -> "${session.teamA!!.name} منفی • ${session.teamB!!.name} مثبت"
            MenfiOutcomeV4.A_MADE_B_FAILED -> "${session.teamA!!.name} مثبت • ${session.teamB!!.name} منفی"
            MenfiOutcomeV4.BOTH_FAILED -> "هر دو گروه منفی شدند"
        }
'''
replacement = marker + '''
        fun previewText(result: MenfiHandResultV4): String =
            "${session.teamA!!.name}: اعلام ${fa(bidA)} ${if (result.teamAMade) "گرفت" else "نگرفت"} → ${signed(result.teamAScore)}  |  " +
                "${session.teamB!!.name}: اعلام ${fa(bidB)} ${if (result.teamBMade) "گرفت" else "نگرفت"} → ${signed(result.teamBScore)}"
'''
replace_once(marker, replacement, 'menfi preview helper')
text = text.replace(
    'preview.text = "پیش‌نمایش امتیاز: ${session.teamA!!.name} ${signed(score.teamAScore)}  |  ${session.teamB!!.name} ${signed(score.teamBScore)}"',
    'preview.text = previewText(score)'
)

old_note = '                    note = buildString { append("اعلام $bidA/$bidB • $human"); if (judgeNote.isNotBlank()) append(" • $judgeNote") },'
new_note = '''                    note = buildString {
                        append("${session.teamA!!.name}: اعلام $bidA ${if (result.teamAMade) "گرفت" else "نگرفت"} ${result.teamAScore}")
                        append(" • ${session.teamB!!.name}: اعلام $bidB ${if (result.teamBMade) "گرفت" else "نگرفت"} ${result.teamBScore}")
                        append(" • $human")
                        if (judgeNote.isNotBlank()) append(" • $judgeNote")
                    },'''
replace_once(old_note, new_note, 'menfi stored description')

# Use the royal dialog skin for result/edit dialogs.
replace_once(
    '        dialog.show()\n    }\n\n    private fun outcomeCard',
    '        dialog.show()\n        styleDivanDialog(dialog)\n    }\n\n    private fun outcomeCard',
    'style menfi result dialog'
)
replace_once(
    '        dialog.show()\n    }\n\n    private fun finish(session: V3Session)',
    '        dialog.show()\n        styleDivanDialog(dialog)\n    }\n\n    private fun finish(session: V3Session)',
    'style hezartaii edit dialog'
)

# 5) Automatic Menfi conclusion immediately after target hand count.
old_add = '''                if (editIndex == null) {
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
'''
new_add = '''                if (editIndex == null) {
                    session.rounds += round
                    if (settings.menfiHideMode == 2) session.revealed = true
                    handleMenfiAfterMutation(session)
                } else {
                    session.rounds[editIndex] = round
                    scope.launch {
                        repo.addAudit(session.id, editIndex, "EDIT", old, round)
                        handleMenfiAfterMutation(session)
                    }
                }
'''
replace_once(old_add, new_add, 'automatic menfi result routing')

finish_marker = '''    private fun menfiFinishCheck(session: V3Session) {
'''
helper = '''    private fun handleMenfiAfterMutation(session: V3Session) {
        when (MenfiRulesV4.matchDecision(session.rounds, session.menfiHandsTarget)) {
            MenfiMatchDecision.CONTINUE -> save(session) { menfi(session) }
            MenfiMatchDecision.TIE -> {
                session.revealed = true
                save(session) { menfiFinishCheck(session) }
            }
            MenfiMatchDecision.TEAM_A_WINS,
            MenfiMatchDecision.TEAM_B_WINS -> {
                session.revealed = true
                finish(session)
            }
        }
    }

'''
replace_once(finish_marker, helper + finish_marker, 'menfi auto-finish helper')

# 6) Settings: remove obsolete +20/-10 and override controls. Keep only settings
# that still affect the requested Menfi rules.
old_settings = '''        body.addView(stepper("تعداد دست مسابقه", settings.menfiHands, 1, 50, 1) { settings.menfiHands = it })
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
'''
new_settings = '''        body.addView(stepper("تعداد دست مسابقه", settings.menfiHands, 1, 50, 1) { settings.menfiHands = it })
        body.addView(stepper("حداقل اعلام", settings.menfiMinBid, 1, 10, 1) { settings.menfiMinBid = it })
        body.addView(info("قانون امتیاز منفی", "هر گروه اگر اعلام همان دست را بگیرد، همان عدد مثبت می‌شود؛ اگر نگیرد، همان عدد منفی می‌شود. مثال: اعلام ۸ → +۸ یا −۸."))
'''
replace_once(old_settings, new_settings, 'remove obsolete menfi scoring settings')

# Shalam settings text follows recovered 165/200 rules; multiplier is no longer a
# fake variable for standard Shelem, which is fixed 330/400.
text = text.replace(
    '        body.addView(stepper("ضریب شلم", settings.shalamMultiplier, 1, 5, 1) { settings.shalamMultiplier = it })\n',
    '        body.addView(info("امتیاز شلم", "بدون جوکر ۳۳۰ و با جوکر ۴۰۰؛ مطابق قوانین شلم‌شمار مرجع."))\n'
)

p.write_text(text, encoding='utf-8')

# Strict verification: fail CI rather than silently shipping a half-patched APK.
final = p.read_text(encoding='utf-8')
required = [
    'ShalamReferenceEngine.readyBids(settings)',
    'ShalamReferenceEngine.score(bid, opp, settings',
    '(draft.input.parent as? ViewGroup)?.removeView(draft.input)',
    'private fun handleMenfiAfterMutation(session: V3Session)',
    'preview.text = previewText(score)',
    'styleDivanDialog(dialog)',
    'همان عدد مثبت می‌شود',
    'private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()',
]
missing = [item for item in required if item not in final]
if missing:
    raise SystemExit(f'V5 patch incomplete, missing: {missing}')
for bad in [
    'private fun dp(value: Int): Int = this.dp(value)',
    'private fun dp(value: Int): Int = dp(value)',
    'body.addView(stepper("برد پایه"',
    'body.addView(stepper("باخت پایه"',
]:
    if bad in final:
        raise SystemExit(f'V5 obsolete/unsafe code remains: {bad}')

print('V5 requested fixes applied successfully')
