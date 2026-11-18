package com.meysam.divanemtiaz

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Fixture signatures exist only in androidTest. No production certificates, publisher events or balances. */
@RunWith(AndroidJUnit4::class)
class RewardActivationSmokeTest {
    private lateinit var scenario:ActivityScenario<MainActivity>
    private val keys=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
    private lateinit var oldPin:String
    private fun pin(value:String){StoreInfo.javaClass.getDeclaredField("pinnedKey").apply{isAccessible=true}.set(StoreInfo,value)}
    @Before fun start(){
        Api.transport={_,_,_,_->JSONObject().put("ok",true).put("data",JSONObject()).put("server_time",System.currentTimeMillis()/1000)}
        scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity{host->
            oldPin=StoreInfo.pinnedKey;pin(B64.encode(keys.public.encoded));ProState.logout()
            Notices.markAsked(host);host.updateSettings{it.copy(general=it.general.copy(animations=false))}
            ProState.userId=987654;ProState.username="reward_ui_fixture";ProState.token="fixture-session"
            host.resetTo(HomeScreen(host))
        }
    }
    @After fun finish(){scenario.onActivity{ProState.logout();pin(oldPin)};scenario.close();Api.transport=null}
    private fun response(end:Long,forged:Boolean=false):JSONObject {
        val now=ProState.now()
        val payload=JSONObject().put("uid",987654).put("user","reward_ui_fixture").put("device",ProState.deviceId)
            .put("pro",true).put("plan","gift").put("exp",end).put("grace",end+3*86400).put("iat",now).put("offline_until",end).put("reward_until",end)
        val body=B64.encode(payload.toString().toByteArray(Charsets.UTF_8),url=true)
        val signer=if(forged)KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair().private else keys.private
        val sig=Signature.getInstance("SHA256withRSA").run{initSign(signer);update(body.toByteArray(Charsets.US_ASCII));sign()}
        return JSONObject().put("user",JSONObject().put("id",987654).put("username","reward_ui_fixture")).put("certificate",body+"."+B64.encode(sig,url=true))
    }
    private fun awaitText(text:String):Boolean {
        val ui=InstrumentationRegistry.getInstrumentation().uiAutomation
        val end=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<end){if(ui.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.isNotEmpty()==true)return true;SystemClock.sleep(100)}
        return false
    }
    @Test fun verifiedRewardUnlocksMafiaAndExpiresWithCountdownAndSavedData(){
        var end=0L;var snapshot=""
        scenario.onActivity{host->
            end=ProState.now()+12
            snapshot=host.repo.store.get("fixture_saved_game").orEmpty()
            host.repo.store.write(mapOf("fixture_saved_game" to "{\"score11\":33,\"round\":7}"))
            ProState.applyAccount(response(end))
            assertTrue(ProState.isPro);assertTrue(ProState.mafiaAllowed);assertFalse(ProState.adsAllowed)
            host.resetTo(HomeScreen(host),SubscriptionScreen(host))
        }
        assertTrue("Actual native subscription UI shows the ticking verified entitlement",awaitText("زمان باقی‌ماندهٔ Pro"))
        SystemClock.sleep(1500)
        scenario.onActivity{assertTrue(ProState.isPro);assertTrue(ProState.license.remainingSeconds(ProState.now())!! in 1..11)}
        assertTrue("Respectful expiry notice appears automatically",awaitExpiry(end))
        scenario.onActivity{host->
            assertFalse(ProState.isPro);assertFalse(ProState.mafiaAllowed)
            assertTrue(host.current is HomeScreen)
            assertEquals("{\"score11\":33,\"round\":7}",host.repo.store.get("fixture_saved_game"))
            assertFalse("Only one expiry notice for this grant",ProState.consumeExpiredReward())
            host.repo.store.write(mapOf("fixture_saved_game" to snapshot.takeIf{it.isNotBlank()}))
        }
    }
    private fun awaitExpiry(end:Long):Boolean {
        val until=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<until){if(awaitText("سپاس از همراهی شما"))return true}
        return false
    }
    @Test fun claimNeverReportsSuccessForAnUnverifiedCertificate(){
        val done=CountDownLatch(1);val accepted=AtomicBoolean(true)
        scenario.onActivity{host->
            val data=response(ProState.now()+3600,forged=true)
            Api.transport={_,_,_,_->JSONObject().put("ok",true).put("data",data).put("server_time",ProState.now())}
            AdsManager.completeDirect(host,JSONObject().put("watch_token","invalid_fixture_"+System.nanoTime()).put("claim_after",0).put("expires_at",ProState.now()+3600)){ok,message->
                accepted.set(ok);assertTrue(message.contains("هنوز تأیید نشده"));done.countDown()
            }
        }
        assertTrue(done.await(5,TimeUnit.SECONDS));assertFalse(accepted.get())
        scenario.onActivity{assertFalse(ProState.isPro)}
    }
    @Test fun verifiedClaimAcknowledgesActualOneHourAccess(){
        val done=CountDownLatch(1);val accepted=AtomicBoolean(false)
        scenario.onActivity{host->
            val data=response(ProState.now()+3600)
            Api.transport={_,_,_,_->JSONObject().put("ok",true).put("data",data).put("server_time",ProState.now())}
            AdsManager.completeDirect(host,JSONObject().put("watch_token","valid_fixture_"+System.nanoTime()).put("claim_after",0).put("expires_at",ProState.now()+3600)){ok,message->
                accepted.set(ok);assertEquals("یک ساعت Pro فعال شد.",message);done.countDown()
            }
        }
        assertTrue(done.await(5,TimeUnit.SECONDS));assertTrue(accepted.get())
        scenario.onActivity{assertTrue(ProState.isPro);assertTrue(ProState.mafiaAllowed);assertTrue(ProState.license.remainingSeconds(ProState.now())!! in 3595..3600)}
    }
}
