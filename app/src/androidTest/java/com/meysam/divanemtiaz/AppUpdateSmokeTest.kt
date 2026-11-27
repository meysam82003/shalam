package com.meysam.divanemtiaz

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateSmokeTest {
    @Test fun requiredUpdateBlocksNavigationAfterAnOfflineRelaunchAndLiftsAfterPolicyChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("divan_app_updates", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val required = JSONObject().put("version_code", 9999).put("version_name", "99.0").put("required", true)
        val empty = JSONObject()
        Api.transport = { _, path, _, _ ->
            JSONObject().put("ok", true).put("data", if (path == "v1/app-update") required else empty)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                Notices.sync(context)
                waitFor(scenario) { it.current is RequiredUpdateScreen }
                scenario.onActivity { host ->
                    host.push(HomeScreen(host))
                    assertTrue(host.current is RequiredUpdateScreen)
                }
            }
            Api.transport = { _, _, _, _ -> throw IOException("offline") }
            Notices.sync(context)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(it.current is RequiredUpdateScreen) }
                assertEquals(9999, AppUpdates.available(context)?.versionCode)
                val withdrawn = JSONObject().put("version_code", 0).put("required", false)
                Api.transport = { _, path, _, _ ->
                    JSONObject().put("ok", true).put("data", if (path == "v1/app-update") withdrawn else empty)
                }
                Notices.sync(context)
                waitFor(scenario) { it.current !is RequiredUpdateScreen }
            }
        } finally {
            preferences.edit().clear().commit()
            Api.transport = null
        }
    }

    private fun waitFor(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 4000
        do {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        scenario.onActivity { assertTrue(condition(it)) }
    }
}
