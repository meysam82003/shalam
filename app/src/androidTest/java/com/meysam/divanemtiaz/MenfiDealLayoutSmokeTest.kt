package com.meysam.divanemtiaz

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenfiDealLayoutSmokeTest {
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    @Test fun rotationUsesTheWholeRowAndLongDealerNamesStayInsideIt() {
        val sides = listOf(Side("ما", 0, listOf("علی", "سید محمدحسین حسینی")), Side("اونا", 1, listOf("امید", "مجتبی")))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var dialog: Dialog
            scenario.onActivity { host -> dialog = host.current!!.chooseMenfiDeal(sides, null) {} }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                val views = descendants(dialog.window!!.decorView).toList()
                val rotate = views.single { it.contentDescription == "چرخش جای تیم‌ها" }
                val body = rotate.parent as ViewGroup
                assertTrue(rotate.width > 0)
                assertEquals(body.width - body.paddingLeft - body.paddingRight, rotate.width)
                val rows = views.filter { it.contentDescription?.startsWith("نوبت ") == true }
                assertEquals(4, rows.size)
                for (row in rows) {
                    val group = row as ViewGroup
                    for (i in 0 until group.childCount) {
                        val child = group.getChildAt(i)
                        assertTrue(child.left >= 0 && child.right <= group.width)
                    }
                }
                rotate.performClick()
                dialog.dismiss()
            }
        }
    }
}
