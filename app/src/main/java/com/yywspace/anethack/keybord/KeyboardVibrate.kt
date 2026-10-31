package com.yywspace.anethack.keybord

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 点击震动：键盘和两条命令面板共用。
 *
 * 是否震动读设置里同一个开关（键盘震动），由 [bind] 时的 [enabled] 决定。
 */
object KeyboardVibrate {

    var enabled: Boolean = true

    private var vibrator: Vibrator? = null

    /** 绑定系统振动器；重复调用无副作用 */
    fun bind(context: Context) {
        if (vibrator == null) vibrator = createVibrator(context.applicationContext)
    }

    fun vibrate() {
        if (!enabled) return
        vibrator?.vibrate(VibrationEffect.createOneShot(100, 120))
    }

    /** Android 12 起用 VibratorManager 取默认振动器，之前用 VIBRATOR_SERVICE */
    @Suppress("DEPRECATION")
    private fun createVibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        else
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
}
