package com.meysam.divanemtiaz

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.widget.TextView

/** Applies the same dark-lapis/gold visual language used by the live Shalam UI. */
fun Activity.styleDivanDialog(dialog: AlertDialog) {
    dialog.window?.setBackgroundDrawable(
        divanShape(this, DivanTheme.surfaceHigh, DivanTheme.goldSoft, 20, 1)
    )

    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
        setTextColor(DivanTheme.turquoise)
        setBackgroundColor(Color.TRANSPARENT)
        useDivanTypography(true)
    }
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
        setTextColor(DivanTheme.gold)
        setBackgroundColor(Color.TRANSPARENT)
        useDivanTypography(true)
    }
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
        setTextColor(DivanTheme.ivory)
        setBackgroundColor(Color.TRANSPARENT)
        useDivanTypography(true)
    }

    val titleId = resources.getIdentifier("alertTitle", "id", "android")
    if (titleId != 0) dialog.findViewById<TextView>(titleId)?.apply {
        setTextColor(DivanTheme.ivory)
        useDivanTypography(true)
    }
    val messageId = resources.getIdentifier("message", "id", "android")
    if (messageId != 0) dialog.findViewById<TextView>(messageId)?.apply {
        setTextColor(DivanTheme.muted)
        useDivanTypography(false)
    }

    dialog.window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL
}
