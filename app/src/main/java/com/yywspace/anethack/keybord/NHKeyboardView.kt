package com.yywspace.anethack.keybord

import android.content.Context
import android.util.AttributeSet
import com.hijamoya.keyboardview.Keyboard
import com.hijamoya.keyboardview.KeyboardView
import com.yywspace.anethack.R


class NHKeyboardView : KeyboardView {

    val letterKeyboard = Keyboard(context, R.xml.keyboard_letter)
    val metaKeyboard = Keyboard(context, R.xml.keyboard_meta)
    val ctrlKeyboard = Keyboard(context, R.xml.keyboard_ctrl)
    val symbolsKeyboard = Keyboard(context, R.xml.keyboard_symbols)
    var onKeyPress:((keyCode:Int)->Unit)? = null
    constructor(context: Context?) : this(context, null, 0)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    init {
        keyboard = letterKeyboard
        onKeyboardActionListener =  object :OnKeyboardActionListener{
            override fun onPress(primaryCode: Int) {

            }

            override fun onRelease(primaryCode: Int) {
            }

            override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
                when (primaryCode) {
                    KEYCODE_META -> keyboard = metaKeyboard
                    KEYCODE_CTRL -> keyboard = ctrlKeyboard
                    KEYCODE_LETTER -> keyboard = letterKeyboard
                    KEYCODE_SYMBOLS ->  keyboard = symbolsKeyboard
                    Keyboard.KEYCODE_SHIFT -> {
                        keyboard = letterKeyboard
                        isShifted = !isShifted
                    }
                    else -> {
                        if(isShifted)
                            onKeyPress?.invoke(Character.toUpperCase(primaryCode))
                        else
                            onKeyPress?.invoke(primaryCode)
                    }
                }
            }

            override fun onText(text: CharSequence?) {
            }

            override fun swipeLeft() {
            }

            override fun swipeRight() {
            }

            override fun swipeDown() {
            }

            override fun swipeUp() {
            }

        }
    }

    companion object {
        private const val KEYCODE_CTRL = -7
        private const val KEYCODE_SYMBOLS = -8
        private const val KEYCODE_META = -9
        private const val KEYCODE_LETTER = -10
    }
}
