package com.meysam.divanemtiaz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

/** Offline certificate, the clock guard, ad frequency rules and plan parsing. */
class ProTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val der = keys.public.encoded

    private fun cert(payload: JSONObject, signer: java.security.PrivateKey = keys.private): String {
        val body = B64.encode(payload.toString().toByteArray(Charsets.UTF_8), url = true)
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(signer); update(body.toByteArray(Charsets.US_ASCII)); sign() }
        return body + "." + B64.encode(sig, url = true)
    }

    private fun payload(now: Long, pro: Boolean = true, lifetime: Boolean = false, exp: Long? = now + 30 * 86400, offline: Long = now + 21 * 86400) =
        JSONObject().put("v", 1).put("uid", 7).put("user", "sara").put("pro", pro).put("lifetime", lifetime).put("plan", "m1")
            .put("exp", exp ?: JSONObject.NULL).put("grace", exp ?: JSONObject.NULL).put("iat", now).put("offline_until", offline)
            .put("features", JSONObject().put("cloud_backup", JSONObject().put("mode", "pro").put("allowed", true)))

    @Test fun webPaymentPagesOnlyOnDomainsTheServerListed() {
        val hosts = setOf("blupal.top", "abangateway.ir")
        assertTrue(ProSync.isPayUrl("https://blupal.top/payment/abc", hosts))
        assertTrue(ProSync.isPayUrl("https://abangateway.ir/pay/inv_9fk2m4qx7t3p8wzy1a6b", hosts))
        assertTrue(ProSync.isPayUrl("https://pay.new-aban.ir/pay/inv_1", setOf("pay.new-aban.ir")))
        assertFalse(ProSync.isPayUrl("http://blupal.top/payment/abc", hosts))
        assertFalse(ProSync.isPayUrl("https://blupal.top.evil.com/payment", hosts))
        assertFalse(ProSync.isPayUrl("https://evil.com/blupal.top", hosts))
        assertFalse(ProSync.isPayUrl("https://user@blupal.top/x", hosts))
        assertFalse(ProSync.isPayUrl("https://blupal.top:8443/x", hosts))
        assertFalse(ProSync.isPayUrl("javascript:alert(1)", hosts))
        assertFalse(ProSync.isPayUrl("https://blupal.top/x", emptySet()))
    }

    @Test fun base64RoundTrip() {
        val data = ByteArray(300) { (it * 37).toByte() }
        assertTrue(data.contentEquals(B64.decode(B64.encode(data))))
        assertTrue(data.contentEquals(B64.decode(B64.encode(data, url = true))))
        assertEquals("aGk", B64.encode("hi".toByteArray(), url = true))
    }

    @Test fun validCertificateGivesProUntilTheEnd() {
        val now = 1_800_000_000L
        val lic = License.parse(cert(payload(now)), der, 7)
        assertTrue(lic.valid)
        assertTrue(lic.proAt(now))
        assertEquals(30, lic.daysLeft(now))
        assertTrue(lic.proAt(now + 20 * 86400))
        // The offline window (21 days) ends before the paid month: the app must sync once.
        assertFalse(lic.proAt(now + 22 * 86400))
        assertTrue(lic.needsSync(now + 22 * 86400))
        assertFalse(lic.proAt(now + 31 * 86400))
        assertEquals("pro", lic.features.getJSONObject("cloud_backup").getString("mode"))
    }

    @Test fun lifetimeHasNoEnd() {
        val now = 1_800_000_000L
        val lic = License.parse(cert(payload(now, lifetime = true, exp = null, offline = now + 365 * 86400)), der, 7)
        assertTrue(lic.proAt(now + 300 * 86400))
        assertNull(lic.daysLeft(now))
        // 5.4: lifetime never depends on the offline window, even with a certificate from an older server.
        assertTrue(lic.proAt(now + 20L * 365 * 86400))
        assertFalse(lic.needsSync(now + 20L * 365 * 86400))
        assertFalse(lic.offlineLimitShown())
        val short = License.parse(cert(payload(now, lifetime = true, exp = null, offline = now + 7 * 86400).put("device", "aa11")), der, 7, "aa11")
        assertTrue(short.proAt(now + 400 * 86400))
        // …but only on the phone it was signed for.
        assertFalse(License.parse(cert(payload(now, lifetime = true, exp = null, offline = 4102444800L).put("device", "aa11")), der, 7, "bb22").proAt(now))
        // A revoked lifetime (server says pro=false) gives nothing.
        assertFalse(License.parse(cert(payload(now, pro = false, lifetime = true, exp = null)), der, 7).proAt(now))
    }

    @Test fun timedPlansEndExactly() {
        val now = 1_800_000_000L
        for (days in listOf(1, 30, 90, 180)) {
            val end = now + days * 86400L
            // Offline window up to the very end (server: min(end, now + offline days)).
            val lic = License.parse(cert(payload(now, exp = end, offline = end)), der, 7)
            assertTrue("$days days: Pro just before the end", lic.proAt(end - 1))
            assertFalse("$days days: no Pro at the end", lic.proAt(end))
            assertFalse("$days days: no Pro after the end", lic.proAt(end + 86400))
            assertFalse(lic.needsSync(end + 1))
            assertEquals(days, lic.daysLeft(now))
            assertFalse(lic.offlineLimitShown())
        }
        // Shorter offline window: the player must connect once; reconnecting never moves the paid end.
        val lic = License.parse(cert(payload(now, exp = now + 90 * 86400L, offline = now + 7 * 86400L)), der, 7)
        assertTrue(lic.offlineLimitShown())
        assertFalse(lic.proAt(now + 7 * 86400L))
        assertTrue(lic.needsSync(now + 7 * 86400L))
        // Grace exists only for self-renewing store subscriptions (server sends grace = exp otherwise).
        val renewing = License.parse(cert(payload(now, exp = now + 30 * 86400L, offline = now + 33 * 86400L).put("grace", now + 33 * 86400L)), der, 7)
        assertTrue(renewing.proAt(now + 31 * 86400L))
        assertFalse(renewing.proAt(now + 33 * 86400L))
    }

    @Test fun forgedOrForeignCertificatesAreRejected() {
        val now = 1_800_000_000L
        val good = cert(payload(now))
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertFalse(License.parse(cert(payload(now), other.private), der, 7).valid)
        val body = good.substringBefore('.')
        val edited = B64.encode(String(B64.decode(body), Charsets.UTF_8).replace("\"lifetime\":false", "\"lifetime\":true").toByteArray(), url = true)
        assertFalse(License.parse(edited + "." + good.substringAfter('.'), der, 7).valid)
        assertEquals("user", License.parse(good, der, 8).reason)
        assertFalse(License.parse("garbage", der, 7).valid)
        assertFalse(License.parse(null, der, 7).proAt(now))
    }

    @Test fun certificateWorksOnlyOnItsPhone() {
        val now = 1_800_000_000L
        val c = cert(payload(now).put("device", "aa11"))
        assertTrue(License.parse(c, der, 7, "aa11").proAt(now))
        val other = License.parse(c, der, 7, "bb22")
        assertTrue(other.valid)
        assertFalse(other.proAt(now))
        assertEquals("device", other.reason)
        val conflict = License.parse(cert(payload(now, pro = false).put("device", "bb22").put("device_conflict", JSONObject().put("name", "Samsung A54").put("since", now))), der, 7, "bb22")
        assertEquals("Samsung A54", conflict.elsewhere)
        assertFalse(conflict.proAt(now))
    }

    @Test fun clockCannotBeWoundBack() {
        val store = HashMap<String, Long>()
        val clock = TrustedClock({ store[it] ?: 0L }, { k, v -> store[k] = v })
        val real = 1_800_000_000_000L
        assertEquals(real / 1000, clock.now(real, 10_000))
        // Same boot, wall clock set back a month: time still moves with uptime.
        assertEquals(real / 1000 + 60, clock.now(real - 30L * 86400_000, 70_000))
        assertTrue(clock.tampered(real - 30L * 86400_000))
        // After a reboot with the clock still set back, time never goes below what was seen.
        assertTrue(clock.now(real - 30L * 86400_000, 5_000) >= real / 1000 + 60)
        // The server's time moves the anchor forward.
        clock.anchor(real / 1000 + 5000, 6_000)
        assertTrue(clock.now(real, 6_000) >= real / 1000 + 5000)
        // Phone off and the clock set back after every boot: time still never goes backwards, and moves on with uptime.
        val before = clock.now(real, 6_000)
        val afterBoot = clock.now(real - 90L * 86400_000, 1_000)
        assertTrue(afterBoot >= before)
        assertTrue(clock.now(real - 90L * 86400_000, 3_601_000) >= afterBoot + 3600)
        // An expired 30-day pass stays expired whatever the device clock says.
        val now = real / 1000
        val lic = License.parse(cert(payload(now, exp = now + 30 * 86400L, offline = now + 30 * 86400L)), der, 7)
        val later = clock.now(real + 31L * 86400_000, 10_000_000)
        assertFalse(lic.proAt(later))
        assertFalse(lic.proAt(clock.now(real, 10_100_000)))
    }

    @Test fun adFrequencyRules() {
        val ad = DirectAd(1, "card", "t", "b", null, null, null, null, null, 0, 0, 0, true, 2, 60, 0, listOf("home_inline"), null)
        val now = 1_800_000_000L
        val day = now / 86400
        assertTrue(AdRules.eligible(ad, "home_inline", listOf("card"), now, null))
        assertFalse(AdRules.eligible(ad, "history_inline", listOf("card"), now, null))
        assertFalse(AdRules.eligible(ad, "home_inline", listOf("text"), now, null))
        assertFalse(AdRules.eligible(ad, "home_inline", listOf("card"), now, AdRules.Seen(day, 2, now - 3600)))
        assertTrue(AdRules.eligible(ad, "home_inline", listOf("card"), now, AdRules.Seen(day - 1, 9, now - 3600)))
        assertFalse(AdRules.eligible(ad, "home_inline", listOf("card"), now, AdRules.Seen(day, 1, now - 30)))
        assertFalse(AdRules.eligible(ad.copy(endsAt = now - 1), "home_inline", listOf("card"), now, null))
        assertTrue(AdRules.interstitialAllowed(now, now - 3600, 0, day, 10, 3))
        assertFalse(AdRules.interstitialAllowed(now, now - 300, 0, day, 10, 3))
        assertFalse(AdRules.interstitialAllowed(now, now - 3600, 3, day, 10, 3))
        assertTrue(AdRules.interstitialAllowed(now, now - 3600, 3, day - 1, 10, 3))
        assertFalse(AdRules.interstitialAllowed(now, 0, 0, -1, 10, 0))
    }

    @Test fun planOffersFromServer() {
        val j = JSONObject("""{"code":"m3","title":"اشتراک سه‌ماهه","description":"x","duration_days":90,"lifetime":false,"base_price":49000,"final_price":39000,
            "discount_percent":20,"badge_text":"محبوب‌ترین","badge_color":"#2D63E6","campaign_ends_at":1800086400,"renewal_note":null,"purchasable":false,"state":"price_sync_pending"}""")
        val p = PlanOffer.from(j)
        assertEquals(90, p.days)
        assertEquals(39000, p.finalPrice)
        assertEquals(20, p.discountPercent)
        assertFalse(p.purchasable)
        assertEquals("price_sync_pending", p.state)
        assertNull(p.renewalNote)
        val life = PlanOffer.from(JSONObject("""{"code":"life","title":"x","duration_days":null,"lifetime":true,"base_price":220000,"final_price":220000,"purchasable":true,"state":"ok"}"""))
        assertTrue(life.lifetime)
        assertNull(life.days)
    }

    @Test fun storePriceText() {
        assertEquals(19000, PlanOffer.storeToman("۱۹٬۰۰۰ تومان"))
        assertEquals(49000, PlanOffer.storeToman("49,000 تومان"))
        assertEquals(19000, PlanOffer.storeToman("190000 ریال"))
        assertNull(PlanOffer.storeToman("رایگان"))
    }

    @Test fun proRulesFromPanel() {
        ProState.logout()
        fun f(mode: String, items: List<String> = emptyList()) = JSONObject().put("mode", mode).put("allowed", mode == "free").put("items", org.json.JSONArray(items)).put("pro_items", org.json.JSONArray(items))
        ProState.configJson = JSONObject().put("features", JSONObject()
            .put("mafia", f("pro"))
            .put("mafia_scenarios", f("pro"))
            .put("mafia_last_cards", f("free", listOf("SILENCE")))
            .put("themes", f("pro", listOf("neon")))
            .put("panto_modes", f("disabled"))).toString()
        // A whole section made Pro asks a free user to buy.
        assertFalse(ProState.feature("mafia").second)
        // Every scenario is Pro when the section is Pro and no single scenario is ticked.
        assertTrue(Gate.itemLocked("mafia_scenarios", "classic"))
        assertTrue(Gate.itemLocked("mafia_scenarios", "godfather"))
        // Ticked items only: the rest stays free and the section stays open.
        assertTrue(Gate.itemLocked("mafia_last_cards", "SILENCE"))
        assertFalse(Gate.itemLocked("mafia_last_cards", "BEAUTIFUL_MIND"))
        assertTrue(ProState.feature("themes").second)
        assertTrue(Gate.itemLocked("themes", "neon"))
        assertFalse(Gate.itemLocked("themes", "cobalt"))
        assertTrue(Gate.itemLocked("panto_modes", "NORMAL"))
        // Unknown modules stay free.
        assertTrue(ProState.feature("spy").second)
        ProState.configJson = null
    }

    @Test fun receiptsSurviveStorage() {
        val r = StoreReceipt("divan_pro_1m", "tok", "ORD-1", "{\"a\":1}", "sig", "dvabc", 123L)
        assertEquals(r, StoreReceipt.from(JSONObject(r.toJson().toString())))
    }
}
