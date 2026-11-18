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
    private lateinit var host:MainActivity
    private val keys=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
    private lateinit var oldPin:String
    private var clockMax=0L;private var clockElapsed=0L
    private fun pin(value:String){StoreInfo.javaClass.getDeclaredField("pinnedKey").apply{isAccessible=true}.set(StoreInfo,value)}
    @Before fun start(){
        Api.transport={_,_,_,_->JSONObject().put("ok",true).put("data",JSONObject()).put("server_time",System.currentTimeMillis()/1000)}
        scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity{activity->
            host=activity
            oldPin=StoreInfo.pinnedKey;pin(B64.encode(keys.public.encoded));ProState.logout()
            clockMax=ProState.getL("clock_max");clockElapsed=ProState.getL("clock_elapsed")
            Notices.markAsked(host);host.updateSettings{it.copy(general=it.general.copy(animations=false))}
            ProState.userId=987654;ProState.username="reward_ui_fixture";ProState.token="fixture-session"
            host.resetTo(HomeScreen(host))
        }
    }
    @After fun finish(){onMain{ProState.logout();pin(oldPin);ProState.putL("clock_max",clockMax);ProState.putL("clock_elapsed",clockElapsed);ProState.putL("reward_observed_987654",0);ProState.putL("reward_notified_987654",0)};scenario.close();Api.transport=null}
    private fun response(end:Long,forged:Boolean=false,pro:Boolean=true,plan:String="gift",rewardUntil:Long=end):JSONObject {
        val now=ProState.now()
        val payload=JSONObject().put("uid",987654).put("user","reward_ui_fixture").put("device",ProState.deviceId)
            .put("pro",pro).put("plan",plan).put("exp",end).put("grace",end+3*86400).put("iat",now).put("offline_until",end).put("reward_until",rewardUntil)
        val body=B64.encode(payload.toString().toByteArray(Charsets.UTF_8),url=true)
        val signer=if(forged)KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair().private else keys.private
        val sig=Signature.getInstance("SHA256withRSA").run{initSign(signer);update(body.toByteArray(Charsets.US_ASCII));sign()}
        return JSONObject().put("user",JSONObject().put("id",987654).put("username","reward_ui_fixture")).put("certificate",body+"."+B64.encode(sig,url=true))
    }
    private fun onMain(action:(MainActivity)->Unit){
        var failure:Throwable?=null
        InstrumentationRegistry.getInstrumentation().runOnMainSync{try{action(host)}catch(t:Throwable){failure=t}}
        failure?.let{throw it}
    }
    private fun timerText():String {
        var found=""
        fun visit(v:android.view.View){
            if(v is android.widget.TextView&&v.text.toString().startsWith("زمان باقی‌ماندهٔ Pro"))found=v.text.toString()
            if(v is android.view.ViewGroup)for(i in 0 until v.childCount)visit(v.getChildAt(i))
        }
        onMain{visit(it.window.decorView)}
        return found
    }
    private fun screenshot(name:String){
        val ui=InstrumentationRegistry.getInstrumentation().uiAutomation
        for(cmd in listOf("mkdir -p /sdcard/Download/divan-ui-smoke","screencap -p /sdcard/Download/divan-ui-smoke/$name.png")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(cmd)).use{it.readBytes()}
        }
    }
    @Test(timeout=90000) fun verifiedRewardUnlocksMafiaAndExpiresWithCountdownAndSavedData(){
        var end=0L;var savedId=0L
        onMain{host->
            end=ProState.now()+3600
            savedId=host.repo.newSessionId()
            host.repo.save(GameSession(savedId,GameType.MENFI,listOf(Side("الف",1),Side("ب",2)),rules=GameRules(),label="reward_expiry_fixture"))
            ProState.applyAccount(response(end))
            assertTrue(ProState.isPro);assertTrue(ProState.mafiaAllowed);assertFalse(ProState.adsAllowed)
            host.resetTo(HomeScreen(host),SubscriptionScreen(host))
        }
        SystemClock.sleep(250)
        val first=timerText()
        screenshot("verified-pro-countdown")
        assertTrue("Actual native subscription UI shows the ticking verified entitlement: $first",first.isNotBlank())
        val tickDeadline=SystemClock.uptimeMillis()+4000
        var next=first
        while(next==first&&SystemClock.uptimeMillis()<tickDeadline){SystemClock.sleep(100);next=timerText()}
        assertTrue("The timer remains present",next.isNotBlank())
        assertNotEquals("The displayed seconds actually decrease",first,next)
        onMain{
            assertTrue(ProState.isPro);assertTrue(ProState.license.remainingSeconds(ProState.now())!! in 1..3599)
            // Simulate one hour passing using the same monotonic anchor as a server sync; never sleep an hour.
            ProState.anchorClock(end+1)
        }
        assertTrue("Respectful expiry returns to Home automatically",awaitExpiry())
        screenshot("verified-pro-expired")
        onMain{host->
            assertFalse(ProState.isPro);assertFalse(ProState.mafiaAllowed)
            assertTrue(host.current is HomeScreen)
            assertEquals("reward_expiry_fixture",host.repo.session(savedId)?.label)
            assertFalse("Only one expiry notice for this grant",ProState.consumeExpiredReward())
            host.repo.delete(savedId)
        }
    }
    private fun awaitExpiry():Boolean {
        val until=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<until){
            var expired=false
            onMain{expired=!ProState.isPro&&it.current is HomeScreen}
            if(expired)return true
            SystemClock.sleep(100)
        }
        return false
    }
    @Test(timeout=90000) fun aPaidSubscriptionSurvivesRewardExpiryWithoutAStaleNotice(){
        onMain{
            val now=ProState.now();val giftEnd=now-1
            ProState.putL("reward_notified_987654",0)
            ProState.applyAccount(response(now+30*86400,plan="m1",rewardUntil=giftEnd))
            assertTrue(ProState.isPro)
            assertFalse("Paid access continues after its overlapping gift ends",ProState.consumeExpiredReward())
            assertEquals(giftEnd,ProState.getL("reward_notified_987654"))
            ProState.applyAccount(response(now-1,pro=false,plan="m1",rewardUntil=0))
            assertFalse(ProState.isPro)
            assertFalse("A later paid expiry cannot be mistaken for the old gift",ProState.consumeExpiredReward())
        }
    }
    @Test(timeout=90000) fun claimNeverReportsSuccessForAnUnverifiedCertificate(){
        val done=CountDownLatch(1);val accepted=AtomicBoolean(true)
        onMain{host->
            val data=response(ProState.now()+3600,forged=true)
            Api.transport={_,_,_,_->JSONObject().put("ok",true).put("data",data).put("server_time",ProState.now())}
            AdsManager.completeDirect(host,JSONObject().put("watch_token","invalid_fixture_"+System.nanoTime()).put("claim_after",0).put("expires_at",ProState.now()+3600)){ok,message->
                accepted.set(ok);assertTrue(message.contains("هنوز تأیید نشده"));done.countDown()
            }
        }
        assertTrue(done.await(5,TimeUnit.SECONDS));assertFalse(accepted.get())
        onMain{assertFalse(ProState.isPro)}
    }
    @Test(timeout=90000) fun verifiedClaimAcknowledgesActualOneHourAccess(){
        val done=CountDownLatch(1);val accepted=AtomicBoolean(false)
        onMain{host->
            val data=response(ProState.now()+3600)
            Api.transport={_,_,_,_->JSONObject().put("ok",true).put("data",data).put("server_time",ProState.now())}
            AdsManager.completeDirect(host,JSONObject().put("watch_token","valid_fixture_"+System.nanoTime()).put("claim_after",0).put("expires_at",ProState.now()+3600)){ok,message->
                accepted.set(ok);assertEquals("یک ساعت Pro فعال شد.",message);done.countDown()
            }
        }
        assertTrue(done.await(5,TimeUnit.SECONDS));assertTrue(accepted.get())
        onMain{assertTrue(ProState.isPro);assertTrue(ProState.mafiaAllowed);assertTrue(ProState.license.remainingSeconds(ProState.now())!! in 3595..3600)}
    }
}
