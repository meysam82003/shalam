package com.meysam.divanemtiaz

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class MafiaAccessTest {
    @Test fun coldStartAndBrokenConfigNeverOpenPaidFeatures() {
        ProState.logout()
        for (config in listOf(null, "broken", "{}", """{"features":{"photos":{}}}""")) {
            ProState.configJson = config
            for (key in listOf("mafia", "mafia_scenarios", "mafia_last_cards", "mafia_audio", "mafia_custom", "mafia_report", "mafia_groups", "photos", "share", "leagues", "rankings", "profiles", "cloud_backup", "panto_words", "spy_words", "future_unknown")) {
                assertFalse("$key must be closed for config=$config", ProState.access(key).allowed)
            }
            for (key in listOf("shalam", "menfi", "hezartaii", "dolo", "panto", "spy", "backup", "deck_calc", "panto_modes")) assertTrue(key, ProState.access(key).allowed)
            assertEquals(30, ProState.access("history").limit)
            assertEquals(30, ProState.access("charts").limit)
            assertEquals(setOf("iranic", "ivory", "neon"), ProState.access("themes").items)
        }
        ProState.configJson = """{"features":{"mafia":{"mode":"free"},"mafia_scenarios":{"mode":"free","items":[]}}}"""
        assertFalse(ProState.access("mafia").allowed)
        assertFalse(ProState.access("mafia_scenarios").allowed)
        ProState.configJson = null
    }

    @Test fun monotonicLeaseDoesNotRestartOnRefreshOrReconnect() {
        val lease = MafiaTrialLease()
        assertEquals(0L, lease.remaining(7, 1600, 1000))
        lease.accept(7, 1600, 600, 1000)
        assertEquals(600L, lease.remaining(7, 1600, 1000))
        assertEquals(1L, lease.remaining(7, 1600, 600000))
        assertEquals(0L, lease.remaining(7, 1600, 601000))
        lease.accept(7, 1600, 600, 700000)
        assertEquals(0L, lease.remaining(7, 1600, 700000))
        assertEquals(0L, lease.remaining(8, 1600, 1000))
        assertEquals(0L, MafiaTrialLease().remaining(7, 1600, 1000))
    }

    @Test fun proofIsSignedBoundAndExpiresExactlyAt600Seconds() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        fun cert(p: JSONObject): String {
            val body = B64.encode(p.toString().toByteArray(), true)
            val sig = Signature.getInstance("SHA256withRSA").run { initSign(keys.private); update(body.toByteArray()); sign() }
            return "$body.${B64.encode(sig, true)}"
        }
        val p = JSONObject().put("uid", 7).put("device", "phone-a").put("pro", false)
            .put("mafia_trial", JSONObject().put("started_at", 1000).put("expires_at", 1600))
        val proof = cert(p)
        val lic = License.parse(proof, keys.public.encoded, 7, "phone-a")
        assertFalse(lic.mafiaTrialAt(999))
        assertTrue(lic.mafiaTrialAt(1000))
        assertTrue(lic.mafiaTrialAt(1599))
        assertFalse(lic.mafiaTrialAt(1600))
        assertFalse(lic.proAt(1000))
        assertFalse(License.parse(proof, keys.public.encoded, 8, "phone-a").mafiaTrialAt(1000))
        assertFalse(License.parse(proof, keys.public.encoded, 7, "phone-b").mafiaTrialAt(1000))
        assertFalse(License.parse(proof, keys.public.encoded, 7, null).mafiaTrialAt(1000))
        p.getJSONObject("mafia_trial").put("expires_at", 1601)
        assertFalse(License.parse(cert(p), keys.public.encoded, 7, "phone-a").mafiaTrialAt(1000))
        p.remove("device")
        assertFalse(License.parse(cert(p), keys.public.encoded, 7, "phone-a").mafiaTrialAt(1000))
        val pending = JSONObject().put("uid", 7).put("device", "phone-a")
            .put("mafia_trial", JSONObject().put("started_at", JSONObject.NULL).put("expires_at", JSONObject.NULL))
        val eligible = License.parse(cert(pending), keys.public.encoded, 7, "phone-a")
        assertTrue(eligible.mafiaTrialEligible)
        assertFalse(eligible.mafiaTrialAt(1000))
        pending.put("mafia_trial", JSONObject())
        assertFalse(License.parse(cert(pending), keys.public.encoded, 7, "phone-a").mafiaTrialEligible)
    }
}
