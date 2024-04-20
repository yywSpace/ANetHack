package com.yywspace.anethack.keybord

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.view.setMargins
import com.yywspace.anethack.R
import com.yywspace.anethack.Utils

class KeyboardGridLayout : GridLayout {
    private var keyboardType:NHKeyboard.Type = NHKeyboard.Type.NONE
    private lateinit var keyboard:NHKeyboard
    private lateinit var keyboardLetter:NHKeyboard
    private lateinit var keyboardSymbol:NHKeyboard
    private lateinit var keyboardCtrl:NHKeyboard
    private lateinit var keyboardMeta:NHKeyboard
    private lateinit var keyboardCustom:NHKeyboard
    private lateinit var keyboardUpperLetter:NHKeyboard
    var keyHeight = 100
    var isUpper = false
    var onKeyPress:((key:NHKeyboard.Key)->Unit)? = null

    constructor(context: Context?) : this(context, null, 0)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    init {
        columnCount = 20
        rowCount = 5

        val keyboardLetterJson = Utils.readAssetsFile(
            context, "keyboard/keyboard_letter.json")
        keyboardLetter = NHKeyboardUtils.readFromJson(keyboardLetterJson)
        val keyboardLetterUpperJson = Utils.readAssetsFile(
            context, "keyboard/keyboard_upper_letter.json")
        keyboardUpperLetter = NHKeyboardUtils.readFromJson(keyboardLetterUpperJson)
        val keyboardSymbolJson = Utils.readAssetsFile(
            context, "keyboard/keyboard_symbol.json")
        keyboardSymbol = NHKeyboardUtils.readFromJson(keyboardSymbolJson)
        val keyboardCtrlJson = Utils.readAssetsFile(
            context, "keyboard/keyboard_ctrl.json")
        keyboardCtrl = NHKeyboardUtils.readFromJson(keyboardCtrlJson)
        val keyboardMetaJson = Utils.readAssetsFile(
            context, "keyboard/keyboard_meta.json")
        keyboardMeta = NHKeyboardUtils.readFromJson(keyboardMetaJson)
        val keyboardCustomJson = Utils.readAssetsFile(
            context, "keyboard/keyboard_custom.json")
        keyboardCustom = NHKeyboardUtils.readFromJson(keyboardCustomJson)

        switchNHKeyboard(NHKeyboard.Type.LETTER)
    }
    fun refreshNHKeyboard() {
        switchNHKeyboard(keyboardType)
    }

    fun switchNHKeyboard(keyboardType:NHKeyboard.Type) {
        when(keyboardType) {
            NHKeyboard.Type.UPPER_LETTER ->
                setNHKeyboard(keyboardUpperLetter, NHKeyboard.Type.UPPER_LETTER)
            NHKeyboard.Type.LETTER ->
                setNHKeyboard(keyboardLetter, NHKeyboard.Type.LETTER)
            NHKeyboard.Type.SYMBOL ->
                setNHKeyboard(keyboardSymbol, NHKeyboard.Type.SYMBOL)
            NHKeyboard.Type.META ->
                setNHKeyboard(keyboardMeta, NHKeyboard.Type.META)
            NHKeyboard.Type.CTRL ->
                setNHKeyboard(keyboardCtrl, NHKeyboard.Type.CTRL)
            NHKeyboard.Type.CUSTOM, NHKeyboard.Type.NONE ->
                setNHKeyboard(keyboardLetter, NHKeyboard.Type.LETTER)
        }
    }
    private fun setNHKeyboard(keyboard:NHKeyboard, keyboardType:NHKeyboard.Type) {
        this.keyboard = keyboard
        this.keyboardType = keyboardType
        removeAllViews()
        // 占位，使后续排版能够完全按照网格进行
        for (i in 0 until rowCount) {
            for (j in 0 until columnCount) {
                if (i == 0 || j == 0) {
                    val textView = TextView(context)
                    val params = LayoutParams(spec(i, 1f), spec(j, 1f)).apply {
                        setMargins(5)
                    }
                    addView(textView, params)
                }
            }
        }
        keyboard.rows.forEach {  row ->
            row.keys.forEach { key ->
                val view = TextView(context).apply {
                    setBackgroundResource(R.drawable.btn_bg_selector)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    tag = key.value
                    text = key.label
                    setOnClickListener {
                        when(key.label) {
                            "Letter", "Shift" -> {
                                if(isUpper) {
                                    setNHKeyboard(keyboardLetter, NHKeyboard.Type.LETTER)
                                }
                                else {
                                    setNHKeyboard(keyboardUpperLetter, NHKeyboard.Type.UPPER_LETTER)
                                }
                                isUpper = !isUpper
                            }
                            "Ctrl" ->
                                setNHKeyboard(keyboardCtrl, NHKeyboard.Type.CTRL)
                            "Meta" ->
                                setNHKeyboard(keyboardMeta, NHKeyboard.Type.META)
                            "Symbol" ->
                                setNHKeyboard(keyboardSymbol, NHKeyboard.Type.SYMBOL)
                            else ->
                                onKeyPress?.invoke(key)
                        }
                    }
                }
                val params = LayoutParams(
                    spec(key.row, key.rowSpan,1f),
                    spec(key.column, key.columnSpan, key.columnWeight.toFloat())
                ).apply {
                    setMargins(5)
                    width = 0
                    height = keyHeight
                }
                addView(view, params)
            }
        }
    }
}