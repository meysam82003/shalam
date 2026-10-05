package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import org.json.JSONObject
import java.util.Calendar

/** Backup of everything to a JSON file, and restoring it (merge or full replace). */
class BackupScreen(host: MainActivity) : Screen(host) {
    override fun build(): View {
        val sessions = host.repo.index().size
        val leagues = host.repo.leagueIndex().size
        val roster = host.repo.roster().size
        return scaffold(title = "پشتیبان‌گیری", subtitle = "بازی‌ها، تاریخچه، تیم‌ها، لیگ‌ها و تنظیمات") {
            addView(kit.panel(PanelStyle.RAISED, 14).apply {
                layoutParams = kit.spaced(10)
                gravity = Gravity.CENTER_HORIZONTAL
                addView(kit.icon(RoyalIcon.SAVE, Royal.gold, 34))
                addView(kit.text("${kit.n(sessions)} بازی  •  ${kit.n(leagues)} لیگ  •  ${kit.n(roster)} تیم و بازیکن", TextStyle.BODY_BOLD, Royal.goldLight, Gravity.CENTER))
                addView(kit.text("فایل پشتیبان روی همین گوشی، گوشی دیگر و نسخهٔ وب دیوان امتیاز قابل بازگردانی است.", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
            })
            addView(kit.section("گرفتن پشتیبان", RoyalIcon.SAVE))
            addView(kit.button("ذخیرهٔ فایل پشتیبان در گوشی", ButtonKind.PRIMARY, RoyalIcon.SAVE) { exportToFile() }, kit.spaced(8))
            addView(kit.button("ارسال فایل پشتیبان (تلگرام، ایمیل، …)", ButtonKind.SECONDARY, RoyalIcon.SHARE) {
                host.shareFile(fileName(), host.repo.exportBackup().toString(), "application/json")
            }, kit.spaced(8))
            addView(kit.section("بازگردانی", RoyalIcon.UNDO))
            addView(kit.button("انتخاب فایل پشتیبان", ButtonKind.SUCCESS, RoyalIcon.PLUS) { importFromFile() }, kit.spaced(8))
            addView(kit.panel(PanelStyle.FLAT, 12).apply {
                layoutParams = kit.spaced(6)
                listOf(
                    "«افزودن به داده‌های فعلی»: بازی‌ها و لیگ‌های تازه اضافه می‌شوند، نسخهٔ جدیدتر هر بازی جایگزین قدیمی‌تر می‌شود و تیم‌ها یکی می‌شوند؛ تنظیمات دست نمی‌خورد.",
                    "«جایگزینی کامل»: همهٔ بازی‌ها، لیگ‌ها و تیم‌های فعلی پاک و داده‌های فایل (همراه تنظیمات) جایگزین می‌شوند.",
                    "پیش از جایگزینی کامل، بهتر است از داده‌های فعلی پشتیبان بگیرید."
                ).forEach { addView(kit.text("•  $it", TextStyle.LABEL, Royal.ivory)) }
            })
        }
    }

    private fun fileName(): String {
        val c = Calendar.getInstance()
        val j = JalaliDate.toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return String.format(java.util.Locale.US, "divan-backup-%04d-%02d-%02d.json", j[0], j[1], j[2])
    }

    private fun exportToFile() {
        host.pickDocument(true, fileName()) { uri ->
            if (uri == null) return@pickDocument
            try {
                host.contentResolver.openOutputStream(uri)?.use { it.write(host.repo.exportBackup().toString(2).toByteArray(Charsets.UTF_8)) }
                    ?: throw IllegalStateException()
                kit.toast("فایل پشتیبان ذخیره شد")
            } catch (e: Exception) {
                kit.toast("ذخیرهٔ فایل ممکن نشد")
            }
        }
    }

    private fun importFromFile() {
        host.pickDocument(false, "") { uri ->
            if (uri == null) return@pickDocument
            val backup = try {
                val text = host.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                JSONObject(text).also { host.repo.inspectBackup(it) }
            } catch (e: Exception) {
                null
            }
            if (backup == null) {
                kit.toast("این فایل پشتیبان دیوان امتیاز نیست")
                return@pickDocument
            }
            confirmImport(backup)
        }
    }

    fun confirmImport(backup: JSONObject) {
        val info = host.repo.inspectBackup(backup)
        val date = if (info.exportedAt > 0) JalaliDate.format(info.exportedAt, settings.general.persianDigits) else "—"
        kit.dialog(
            "بازگردانی پشتیبان",
            "${kit.n(info.sessions)} بازی • ${kit.n(info.leagues)} لیگ • ${kit.n(info.roster)} تیم و بازیکن\nتاریخ پشتیبان: $date",
            null,
            listOf(
                DialogAction("افزودن به داده‌های فعلی", ButtonKind.PRIMARY) { apply(backup, false) },
                DialogAction("جایگزینی کامل", ButtonKind.DANGER) {
                    kit.confirm("جایگزینی کامل", "همهٔ بازی‌ها، لیگ‌ها و تیم‌های فعلی پاک و با فایل پشتیبان جایگزین شوند؟", "جایگزین کن", true) { apply(backup, true) }
                },
                DialogAction("انصراف")
            )
        ).show()
    }

    private fun apply(backup: JSONObject, replace: Boolean) {
        try {
            val r = host.repo.importBackup(backup, replace)
            host.reloadSettings()
            kit.toast("${kit.n(r.sessionsAdded)} بازی و ${kit.n(r.leaguesAdded)} لیگ اضافه شد" + if (r.sessionsUpdated > 0) "؛ ${kit.n(r.sessionsUpdated)} بازی به‌روز شد" else "")
            host.refresh()
        } catch (e: Exception) {
            kit.toast("بازگردانی ممکن نشد")
        }
    }
}
