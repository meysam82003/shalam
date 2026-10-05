package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** Saved teams and players with their emblems and a small record of games and wins. */
class PlayersScreen(host: MainActivity) : Screen(host) {
    private var teams = true

    override fun build(): View {
        val roster = host.repo.roster()
        val shown = roster.filter { it.isTeam == teams }
        val sessions = host.repo.sessions()
        return scaffold(
            title = "بازیکنان و تیم‌ها",
            subtitle = "${kit.n(roster.count { it.isTeam })} تیم • ${kit.n(roster.count { !it.isTeam })} بازیکن",
            bottom = kit.button(if (teams) "افزودن تیم" else "افزودن بازیکن", ButtonKind.PRIMARY, RoyalIcon.PLUS) { edit(null) }
        ) {
            addView(kit.grid(2, listOf(
                kit.chip("تیم‌ها (شلم و منفی)", teams, ButtonKind.CHIP_GOLD) { teams = true; host.refresh() },
                kit.chip("بازیکنان (هزارتایی، دو لو)", !teams, ButtonKind.CHIP_GOLD) { teams = false; host.refresh() }
            ), 6), kit.spaced(10))
            if (shown.isEmpty()) {
                addView(emptyState(RoyalIcon.PLAYERS, if (teams) "هنوز تیمی ثبت نشده" else "هنوز بازیکنی ثبت نشده", "نام‌هایی که در شروع بازی وارد می‌کنید خودکار اینجا ذخیره می‌شوند."))
            }
            shown.forEach { entry ->
                val related = sessions.filter { s -> s.game.isTeamGame == entry.isTeam && s.sides.any { it.name == entry.name } }
                val wins = related.count { s -> s.finished && GameEngine.winners(s).any { s.sides[it].name == entry.name } }
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
                    setPadding(kit.dp(10), kit.dp(7), kit.dp(10), kit.dp(10))
                    layoutParams = kit.spaced(6)
                    addView(kit.avatar(entry.avatar, 40))
                    addView(kit.hgap(10))
                    addView(kit.vertical().apply {
                        addView(kit.text(entry.name, TextStyle.BODY_BOLD, Royal.goldLight, maxLines = 1))
                        addView(kit.text("${kit.n(related.size)} بازی  •  ${kit.n(wins)} برد", TextStyle.CAPTION, Royal.muted))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(kit.iconButton(RoyalIcon.EDIT, "ویرایش ${entry.name}", ButtonKind.CHIP, 38) { edit(entry) })
                    addView(kit.hgap(6))
                    addView(kit.iconButton(RoyalIcon.TRASH, "حذف ${entry.name}", ButtonKind.CHIP, 38) {
                        kit.confirm("حذف از فهرست", "«${entry.name}» از فهرست حذف شود؟ بازی‌های ثبت‌شده تغییری نمی‌کنند.", "حذف", true) {
                            host.repo.saveRoster(host.repo.roster().filterNot { it.id == entry.id })
                            host.refresh()
                        }
                    })
                })
            }
        }
    }

    private fun edit(entry: RosterEntry?) {
        var avatar = entry?.avatar ?: (host.repo.roster().size * 3) % Emblems.COUNT
        val name = kit.field(if (teams) "نام تیم" else "نام بازیکن", entry?.name ?: "")
        val avatarView = kit.avatar(avatar, 64)
        avatarView.setOnClickListener {
            avatarPicker(avatar) { picked ->
                avatar = picked
                avatarView.avatar = picked
            }
        }
        val error = kit.text("", TextStyle.CAPTION, Royal.crimsonLight, Gravity.CENTER)
        lateinit var dialog: android.app.Dialog
        dialog = kit.dialog(if (entry == null) (if (teams) "تیم تازه" else "بازیکن تازه") else "ویرایش", null, kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(avatarView)
            addView(kit.text("برای تغییر نشان لمس کنید", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
            addView(kit.gap(8))
            addView(name, kit.fill())
            addView(error)
        }, listOf(
            DialogAction("ذخیره", ButtonKind.PRIMARY, dismiss = false) {
                val value = name.text.toString().trim()
                val roster = host.repo.roster().toMutableList()
                when {
                    value.isEmpty() -> error.text = "نام را وارد کنید"
                    roster.any { it.name == value && it.isTeam == teams && it.id != entry?.id } -> error.text = "این نام قبلاً ثبت شده است"
                    else -> {
                        dialog.dismiss()
                        if (entry == null) roster += RosterEntry(System.currentTimeMillis(), value, avatar, teams)
                        else {
                            val i = roster.indexOfFirst { it.id == entry.id }
                            if (i >= 0) roster[i] = entry.copy(name = value, avatar = avatar)
                        }
                        host.repo.saveRoster(roster)
                        host.refresh()
                    }
                }
            },
            DialogAction("انصراف")
        ))
        dialog.show()
    }
}
