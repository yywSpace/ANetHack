package com.yywspace.anethack.extensions

import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager.LayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.PopupWindow

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
/**
 * 显示 PopupWindow，并按 [immersive] 重新应用系统栏状态。
 *
 * PopupWindow 一旦 isFocusable=true（例如内部有 EditText），就会成为焦点
 * 窗口，系统栏可能重新出现，所以需要在它自己的窗口上再隐藏一次。
 */
fun PopupWindow.showAtLocation(
    anchor: View,
    gravity: Int,
    x: Int,
    y: Int,
    immersive: Boolean
) {
    showAtLocation(anchor, gravity, x, y)
    if (!immersive)
        return

    val content = contentView ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        content.windowInsetsController?.apply {
            systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    } else {
        @Suppress("DEPRECATION")
        content.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN
    }
}
