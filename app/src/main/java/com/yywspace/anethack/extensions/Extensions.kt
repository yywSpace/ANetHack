package com.yywspace.anethack.extensions

import android.view.WindowManager.LayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.PopupWindow
import android.widget.Spinner

fun AlertDialog.show(immersive:Boolean) {
    if (immersive) {
        this.window?.setFlags(
            LayoutParams.FLAG_NOT_FOCUSABLE,
            LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }
    show()
    if (immersive) {
        this.window?.apply {
            val windowInsetsController =
                WindowCompat.getInsetsController(this, decorView)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            clearFlags(LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }
    // 避免左右留白太多
    this.window?.apply {
        attributes = attributes.apply {
            width = LayoutParams.MATCH_PARENT
        }
    }
}

fun AlertDialog.Builder.show(immersive:Boolean):AlertDialog{
    val dialog = create()
    dialog.show(immersive)
    return dialog
}