package com.meysam.divanemtiaz

import android.app.Dialog
import android.view.View
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject

/** Four independent rule snapshots per game. No game, roster, entitlement or theme is stored here. */
class RulePresetBook(private val store: KeyValueStore) {
    data class Entry(val slot: Int, val name: String, val rules: JSONObject)
    fun entries(scope: String): List<Entry> {
        require(scope in TITLES)
        val a = export().optJSONArray(scope) ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val slot = o.optInt("slot", -1)
            val rules = o.optJSONObject("rules") ?: return@mapNotNull null
            if (slot !in 0..3) null else Entry(slot, o.optString("name").take(40), JSONObject(rules.toString()))
        }.distinctBy { it.slot }
    }
    fun save(scope: String, slot: Int, name: String, rules: JSONObject) {
        require(scope in TITLES && slot in 0..3 && name.trim().isNotEmpty())
        require(rules.toString().length < 100_000)
        val root = export()
        val entries = entries(scope).filter { it.slot != slot } + Entry(slot, name.trim().take(40), JSONObject(rules.toString()))
        root.put(scope, JSONArray().apply { entries.sortedBy { it.slot }.forEach { put(JSONObject().put("slot", it.slot).put("name", it.name).put("rules", it.rules)) } })
        store.write(mapOf(KEY to root.toString()))
    }
    fun delete(scope: String, slot: Int) {
        require(scope in TITLES && slot in 0..3)
        val root = export()
        root.put(scope, JSONArray().apply { entries(scope).filter { it.slot != slot }.forEach { put(JSONObject().put("slot", it.slot).put("name", it.name).put("rules", it.rules)) } })
        store.write(mapOf(KEY to root.toString()))
    }
    fun export(): JSONObject = runCatching { JSONObject(store.get(KEY) ?: "{}") }.getOrDefault(JSONObject())
    companion object {
        const val KEY = "rule_presets_v1"
        val TITLES = linkedMapOf("shalam" to "شلم", "menfi" to "منفی", "hezartaii" to "هزارتایی", "dolo" to "دو لو گشنیز", "league" to "لیگ", "mafia" to "راوی مافیا", "panto" to "پانتومیم", "spy" to "جاسوس")
        fun merge(existing: JSONObject, incoming: JSONObject, replace: Boolean): JSONObject {
            require(incoming.toString().length < 800_000)
            val result = if (replace) JSONObject() else JSONObject(existing.toString())
            for (scope in TITLES.keys) {
                val source = incoming.optJSONArray(scope) ?: continue
                require(source.length() <= 4)
                val slots = HashSet<Int>()
                val merged = if (replace) JSONArray() else result.optJSONArray(scope) ?: JSONArray()
                val occupied = (0 until merged.length()).map { merged.getJSONObject(it).getInt("slot") }.toSet()
                for (i in 0 until source.length()) {
                    val value = source.getJSONObject(i)
                    val slot = value.getInt("slot")
                    require(slot in 0..3 && slots.add(slot) && value.getString("name").trim().isNotEmpty() && value.getString("name").length <= 40)
                    require(value.getJSONObject("rules").toString().length < 100_000)
                    if (slot !in occupied) merged.put(JSONObject(value.toString()))
                }
                result.put(scope, merged)
            }
            return result
        }
    }
}

object RulePresetPolicy {
    private val mafiaKeys=listOf("speak","challenge","defence","query","edgeUp","capo","selfKill","screen","vibrate")
    fun captureMafia(settings:MafiaSettings):JSONObject {
        val all=MafiaCodec.encodeSettings(settings)
        return JSONObject().apply{mafiaKeys.forEach{put(it,all.get(it))}}
    }
    fun applyMafia(settings:MafiaSettings,rules:JSONObject):MafiaSettings {
        val all=MafiaCodec.encodeSettings(settings)
        mafiaKeys.forEach{if(rules.has(it))all.put(it,rules.get(it))}
        return MafiaCodec.decodeSettings(all)
    }
    private fun keys(scope: String): List<String> = when (scope) {
        "shalam" -> listOf("shalam")
        "menfi" -> listOf("menfi")
        "hezartaii" -> listOf("hezar", "deck")
        "dolo" -> listOf("dolo")
        "league" -> listOf("league")
        else -> emptyList()
    }
    fun capture(settings: AppSettings, scope: String): JSONObject {
        val encoded = GameCodec.encodeSettings(settings)
        return JSONObject().apply { keys(scope).forEach { put(it, encoded.getJSONObject(it)) } }
    }
    fun apply(settings: AppSettings, scope: String, rules: JSONObject): AppSettings {
        val encoded = GameCodec.encodeSettings(settings)
        keys(scope).forEach { k -> rules.optJSONObject(k)?.let { encoded.put(k, JSONObject(it.toString())) } }
        return GameCodec.decodeSettings(encoded)
    }
}

fun Screen.captureRulePreset(scope: String): JSONObject = when (scope) {
    "mafia" -> RulePresetPolicy.captureMafia(host.mafia.settings())
    "panto" -> host.panto.defaults().let { JSONObject().put("rounds", it.rounds).put("manual", it.manualTime).put("seconds", it.seconds).put("extraTime", it.extraTimeSeconds) }
    "spy" -> host.spy.settings().let { JSONObject().put("players", it.players).put("spies", it.spies).put("minutes", it.minutes).put("hint", it.spyHint).put("questionTurns", it.questionTurns) }
    else -> RulePresetPolicy.capture(settings, scope)
}
fun Screen.defaultRulePreset(scope: String): JSONObject = when (scope) {
    "mafia" -> RulePresetPolicy.captureMafia(MafiaSettings())
    "panto" -> PantoDefaults().let { JSONObject().put("rounds", it.rounds).put("manual", it.manualTime).put("seconds", it.seconds).put("extraTime", it.extraTimeSeconds) }
    "spy" -> SpySettings().let { JSONObject().put("players", it.players).put("spies", it.spies).put("minutes", it.minutes).put("hint", it.spyHint).put("questionTurns", it.questionTurns) }
    else -> RulePresetPolicy.capture(AppSettings(), scope).apply { if (scope == "league") put("_factory", true) }
}
fun Screen.applyRulePreset(scope: String, rules: JSONObject) {
    when (scope) {
        "mafia" -> host.mafia.saveSettings(RulePresetPolicy.applyMafia(host.mafia.settings(),rules))
        "panto" -> host.panto.saveDefaults(PantoDefaults(rules.optInt("rounds", 3), rules.optBoolean("manual"), rules.optInt("seconds", 45), rules.optInt("extraTime")).sane())
        "spy" -> host.spy.saveSettings(SpySettings(rules.optInt("players", 6).coerceIn(SpyEngine.MIN_PLAYERS, SpyEngine.MAX_PLAYERS), rules.optInt("spies", 1).coerceAtLeast(1).coerceAtMost(rules.optInt("players", 6).coerceAtLeast(2) - 1), rules.optInt("minutes", 5).coerceIn(SpyEngine.MIN_MINUTES, SpyEngine.MAX_MINUTES), rules.optBoolean("hint"), rules.optBoolean("questionTurns")))
        else -> host.updateSettings { RulePresetPolicy.apply(it, scope, rules) }
    }
}

fun Screen.rulePresetButton(scope: String, capture: () -> JSONObject = { captureRulePreset(scope) }, apply: (JSONObject) -> Unit = { applyRulePreset(scope, it); host.refresh() }): View =
    kit.button("تنظیمات ذخیره‌شده • ۴ جایگاه", ButtonKind.SECONDARY, RoyalIcon.SETTINGS, 46) { rulePresetDialog(scope, capture, apply) }

private fun Screen.rulePresetDialog(scope: String, capture: () -> JSONObject, apply: (JSONObject) -> Unit) {
    val book = RulePresetBook(host.repo.store)
    lateinit var dialog: Dialog
    fun save(slot: Int, old: RulePresetBook.Entry?) {
        val name = kit.field("نام تنظیمات", old?.name ?: "ترکیب ${kit.n(slot + 1)}")
        kit.dialog("ذخیرهٔ تنظیمات ${RulePresetBook.TITLES[scope]}", "قوانین و امتیازهای فعلی در این جایگاه ذخیره می‌شوند.", name, listOf(
            DialogAction("ذخیره", ButtonKind.PRIMARY, dismiss = false) {
                if (name.text.toString().trim().isBlank()) kit.toast("نام تنظیمات را وارد کنید")
                else {
                    book.save(scope, slot, name.text.toString(), capture())
                    kit.dismissDialogs()
                    rulePresetDialog(scope, capture, apply)
                }
            }, DialogAction("انصراف")
        )).show()
    }
    val body = kit.vertical().apply {
        addView(kit.button("پیش‌فرض برنامه", ButtonKind.PRIMARY, RoyalIcon.UNDO, 46) { dialog.dismiss(); apply(defaultRulePreset(scope)) }, kit.spaced(12))
        val entries = book.entries(scope)
        for (slot in 0..3) {
            val saved = entries.firstOrNull { it.slot == slot }
            addView(kit.panel(PanelStyle.NORMAL, 12).apply {
                addView(kit.text("${kit.n(slot + 1)} • ${saved?.name ?: "جایگاه خالی"}", TextStyle.BODY_BOLD, Royal.goldLight))
                addView(kit.horizontal().apply {
                    if (saved != null) {
                        addView(kit.weight(kit.button("انتخاب", ButtonKind.SUCCESS, RoyalIcon.CHECK, 40) { dialog.dismiss(); apply(JSONObject(saved.rules.toString())) }))
                        addView(kit.hgap(6))
                    }
                    addView(kit.weight(kit.button(if (saved == null) "ذخیرهٔ فعلی" else "جایگزینی", ButtonKind.SECONDARY, RoyalIcon.EDIT, 40) { save(slot, saved) }))
                    if (saved != null) {
                        addView(kit.hgap(6))
                        addView(kit.iconButton(RoyalIcon.TRASH, "حذف ترکیب", ButtonKind.SECONDARY, 40) { kit.confirm("حذف تنظیمات ذخیره‌شده", "فقط همین ترکیب حذف شود؟", "حذف", true) { book.delete(scope, slot); dialog.dismiss(); rulePresetDialog(scope, capture, apply) } })
                    }
                })
            }, kit.spaced(8))
        }
    }
    dialog = kit.dialog("تنظیمات ${RulePresetBook.TITLES[scope]}", "چهار ترکیب مستقل؛ انتخاب تنظیمات، نتیجه‌های قبلی را تغییر نمی‌دهد.", body, listOf(DialogAction("بستن")))
    dialog.show()
}
