package com.yywspace.anethack.keybord

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.yywspace.anethack.R

/**
 * 标准布局（字母 / 大写 / 符号 / Ctrl / Meta）。
 *
 * 视图按 keyboard_view_template.json 建一次，切换布局只改文字。
 */
class NHStandardKeyboardView(context: Context) : KeyboardGridBase(context) {

    private var keyboardType: KeyboardLayout.Type = KeyboardLayout.Type.LETTER

    /** 布局切换类按键：这些键长按不发送字符，交给外层处理 */
    private val layoutKeyLabels = setOf(
        "Letter", "Shift", "Ctrl", "Meta", "Symbol", "Num", "Cust", "Enter", "ESC", "DEL"
    )
    private lateinit var keyboard: KeyboardLayout
    private lateinit var letterLayout: KeyboardLayout
    private lateinit var upperLetterLayout: KeyboardLayout
    private lateinit var symbolLayout: KeyboardLayout
    private lateinit var ctrlLayout: KeyboardLayout
    private lateinit var metaLayout: KeyboardLayout

    /** 每个格子的视图，和模板一一对应 */
    private val keyViews: MutableList<MutableList<View>> = mutableListOf()

    /** 大写状态 */
    var isUpper: Boolean = false
        private set

    /** 点 Cust 时通知容器切到自定义布局 */
    var onSwitchLayout: ((KeyboardLayout.Type) -> Unit)? = null

    init {
        loadLayouts()
        buildKeyViews()
        switchLayout(KeyboardLayout.Type.LETTER)
    }

    private fun loadLayouts() {
        letterLayout = KeyboardStore.readLetter(context)
        upperLetterLayout = KeyboardStore.readUpperLetter(context)
        symbolLayout = KeyboardStore.readSymbol(context)
        ctrlLayout = KeyboardStore.readCtrl(context)
        metaLayout = KeyboardStore.readMeta(context)
    }

    /** 按模板建一次视图 */
    private fun buildKeyViews() {
        keyViews.clear()
        val placeholderRows = resetGrid()
        val template = KeyboardStore.readTemplate(context)
        template.rows.forEachIndexed { i, row ->
            val rowView = mutableListOf<View>()
            row.keys.forEachIndexed { j, cell ->
                val view = createKeyView(
                    { keyboard.rows[i].keys[j] },
                    { k -> handleKeyLongClick(k, i, j) }
                )
                grid.addView(view, gridParams(cell, keyHeight))
                rowView.add(view)
            }
            keyViews.add(rowView)
        }
        numberViews.clear()
        numberViews.addAll(keyViews[0])
        numberViews.addAll(placeholderRows[0])
    }

    fun switchLayout(type: KeyboardLayout.Type) {
        val layout = when (type) {
            KeyboardLayout.Type.LETTER -> letterLayout
            KeyboardLayout.Type.UPPER_LETTER -> upperLetterLayout
            KeyboardLayout.Type.SYMBOL -> symbolLayout
            KeyboardLayout.Type.CTRL -> ctrlLayout
            KeyboardLayout.Type.META -> metaLayout
            else -> return
        }
        keyboardType = type
        keyboard = layout
        layout.rows.forEachIndexed { i, row ->
            row.keys.forEachIndexed { j, key ->
                val view = keyViews[i][j]
                bindKeyView(view, key.label, key.command, subLabel(key, i, j))
            }
        }
        setNumberPanelVisible(numberShown)
    }

    override fun applyTheme() {
        buildKeyViews()
        switchLayout(keyboardType)
    }

    override fun handleKeyClick(key: KeyboardLayout.Key, statusImage: ImageView?) {
        if (key.command.isEmpty()) return
        when (key.command) {
            "Letter" -> {
                if (isUpper) switchLayout(KeyboardLayout.Type.UPPER_LETTER)
                else switchLayout(KeyboardLayout.Type.LETTER)
            }
            "Shift" -> {
                if (isUpper) {
                    statusImage?.setImageResource(R.drawable.dot_unselected)
                    switchLayout(KeyboardLayout.Type.LETTER)
                } else {
                    statusImage?.setImageResource(R.drawable.dot_selected)
                    switchLayout(KeyboardLayout.Type.UPPER_LETTER)
                }
                isUpper = !isUpper
            }
            "Ctrl" -> switchLayout(KeyboardLayout.Type.CTRL)
            "Meta" -> switchLayout(KeyboardLayout.Type.META)
            "Symbol" -> switchLayout(KeyboardLayout.Type.SYMBOL)
            "Custom" -> onSwitchLayout?.invoke(KeyboardLayout.Type.CUSTOM)
            "Num" -> toggleNumberPanel(statusImage)
            else -> onKeyPress?.invoke(key)
        }
    }

    private fun handleKeyLongClick(key: KeyboardLayout.Key, x: Int, y: Int) {
        if (key.label == "Space" || key.label in layoutKeyLabels) {
            onSpecialKeyLongPress?.invoke(key)
            return
        }
        when (keyboardType) {
            KeyboardLayout.Type.UPPER_LETTER, KeyboardLayout.Type.LETTER ->
                onKeyPress?.invoke(symbolLayout.rows[x].keys[y])
            KeyboardLayout.Type.SYMBOL ->
                onKeyPress?.invoke(letterLayout.rows[x].keys[y])
            else ->
                onKeyPress?.invoke(key)
        }
    }

    /** 副标签：空格显示 /Esc，布局切换键没有副标签，其余按当前布局取备用字符 */
    private fun subLabel(key: KeyboardLayout.Key, i: Int, j: Int): String? {
        if (key.label == "Space") return "/Esc"
        if (key.label in layoutKeyLabels)
            return null
        return when (keyboardType) {
            KeyboardLayout.Type.UPPER_LETTER, KeyboardLayout.Type.LETTER ->
                symbolLayout.rows[i].keys[j].label
            KeyboardLayout.Type.SYMBOL ->
                letterLayout.rows[i].keys[j].label
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }

}
