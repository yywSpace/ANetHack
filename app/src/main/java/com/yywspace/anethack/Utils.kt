package com.yywspace.anethack

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.yywspace.anethack.entity.NHColor

object Utils {
    /**
     * 把偏好的 themeMode 应用到整个主题（light / dark / system）。
     * @param mode 显式传入的模式；切换偏好的回调里偏好尚未落盘，先把新值写进内存再应用，
     *             这样随后重建的 Activity 读到的就是新值
     */
    fun applyThemeMode(context: Context, mode: String? = null) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (mode != null)
            prefs.edit { putString("themeMode", mode) }
        val value = mode ?: prefs.getString("themeMode", "system")
        // 跟随系统时，深浅色由当前系统配置决定
        val dark = when (value) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        // 游戏调色板（NHColor）在深色模式下用柔和的一套
        NHColor.darkTheme = dark
        AppCompatDelegate.setDefaultNightMode(
            when (value) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
