package com.meysam.divanadmin

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Civil values stay ISO Gregorian on the wire. The user chooses a Solar Hijri date and Tehran time. */
internal class AdminCalendar(private val activity: Activity) {
    private data class Day(val year: Int, val month: Int, val day: Int)

    private val months = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    private val dates = JSONObject(activity.assets.open("iran-years.json").bufferedReader().use { it.readText() })
    private val first = dates.getInt("first")
    private val starts = dates.getJSONArray("starts").let { json -> LongArray(json.length()) { json.getLong(it) } }
    private val last = first + starts.size - 2
    private val dayMs = 86_400_000L
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    private val localClock = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
        // Iran has used fixed standard time since 2023. This is also correct on old Android time-zone data.
        timeZone = TimeZone.getTimeZone("GMT+03:30")
    }

    private fun fa(value: Any): String = value.toString().map { ch ->
        if (ch in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[ch - '0'] else ch
    }.joinToString("")

    private fun monthOffset(month: Int): Int = if (month <= 6) (month - 1) * 31 else 186 + (month - 7) * 30

    private fun monthDays(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month < 12 -> 30
        else -> (starts[year - first + 1] - starts[year - first]).toInt() - 336
    }

    private fun fromIso(value: String): Day? {
        if (value.length < 10) return null
        val date = runCatching { iso.parse(value.take(10)) }.getOrNull() ?: return null
        val day = date.time / dayMs
        val match = starts.binarySearch(day)
        val index = if (match >= 0) match else -match - 2
        if (index !in 0 until starts.lastIndex) return null
        val offset = (day - starts[index]).toInt()
        val month = if (offset < 186) offset / 31 + 1 else (offset - 186) / 30 + 7
        return Day(first + index, month, offset - monthOffset(month) + 1)
    }

    fun display(value: String, type: String): String {
        if (value.isBlank()) return when (type) {
            "date" -> "انتخاب تاریخ"
            "time" -> "انتخاب ساعت تهران"
            else -> "انتخاب تاریخ و ساعت تهران"
        }
        if (type == "time") return fa(value)
        val date = fromIso(value)?.let { "${fa(it.day)} ${months[it.month - 1]} ${fa(it.year)}" } ?: fa(value.take(10))
        return if (type == "date") date else "$date، ساعت ${fa(value.takeLast(5))}"
    }

    fun choose(type: String, current: String, required: Boolean, onDone: (String) -> Unit) {
        val now = localClock.format(Date())
        val day = fromIso(current) ?: fromIso(now) ?: Day(first, 1, 1)
        val time = current.takeLast(5).takeIf { it.matches(Regex("\\d{2}:\\d{2}")) } ?: now.takeLast(5)
        val hasDate = type != "time"
        val hasTime = type != "date"
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            setPadding(20, 8, 20, 16)
        }
        fun picker(min: Int, max: Int, selected: Int, digits: (Int) -> String = { fa(it) }): NumberPicker =
            NumberPicker(activity).apply {
                minValue = min
                maxValue = max
                value = selected.coerceIn(min, max)
                setFormatter(digits)
                wrapSelectorWheel = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        fun row(vararg controls: Pair<String, NumberPicker>) {
            form.addView(LinearLayout(activity).apply {
                for ((label, control) in controls) addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(activity).apply { text = label; gravity = Gravity.CENTER })
                    addView(control, LinearLayout.LayoutParams(-1, -2))
                }, LinearLayout.LayoutParams(0, -2, 1f))
            })
        }
        val year = picker(first, last, day.year)
        val month = picker(1, 12, day.month) { months[it - 1] }
        val date = picker(1, monthDays(day.year, day.month), day.day)
        val hour = picker(0, 23, time.take(2).toInt()) { fa(it.toString().padStart(2, '0')) }
        val minute = picker(0, 59, time.takeLast(2).toInt()) { fa(it.toString().padStart(2, '0')) }
        if (hasDate) {
            row("روز" to date, "ماه" to month, "سال" to year)
            val change = NumberPicker.OnValueChangeListener { _, _, _ -> date.maxValue = monthDays(year.value, month.value) }
            month.setOnValueChangedListener(change)
            year.setOnValueChangedListener(change)
        }
        if (hasTime) row("ساعت تهران" to hour, "دقیقه" to minute)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (hasDate) "تقویم شمسی" else "ساعت تهران")
            .setView(form)
            .setPositiveButton("تأیید") { _, _ ->
                val epochDay = starts[year.value - first] + monthOffset(month.value) + date.value - 1
                val civil = iso.format(Date(epochDay * dayMs))
                val clock = String.format(Locale.US, "%02d:%02d", hour.value, minute.value)
                onDone(if (hasDate) civil + if (hasTime) "T$clock" else "" else clock)
            }
            .setNegativeButton("انصراف", null)
        if (!required) dialog.setNeutralButton("پاک کردن") { _, _ -> onDone("") }
        dialog.show()
    }
}
