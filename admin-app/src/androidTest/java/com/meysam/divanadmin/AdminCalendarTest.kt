package com.meysam.divanadmin

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminCalendarTest {
    @Test fun tehranCivilDatesDisplayInSolarHijri() {
        ActivityScenario.launch(AdminActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val calendar = AdminCalendar(activity)
                assertEquals("۳۰ اسفند ۱۴۰۳، ساعت ۲۳:۵۵", calendar.display("2025-03-20T23:55", "datetime-local"))
                assertEquals("۱ فروردین ۱۴۰۴", calendar.display("2025-03-21", "date"))
                assertEquals("۱ آذر ۱۴۰۵، ساعت ۰۰:۱۵", calendar.display("2026-11-22T00:15", "datetime-local"))
                assertEquals("۱۷:۲۰", calendar.display("17:20", "time"))
                assertEquals("انتخاب تاریخ", calendar.display("", "date"))
            }
        }
    }

    @Test fun rememberedSessionSurvivesFreshVaultReadAndExplicitLogout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = AdminSessionVault.read(context)
        try {
            val fakeSession = "test-session".repeat(4)
            AdminSessionVault.save(context, fakeSession)
            assertEquals(fakeSession, AdminSessionVault.read(context))
            val raw = context.getSharedPreferences("admin", Activity.MODE_PRIVATE).getString("token", null)
            org.junit.Assert.assertNotEquals(fakeSession, raw)
            AdminSessionVault.save(context, null)
            assertNull(AdminSessionVault.read(context))
        } finally {
            AdminSessionVault.save(context, previous)
        }
    }
}
