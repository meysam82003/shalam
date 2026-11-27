package com.meysam.divanemtiaz

import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A local 8-second media fixture, never a production advert or a fabricated revenue record. */
@RunWith(AndroidJUnit4::class)
class RewardLayoutSmokeTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:ActivityScenario<MainActivity>
    @Before fun launch(){
        Api.transport={_,_,_,_->JSONObject().put("ok",true).put("data",JSONObject()).put("server_time",System.currentTimeMillis()/1000)}
        activity=ActivityScenario.launch(MainActivity::class.java)
        activity.onActivity{host->
            Notices.markAsked(host)
            host.updateSettings{it.copy(general=it.general.copy(animations=false))}
            host.replace(HomeScreen(host))
        }
        instrumentation.waitForIdleSync()
    }
    @After fun close(){activity.onActivity{AdsManager.javaClass.getDeclaredField("cached").apply{isAccessible=true}.set(AdsManager,null)};activity.close();Api.transport=null}
    private fun screenshot(name:String){
        for(command in listOf("mkdir -p /sdcard/Download/divan-ui-smoke", "screencap -p /sdcard/Download/divan-ui-smoke/$name.png")) {
            val output=instrumentation.uiAutomation.executeShellCommand(command)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(output).use{it.readBytes()}
        }
    }
    private fun find(text:String):AccessibilityNodeInfo?=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    private fun videoCta(): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo>? {
        val until = SystemClock.uptimeMillis() + 4000
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val button = root?.findAccessibilityNodeInfosByText("باز کردن سایت")?.firstOrNull()
            if (root != null && button != null) return root to button
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < until)
        return null
    }
    private fun showVideo(callback:(Boolean)->Unit){
        val bytes=instrumentation.context.assets.open("reward-fixture.mp4").use{it.readBytes()}
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        activity.onActivity{host->
            File(File(host.cacheDir,"ads").apply{mkdirs()},sha).writeBytes(bytes)
            val ad=DirectAd.from(JSONObject().put("id",999999).put("format","rewarded").put("title","نمونهٔ محلی بررسی چیدمان").put("body","توضیح بلند برای بررسی پیمایش صفحه و دسترسی همیشگی به دکمهٔ پایین. ".repeat(12)).put("cta","باز کردن سایت").put("target_url","https://example.test/").put("media",JSONObject().put("url","https://example.test/fixture.mp4").put("kind","video").put("sha256",sha)))
            val method=AdEngine.javaClass.getDeclaredMethod("fullScreen",Screen::class.java,String::class.java,DirectAd::class.java,Boolean::class.javaPrimitiveType,kotlin.jvm.functions.Function1::class.java).apply{isAccessible=true}
            method.invoke(AdEngine,host.current!!,"rewarded_offer",ad,true,callback)
        }
    }
    @Test fun portraitVideoKeepsCtaInsideWindowAndReportsActualCompletion(){
        val done=CountDownLatch(1);val completed=AtomicBoolean(false)
        showVideo{completed.set(it);done.countDown()}
        // Measure the live window before slow emulator screenshot capture or video completion.
        val cta = videoCta()
        fun tree(node:AccessibilityNodeInfo?,depth:Int=0):String {
            if(node==null||depth>12)return ""
            val rect=Rect();node.getBoundsInScreen(rect)
            return "${node.className} text=${node.text} desc=${node.contentDescription} bounds=$rect\n"+(0 until node.childCount).joinToString(""){tree(node.getChild(it),depth+1)}
        }
        if (cta == null) {
            fail("CTA remains accessible; callback=${done.count} completed=${completed.get()} tree="+tree(instrumentation.uiAutomation.rootInActiveWindow))
            return
        }
        val (windowRoot, label) = cta
        var button = label
        while (!button.isClickable) button = button.parent ?: break
        val bounds=Rect();button.getBoundsInScreen(bounds)
        val window=Rect();windowRoot.getBoundsInScreen(window)
        assertTrue("CTA is entirely inside the dialog viewport",window.contains(bounds))
        assertTrue("CTA keeps a usable touch height",bounds.height()>=40*instrumentation.targetContext.resources.displayMetrics.density)
        screenshot("reward-video-cta")
        assertTrue("the video ends without user intervention",done.await(20,TimeUnit.SECONDS))
        assertTrue("natural video completion is retained when dialog is dismissed",completed.get())
    }
    @Test fun exitingBeforeEndCannotCompleteReward(){
        val done=CountDownLatch(1);val completed=AtomicBoolean(true)
        showVideo{completed.set(it);done.countDown()}
        assertNotNull("Reward dialog opened before pressing Back", videoCta())
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertTrue(done.await(5,TimeUnit.SECONDS));assertFalse(completed.get())
    }
    @Test fun fourPresetsAndFactoryChoiceOpenInNativeSettings(){
        activity.onActivity{it.push(SettingsScreen(it,SettingsScreen.TAB_MENFI))}
        instrumentation.waitForIdleSync()
        val button=find("تنظیمات ذخیره‌شده");assertNotNull(button)
        var node=button;var clicked=false
        while(node!=null&&!clicked){clicked=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);node=node.parent}
        assertTrue(clicked);instrumentation.waitForIdleSync()
        assertNotNull(find("پیش‌فرض برنامه"));assertNotNull(find("جایگاه خالی"))
        screenshot("menfi-presets")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
    @Test fun failedPrimaryFallsBackAndLoadedBannerBecomesVisibleBeforeImpression(){
        class FixtureAdapter(private val fail:Boolean):IranianAdAdapter {
            var requests=0;var destroyed=0
            override fun configure(activity:android.app.Activity,appId:String,test:Boolean)=true
            override fun ready(format:String,zone:String)=false
            override fun load(activity:android.app.Activity,format:String,zone:String,done:(Boolean)->Unit){done(false)}
            override fun show(activity:android.app.Activity,format:String,zone:String,event:(String)->Unit){event("error")}
            override fun banner(activity:android.app.Activity,zone:String,box:android.widget.FrameLayout,event:(String)->Unit){requests++;if(fail)throw IllegalStateException("local test: primary unavailable");box.addView(android.view.View(activity));event("loaded")}
            override fun destroyBanner(box:android.widget.FrameLayout){destroyed++;box.removeAllViews()}
            override fun release(){}
        }
        activity.onActivity { host ->
            AdsManager.release()
            val cfg=JSONObject().put("enabled",true).put("primary","tapsell").put("fallback","adivery")
                .put("banner",JSONObject().put("enabled",true).put("pages",org.json.JSONArray().put("home")).put("placements",JSONObject().put("sticky_bottom",true)))
                .put("networks",JSONObject().apply{for(n in listOf("tapsell","adivery"))put(n,JSONObject().put("enabled",true).put("test",true).put("app_id","local_fixture").put("zones",JSONObject().put("banner","fixture_banner")))})
            AdsManager.javaClass.getDeclaredField("cached").apply{isAccessible=true}.set(AdsManager,cfg)
            @Suppress("UNCHECKED_CAST")
            val adapters=AdsManager.javaClass.getDeclaredField("adapters").apply{isAccessible=true}.get(AdsManager) as MutableMap<String,IranianAdAdapter>
            val primary=FixtureAdapter(true);val fallback=FixtureAdapter(false);adapters["tapsell"]=primary;adapters["adivery"]=fallback
            val banner=AdsManager.banner(host.current!!,"sticky_bottom")
            assertNotNull(banner);assertEquals(android.view.View.VISIBLE,banner!!.visibility)
            assertEquals(1,primary.requests);assertEquals(1,fallback.requests)
            AdsManager.releaseBanners()
            assertEquals("failed primary must be detached, not retain the fallback view",1,primary.destroyed)
            assertEquals(1,fallback.destroyed)
        }
    }
}
