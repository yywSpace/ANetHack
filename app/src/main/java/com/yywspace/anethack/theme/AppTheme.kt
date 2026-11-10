package com.yywspace.anethack.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.yywspace.anethack.R
import com.yywspace.anethack.entity.NHColor

/**
 * 主题与系统栏的统一入口。
 *
 * 新加 Activity 时只需要：
 * - onCreate 里先调 [applyTheme]；
 * - 普通页面（设置、编辑页）调 [applyNormalSystemBars]；
 * - 游戏页调 [applyGameBars]，按沉浸式变量切换状态栏/导航栏。
 */
object AppTheme {

    private const val KEY_THEME_MODE = "themeMode"

    /**
     * 应用主题模式（light / dark / system）。
     *
     * @param mode 显式传入的模式；切换偏好的回调里偏好尚未落盘，先把新值写进内存再应用，
     *             这样随后重建的 Activity 读到的就是新值
     */
    fun applyTheme(context: Context, mode: String? = null) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (mode != null)
            prefs.edit { putString(KEY_THEME_MODE, mode) }
        val value = mode ?: prefs.getString(KEY_THEME_MODE, "system")
        // 游戏调色板（NHColor）在深色模式下用柔和的一套
        NHColor.applyDarkPalette(isDark(context, value))
        AppCompatDelegate.setDefaultNightMode(
            when (value) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** 当前是否为深色：按偏好判断，避免 Activity 刚创建时 resources 还没更新 */
    fun isDark(context: Context): Boolean = isDark(context, null)

    private fun isDark(context: Context, mode: String?): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = mode ?: prefs.getString(KEY_THEME_MODE, "system")
        return when (value) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
    }

    /** 普通页面（设置、编辑页）：状态栏透明，图标颜色跟随深浅色 */
    fun applyNormalSystemBars(activity: Activity) {
        activity.window.statusBarColor = Color.TRANSPARENT
        val controller =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !isDark(activity)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }


    fun applyGameBars(activity: Activity, immersive: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, !immersive)
        activity.window.statusBarColor = Color.BLACK
        activity.window.navigationBarColor = ContextCompat.getColor(activity, R.color.app_background)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            activity.window.isNavigationBarContrastEnforced = false
        val controller = controller(activity)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        if (!immersive) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            return
        }

        // 状态栏在第一次布局前隐藏
        controller.apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        // 导航栏等第一次布局完成后再隐藏
        activity.window.decorView.post {
            controller.apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    private fun controller(activity: Activity) =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)

}
