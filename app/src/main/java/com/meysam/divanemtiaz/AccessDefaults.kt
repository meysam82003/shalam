package com.meysam.divanemtiaz

import org.json.JSONArray
import org.json.JSONObject

/** Cold-start policy, matching the owner's panel screenshots (2026-10-18).
 * No network is needed for explicitly free games. Unknown modules never default to free.
 * Mafia is evaluated separately using its signed trial/Pro entitlement.
 */
object AccessDefaults {
    private val free = setOf("shalam", "menfi", "hezartaii", "dolo", "panto", "panto_modes", "spy", "backup", "deck_calc")
    private val pro = setOf("panto_words", "spy_words", "leagues", "rankings", "profiles", "photos", "cloud_backup", "share")
    fun policy(key: String): JSONObject = when {
        key in free -> JSONObject().put("mode", "free")
        key == "history" || key == "charts" -> JSONObject().put("mode", "limited").put("limit", 30)
        key == "themes" -> JSONObject().put("mode", "pro").put("items", JSONArray(listOf("iranic", "ivory", "neon")))
        key in pro || key == "mafia" || key.startsWith("mafia_") -> JSONObject().put("mode", "pro")
        else -> JSONObject().put("mode", "disabled")
    }

    fun valid(policy: JSONObject): Boolean = policy.optString("mode") in setOf("free", "pro", "gift", "disabled") ||
        (policy.optString("mode") == "limited" && policy.has("limit") && !policy.isNull("limit") && policy.optInt("limit", -1) >= 0)
}
