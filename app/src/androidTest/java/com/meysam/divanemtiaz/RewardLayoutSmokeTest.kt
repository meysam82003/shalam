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
    @After fun close(){activity.close();Api.transport=null}
    private fun screenshot(name:String){
        val ctx=instrumentation.targetContext
        val dir=File(ctx.getExternalFilesDir(null),"ui-smoke").apply{mkdirs()}
        instrumentation.uiAutomation.takeScreenshot()?.let{image->File(dir,"$name.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
    }
    private fun find(text:String):AccessibilityNodeInfo?=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    private fun showVideo(callback:(Boolean)->Unit){
        val bytes=instrumentation.context.assets.open("reward-fixture.mp4").use{it.readBytes()}
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        activity.onActivity{host->
            File(File(host.cacheDir,"ads").apply{mkdirs()},sha).writeBytes(bytes)
            val ad=DirectAd.from(JSONObject().put("id",999999).put("format","rewarded").put("title","نمونهٔ محلی بررسی چیدمان").put("body","توضیح بلند برای بررسی پیمایش صفحه و دسترسی همیشگی به دکمهٔ پایین. ".repeat(12)).put("cta","باز کردن سایت").put("target_url","https://example.test/").put("media",JSONObject().put("url","https://example.test/fixture.mp4").put("kind","video").put("sha256",sha)))
            val method=AdEngine.javaClass.getDeclaredMethod("fullScreen",Screen::class.java,String::class.java,DirectAd::class.java,Boolean::class.javaPrimitiveType,kotlin.jvm.functions.Function1::class.java).apply{isAccessible=true}
            method.invoke(AdEngine,host.current!!,"rewarded_offer",ad,true,callback)
        }
        instrumentation.waitForIdleSync()
    }
    @Test fun portraitVideoKeepsCtaInsideWindowAndReportsActualCompletion(){
        val done=CountDownLatch(1);val completed=AtomicBoolean(false)
        showVideo{completed.set(it);done.countDown()}
        SystemClock.sleep(500)
        val button=find("باز کردن سایت");assertNotNull("CTA remains accessible beside long video content",button)
        val bounds=Rect();button!!.getBoundsInScreen(bounds)
        val window=Rect();instrumentation.uiAutomation.rootInActiveWindow.getBoundsInScreen(window)
        assertTrue("CTA is entirely inside the dialog viewport",window.contains(bounds))
        assertTrue("CTA keeps a usable touch height",bounds.height()>=40*instrumentation.targetContext.resources.displayMetrics.density)
        screenshot("reward-video-cta")
        assertTrue("the video ends without user intervention",done.await(20,TimeUnit.SECONDS))
        assertTrue("natural video completion is retained when dialog is dismissed",completed.get())
    }
    @Test fun exitingBeforeEndCannotCompleteReward(){
        val done=CountDownLatch(1);val completed=AtomicBoolean(true)
        showVideo{completed.set(it);done.countDown()}
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
}
