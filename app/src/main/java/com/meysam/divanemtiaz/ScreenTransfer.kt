package com.meysam.divanemtiaz

import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView

private fun Screen.canTransfer(kind: TransferKind): Boolean {
    if (kind != TransferKind.MAFIA || ProState.isPro) return true
    kit.dialog("اشتراک Pro لازم است", "انتقال و ادامهٔ بازی مافیا روی گوشی دیگر به اشتراک فعال Pro نیاز دارد.",
        null, listOf(DialogAction("مشاهدهٔ اشتراک", ButtonKind.PRIMARY) { host.push(ProScreen(host)) }, DialogAction("بستن"))).show()
    return false
}

fun Screen.transferGame(kind: TransferKind, id: Long) {
    if (!canTransfer(kind)) return
    val text = runCatching { GameTransfer(host.repo.store).export(TransferTarget(kind, id), ProState.isPro) }.getOrNull()
    if (text == null) {
        kit.toast("آماده‌کردن فایل ممکن نشد. از ذخیره‌شدن بازی‌ها و حجم فایل مطمئن شوید.")
        return
    }

    val fileName = "Divan-${kind.key}-$id-${System.currentTimeMillis()}.divan.json"
    val sendFile = { host.shareFile(fileName, text, GameTransfer.MIME) }
    val actions = mutableListOf<DialogAction>()
    if (TransferPlatform.available()) actions += DialogAction("ساخت یک کد QR", ButtonKind.PRIMARY) { showTransferQr(text, sendFile) }
    actions += DialogAction("ارسال فایل", if (TransferPlatform.available()) ButtonKind.SECONDARY else ButtonKind.PRIMARY, onClick = sendFile)
    actions += DialogAction("دریافت بازی یا لیگ") { host.push(TransferScreen(host, scanOnOpen = true)) }
    actions += DialogAction("بستن")
    kit.dialog("انتقال ${kind.title}", "کد را فقط برای گیرنده بفرستید. در بازی‌های نقش مخفی، گیرنده باید گرداننده باشد.",
        null, actions).show()
}

private fun Screen.showTransferQr(text: String, sendFile: () -> Unit) {
    val progress = kit.dialog("آماده‌کردن کد انتقال", "ساخت لینک به اینترنت نیاز دارد.", null,
        listOf(DialogAction("ارسال فایل", onClick = sendFile), DialogAction("بستن")))
    progress.show()
    TransferLink.create(text) { link ->
        if (!progress.isShowing || host.current !== this) return@create
        progress.dismiss()
        val local = if (link == null) runCatching { TransferPlatform.frames(text).singleOrNull() }.getOrNull() else null
        val code = link ?: local
        if (code == null) {
            kit.dialog("ساخت لینک ممکن نشد", "برای انتقال یک‌مرحله‌ای فایل بازی را بفرستید، یا پس از اتصال اینترنت دوباره تلاش کنید.",
                null, listOf(DialogAction("ارسال فایل", ButtonKind.PRIMARY, onClick = sendFile), DialogAction("بستن"))).show()
            return@create
        }
        val bitmap = runCatching { TransferPlatform.bitmap(code) }.getOrNull()
        if (bitmap == null) {
            kit.toast("ساخت کد QR ممکن نشد. فایل را بفرستید.")
            return@create
        }
        val body = kit.vertical(Gravity.CENTER_HORIZONTAL)
        val size = (host.resources.displayMetrics.widthPixels - kit.dp(96)).coerceIn(kit.dp(135), kit.dp(420))
        body.addView(ImageView(host).apply {
            setImageBitmap(bitmap)
            contentDescription = "کد QR انتقال بازی"
        }, android.widget.LinearLayout.LayoutParams(size, size))
        kit.dialog("یک اسکن برای انتقال",
            if (link != null) "با دوربین گوشی یا دیوان امتیاز اسکن کنید. کد تا یک ساعت معتبر است."
            else "کد آفلاین را با اسکنر داخل دیوان امتیاز بخوانید.",
            body, listOf(DialogAction("ارسال فایل", onClick = sendFile), DialogAction("بستن"))).show()
    }
}

fun Screen.receiveTransferLink(code: TransferLink.Code) {
    val progress = kit.dialog("دریافت بازی", "در حال دریافت اطلاعات با اینترنت", null, listOf(DialogAction("انصراف")))
    progress.show()
    TransferLink.fetch(code) { text ->
        if (!progress.isShowing || host.current !== this) return@fetch
        progress.dismiss()
        if (text == null) kit.toast("دریافت ممکن نشد. اینترنت یا اعتبار کد را بررسی کنید.")
        else receiveTransfer(text)
    }
}

fun Screen.readTransferFile(uri: Uri) {
    val text = runCatching {
        requireNotNull(host.contentResolver.openInputStream(uri)).use { GameTransfer.read(it).toString(Charsets.UTF_8) }
    }.getOrNull()
    if (text == null) kit.toast("فایل انتقال خوانده نشد یا بیش از ۴ مگابایت است")
    else receiveTransfer(text)
}

private fun Screen.receiveTransfer(text: String) {
    val kind = runCatching { GameTransfer.inspect(text) }.getOrNull()
    if (kind == null) {
        kit.toast("این فایل یا کد، انتقال معتبر دیوان امتیاز نیست")
        return
    }
    if (!canTransfer(kind)) return
    kit.confirm("دریافت ${kind.title}", "یک نسخهٔ قابل ادامه روی این دستگاه ذخیره می‌شود. بازی‌ها و تیم‌های قبلی حفظ می‌شوند.", "دریافت و باز کردن") {
        if (!canTransfer(kind)) return@confirm
        val target = runCatching { GameTransfer(host.repo.store).import(text, ProState.isPro) }.getOrNull()
        if (target == null) {
            kit.toast("انتقال انجام نشد. از گوشی فرستنده فایل تازه بگیرید.")
            return@confirm
        }
        openTransferTarget(target)
    }
}

private fun Screen.openTransferTarget(target: TransferTarget) {
    val id = target.id
    when (target.kind) {
        TransferKind.GAME -> host.repo.session(id)?.let(host::openSession)
        TransferKind.LEAGUE -> host.push(LeagueScreen(host, id))
        TransferKind.PANTO -> host.panto.match(id)?.let { m ->
            host.push(when {
                m.finished -> PantoResultScreen(host, id)
                m.turn?.words?.isNotEmpty() == true -> PantoPlayScreen(host, id)
                else -> PantoBoardScreen(host, id)
            })
        }
        TransferKind.SPY -> host.spy.game(id)?.let { g ->
            host.push(when {
                g.finished -> SpyResultScreen(host, id)
                g.allViewed -> SpyTimerScreen(host, id)
                else -> SpyRevealScreen(host, id)
            })
        }
        TransferKind.MAFIA -> if (canTransfer(target.kind)) host.push(MafiaGameScreen(host, id))
    }
}

class TransferScreen(host: MainActivity, private var scanOnOpen: Boolean = false) : Screen(host) {
    private var scan = TransferPlatform.Scan()

    override fun onShow() {
        if (scanOnOpen) {
            scanOnOpen = false
            if (TransferPlatform.available()) scanNext()
        }
    }

    private fun scanNext() {
        host.scanTransfer { result ->
            if (result != null) {
                val code = TransferLink.read(Uri.parse(result))
                if (code != null) {
                    receiveTransferLink(code)
                    return@scanTransfer
                }
                val incoming = runCatching { scan.add(result) }
                when {
                    incoming.isFailure -> {
                        kit.toast("کد معتبر نیست یا به انتقال دیگری تعلق دارد")
                        if (scan.received == scan.total) scan = TransferPlatform.Scan()
                        host.refresh()
                    }
                    incoming.getOrNull() != null -> {
                        scan = TransferPlatform.Scan()
                        host.refresh()
                        receiveTransfer(incoming.getOrThrow()!!)
                    }
                    else -> host.refresh()
                }
            }
        }
    }

    override fun build(): View = scaffold("انتقال بازی و لیگ", "ادامهٔ داوری روی گوشی دیگر") {
        if (TransferPlatform.available()) {
            addView(kit.button(if (scan.received == 0) "اسکن کد QR" else "اسکن بخش بعدی · ${kit.n(scan.received)} از ${kit.n(scan.total)}",
                ButtonKind.PRIMARY, RoyalIcon.SEARCH) { scanNext() }, kit.spaced(8))
            if (scan.received > 0) addView(kit.button("شروع اسکن از ابتدا", ButtonKind.GHOST, height = 40) {
                scan = TransferPlatform.Scan()
                host.refresh()
            }, kit.spaced(8))
        }
        addView(kit.button("دریافت از فایل", ButtonKind.SECONDARY, RoyalIcon.SAVE) {
            host.pickFile(arrayOf(GameTransfer.MIME, "application/json", "application/octet-stream", "text/plain")) { uri ->
                if (uri != null) readTransferFile(uri)
            }
        }, kit.spaced(10))

        val local = buildList {
            host.repo.leagues().filter { ProState.access("leagues").allowed || GameTransfer.importedLeague(host.repo.store, it.id) }.forEach {
                add(SavedTransfer(TransferTarget(TransferKind.LEAGUE, it.id), it.name, it.game.title, it.updatedAt))
            }
            host.repo.sessions().forEach {
                add(SavedTransfer(TransferTarget(TransferKind.GAME, it.id), it.sides.joinToString(" • ") { side -> side.name }, it.game.title, it.updatedAt))
            }
            host.panto.matches().forEach {
                add(SavedTransfer(TransferTarget(TransferKind.PANTO, it.id), it.teams.joinToString(" • ") { team -> team.name }, TransferKind.PANTO.title, it.updatedAt))
            }
            host.spy.games().forEach {
                add(SavedTransfer(TransferTarget(TransferKind.SPY, it.id), "${kit.n(it.players.size)} بازیکن", TransferKind.SPY.title, it.updatedAt))
            }
            if (ProState.isPro) host.mafia.games().forEach {
                add(SavedTransfer(TransferTarget(TransferKind.MAFIA, it.id), "${kit.n(it.players.size)} بازیکن", TransferKind.MAFIA.title, it.updatedAt))
            }
        }.sortedByDescending { it.updatedAt }
        if (local.isNotEmpty()) {
            addView(kit.section("بازی‌ها و لیگ‌های این دستگاه", RoyalIcon.SHARE))
            var shown = 0
            val rows = kit.vertical()
            val more = kit.button("نمایش موارد بیشتر", ButtonKind.GHOST, height = 44) { }
            fun showPage() {
                local.drop(shown).take(20).forEach { item ->
                    rows.addView(kit.listRow(kit.iconChip(RoyalIcon.SHARE), item.title, item.subtitle) {
                        kit.dialog(item.title, item.subtitle, null, listOf(
                            DialogAction("انتقال به گوشی دیگر", ButtonKind.PRIMARY) { transferGame(item.target.kind, item.target.id) },
                            DialogAction("باز کردن") { openTransferTarget(item.target) },
                            DialogAction("بستن")
                        )).show()
                    }, kit.spaced(5))
                    shown++
                }
                more.visibility = if (shown < local.size) View.VISIBLE else View.GONE
            }
            more.setOnClickListener { kit.tap(it); showPage() }
            addView(rows)
            addView(more)
            showPage()
        }
    }

    private data class SavedTransfer(val target: TransferTarget, val title: String, val subtitle: String, val updatedAt: Long)
}
