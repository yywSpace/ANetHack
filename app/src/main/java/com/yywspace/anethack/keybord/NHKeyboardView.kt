package com.yywspace.anethack.keybord

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.yywspace.anethack.R

/**
 * 键盘容器：持有标准布局和自定义布局两个独立的网格视图，整块切换。
 *
 * 两个子视图各自管自己的模板、数据和数字行，这里只做三件事：
 * - 显示/隐藏对应的子视图；
 * - 转发按键回调；
 * - 处理两边互相切换布局的请求。
 */
class NHKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val standardView = NHStandardKeyboardView(context)
    private val customView = NHCustomKeyboardView(context)

    private var keyboardType: KeyboardLayout.Type = KeyboardLayout.Type.LETTER

    var onKeyPress: ((key: KeyboardLayout.Key) -> Unit)? = null
        set(value) {
            field = value
            standardView.onKeyPress = value
            customView.onKeyPress = value
        }

    var onSpecialKeyLongPress: ((key: KeyboardLayout.Key) -> Unit)? = null
        set(value) {
            field = value
            standardView.onSpecialKeyLongPress = value
            customView.onSpecialKeyLongPress = value
        }

    /** 弹窗是否需要按沉浸式显示（和命令面板一致） */
    var immersiveProvider: (() -> Boolean)? = null
        set(value) {
            field = value
            standardView.immersiveProvider = value
            customView.immersiveProvider = value
        }

    init {
        addView(standardView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(customView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        standardView.onSwitchLayout = { type -> switchKeyboardLayout(type) }
        customView.onSwitchLayout = { type -> switchKeyboardLayout(type) }

        showCustomKeyboard(false)
    }

    /** 切换布局：CUSTOM 显示自定义网格，其余显示标准网格 */
    fun switchKeyboardLayout(type: KeyboardLayout.Type) {
        if (type == KeyboardLayout.Type.CUSTOM) {
            keyboardType = type
            customView.loadLayout()
            showCustomKeyboard(true)
            return
        }
        // 从自定义布局点 Letter 回来时，保持之前的大写状态
        keyboardType = if (type == KeyboardLayout.Type.LETTER && standardView.isUpper)
            KeyboardLayout.Type.UPPER_LETTER else type
        standardView.switchLayout(keyboardType)
        showCustomKeyboard(false)
    }

    fun refreshKeyboardLayout() = switchKeyboardLayout(keyboardType)

    /** 重新读取自定义布局（在设置里改完后回到游戏时调用） */
    fun reloadCustomLayout() {
        if (keyboardType == KeyboardLayout.Type.CUSTOM)
            customView.loadLayout()
    }

    /** 主题切换：两个子视图各自重建按键 */
    fun applyTheme() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        standardView.applyTheme()
        customView.applyTheme()
        showCustomKeyboard(keyboardType == KeyboardLayout.Type.CUSTOM)
    }

    /** 整块切换：true 显示自定义键盘，false 显示标准键盘 */
    private fun showCustomKeyboard(custom: Boolean) {
        customView.visibility = if (custom) VISIBLE else GONE
        standardView.visibility = if (custom) GONE else VISIBLE
    }
}
