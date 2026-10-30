package com.yywspace.anethack

import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.yywspace.anethack.entity.NHColor
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.Charset
import androidx.core.content.edit


object Utils {
    /**
     * 按设置里的 themeMode 应用明暗主题（light / dark）。
     * @param mode 显式传入的模式；切换偏好的回调里偏好尚未落盘，先把新值写进内存再应用，
     *             这样随后重建的 Activity 读到的就是新值
     */
    fun applyThemeMode(context: Context, mode: String? = null) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (mode != null)
            prefs.edit { putString("themeMode", mode) }
        val value = mode ?: prefs.getString("themeMode", "system")
        // 跟随系统时，深色与否由当前系统配置决定
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

    fun readAssetsFile(context: Context, filename: String): String {
        val stream = context.assets.open(filename)
        val fileLength = stream.available()
        val buffer = ByteArray(fileLength)
        stream.read(buffer)
        stream.close()
        return String(buffer, Charset.defaultCharset())
    }

    fun isAssetsFileExists(context: Context, filename: String): Boolean {
        try {
            context.assets.open(filename).use {
                return true
            }
        } catch(e: FileNotFoundException) {
            return false
        }
    }


    // 根据手机的分辨率从 dp 的单位 转成为 px(像素)
    fun dip2px(context: Context, dpValue: Float): Int {
        // 获取当前手机的像素密度（1个dp对应几个px）
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt() // 四舍五入取整
    }

    // 根据手机的分辨率从 px(像素) 的单位 转成为 dp
    fun px2dip(context: Context, pxValue: Float): Int {
        // 获取当前手机的像素密度（1个dp对应几个px）
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt() // 四舍五入取整
    }


    fun removeDialogFocus(dialog:AlertDialog){
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setGravity(Gravity.CENTER)
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
        }
    }
}