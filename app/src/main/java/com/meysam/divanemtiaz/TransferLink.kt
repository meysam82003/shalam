package com.meysam.divanemtiaz

import android.net.Uri
import org.json.JSONObject

/** The fragment travels with the QR and stays out of the landing-page request. */
object TransferLink {
    private const val PAGE = "https://divan-emtiaz.ir/divan/transfer/"
    private val fragment = Regex("([a-f0-9]{32})\\.([A-Za-z0-9_-]{43})")
    data class Code(val id: String, val key: String)

    fun read(uri: Uri?): Code? {
        if (uri == null) return null
        val path = uri.path.orEmpty()
        val isSite = uri.scheme == "https" && uri.host == "divan-emtiaz.ir" && path == "/divan/transfer/" && uri.port == -1
        val isApp = uri.scheme == "divan" && uri.host == "transfer" && path == "/"
        if (!isSite && !isApp) return null
        val match = fragment.matchEntire(uri.fragment.orEmpty()) ?: return null
        return Code(match.groupValues[1], match.groupValues[2])
    }

    fun create(text: String, done: (String?) -> Unit) {
        TransferPlatform.seal(text) { key, sealed ->
            if (key == null || sealed == null) done(null)
            else Api.call("POST", "v1/game-transfer", JSONObject().put("payload", sealed), null) { data, _ ->
                val id = data?.optString("id").orEmpty()
                val value = "$id.$key"
                done(if (fragment.matches(value)) "$PAGE#$value" else null)
            }
        }
    }

    fun fetch(code: Code, done: (String?) -> Unit) {
        Api.call("GET", "v1/game-transfer", JSONObject().put("id", code.id), null) { data, _ ->
            val payload = data?.optString("payload").orEmpty()
            if (payload.isEmpty()) done(null)
            else TransferPlatform.unseal(code.key, payload, done)
        }
    }
}
