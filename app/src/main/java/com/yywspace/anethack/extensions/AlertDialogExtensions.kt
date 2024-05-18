package com.yywspace.anethack.extensions

import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun AlertDialog.showImmersive() {
    this.window?.setFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    )
    show()
    this.window?.apply {
        val windowInsetsController =
            WindowCompat.getInsetsController(this, decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }
}

fun AlertDialog.Builder.showImmersive():AlertDialog{
    val dialog = create()
    dialog.showImmersive()
    return dialog
}

