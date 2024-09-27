package com.yywspace.anethack.keybord

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.setMargins
import com.yywspace.anethack.R
import com.yywspace.anethack.Utils


class KeyboardView : GridLayout {
    private var keyboardView:MutableList<MutableList<View>> = mutableListOf()
    private var placeholderView:MutableList<MutableList<View>> = mutableListOf()

    private var keyboardType:NHKeyboard.Type = NHKeyboard.Type.NONE
    private lateinit var keyboard:NHKeyboard
    private lateinit var keyboardLetter:NHKeyboard
    private lateinit var keyboardSymbol:NHKeyboard
    private lateinit var keyboardCtrl:NHKeyboard
    private lateinit var keyboardMeta:NHKeyboard
    private lateinit var keyboardCustom:NHKeyboard
    private lateinit var keyboardUpperLetter:NHKeyboard

    private var keyHeight = 40F
    private var keyGap = 3
    private var isUpper = false
    private var isNumberPanelShow = false
    var onKeyPress:((key:NHKeyboard.Key)->Unit)? = null
    var onSpecialKeyLongPress:((key:NHKeyboard.Key)->Unit)? = null

    private var vibrator: Vibrator
    private var keyboardVibrate: Boolean = true

    constructor(context: Context?) : this(context, null, 0)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    init {
        columnCount = 20
        rowCount = 5
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        initKeyboardView()
        initNumPanel()
        initKeyboardData()
        refreshNHKeyboard()
    }
    fun refreshNHKeyboard() {
        switchNHKeyboard(keyboardType)
    }
    private fun initKeyboardData() {
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
    }

    private fun initNumPanel() {
        keyboardView[0].forEach {
            it.visibility = if (!isNumberPanelShow) View.GONE else View.VISIBLE
        }
        placeholderView[0].forEach {
            it.visibility = if (!isNumberPanelShow) View.GONE else View.VISIBLE
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun initKeyView(x:Int, y:Int):View{
        val view = inflate(context, R.layout.keyboard_key_view, null).apply {
            setBackgroundResource(R.drawable.btn_bg_selector)
            val statusImage = findViewById<ImageView>(R.id.key_status)
            setOnClickListener {
                if (keyboardVibrate)
                    vibrator.vibrate(VibrationEffect.createOneShot(100, 120))
                val k = this@KeyboardView.keyboard.rows[x].keys[y]
                when(k.value) {
                    "Letter" -> {
                        if(isUpper)
                            switchNHKeyboard(NHKeyboard.Type.UPPER_LETTER)
                        else
                            switchNHKeyboard(NHKeyboard.Type.LETTER)
                    }
                    "Shift" -> {
                        if(isUpper) {
                            statusImage.setImageResource(R.drawable.dot_unselected)
                            switchNHKeyboard(NHKeyboard.Type.LETTER)
                        } else {
                            statusImage.setImageResource(R.drawable.dot_selected)
                            switchNHKeyboard(NHKeyboard.Type.UPPER_LETTER)
                        }
                        isUpper = !isUpper
                    }
                    "Ctrl" ->
                        switchNHKeyboard(NHKeyboard.Type.CTRL)
                    "Meta" ->
                        switchNHKeyboard(NHKeyboard.Type.META)
                    "Symbol" ->
                        switchNHKeyboard(NHKeyboard.Type.SYMBOL)
                    "Num" -> {
                        keyboardView[0].forEach {
                            it.visibility = if (isNumberPanelShow) View.GONE else View.VISIBLE
                        }
                        placeholderView[0].forEach {
                            it.visibility = if (isNumberPanelShow) View.GONE else View.VISIBLE
                        }
                        if (isNumberPanelShow)
                            statusImage.setImageResource(R.drawable.dot_unselected)
                        else
                            statusImage.setImageResource(R.drawable.dot_selected)
                        isNumberPanelShow = !isNumberPanelShow
                    }
                    else ->
                        onKeyPress?.invoke(k)
                }
            }
            setOnLongClickListener {
                val k = this@KeyboardView.keyboard.rows[x].keys[y]
                if (k.label in listOf<String>( "Letter", "Shift", "Ctrl", "Meta", "Symbol", "Num", "ESC", "DEL", "Enter")) {
                    onSpecialKeyLongPress?.invoke(k)
                    return@setOnLongClickListener true
                }
                when(keyboardType) {
                    NHKeyboard.Type.UPPER_LETTER, NHKeyboard.Type.LETTER -> {
                        onKeyPress?.invoke(keyboardSymbol.rows[x].keys[y])
                    }
                    NHKeyboard.Type.SYMBOL -> {
                        onKeyPress?.invoke(keyboardLetter.rows[x].keys[y])
                    }
                    else -> {
                        onKeyPress?.invoke(k)
                    }
                }
                true
            }
        }
        return view
    }
    private fun initKeyboardView() {
        val keyboardTemplateJson = Utils.readAssetsFile(
            context, "keyboard/keyboard_view_template.json")
        val keyboardTemplate = NHKeyboardUtils.readFromJson(keyboardTemplateJson)
        // 占位，使后续排版能够完全按照网格进行
        for (i in 0 until rowCount) {
            val rowView = mutableListOf<View>()
            for (j in 0 until columnCount) {
                if (i == rowCount-1 || j == 0) {
                    val textView = TextView(context)
                    val params = LayoutParams(spec(i, 1f), spec(j, 1f)).apply {
                        setMargins(keyGap)
                    }
                    addView(textView, params)
                    rowView.add(textView)
                }
            }
            placeholderView.add(rowView)
        }
        keyboardTemplate.rows.forEachIndexed { i, row ->
            val rowView = mutableListOf<View>()
            row.keys.forEachIndexed { j, key ->
                val view = initKeyView(i, j)
                val params = LayoutParams(
                    spec(key.row, key.rowSpan,1f),
                    spec(key.column, key.columnSpan, key.columnWeight.toFloat())
                ).apply {
                    setMargins(keyGap)
                    width = 0
                    height = Utils.dip2px(context, keyHeight)
                }
                addView(view, params)
                rowView.add(view)
            }
            keyboardView.add(rowView)
        }
    }

    fun setKeyboardVibrate(vibrate:Boolean) {
        keyboardVibrate = vibrate
    }
    private fun switchNHKeyboard(keyboardType:NHKeyboard.Type) {
        when(keyboardType) {
            NHKeyboard.Type.LETTER ->
                setNHKeyboard(keyboardLetter, NHKeyboard.Type.LETTER)
            NHKeyboard.Type.UPPER_LETTER ->
                setNHKeyboard(keyboardUpperLetter, NHKeyboard.Type.UPPER_LETTER)
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
        keyboard.rows.forEachIndexed {  i, row ->
            row.keys.forEachIndexed { j, key ->
                keyboardView[i][j].apply {
                    findViewById<ImageView>(R.id.key_status).apply {
                        visibility = if(key.label in listOf("Num", "Shift"))
                                View.VISIBLE
                            else
                                View.INVISIBLE
                    }
                    findViewById<TextView>(R.id.key_main).apply {
                        tag = key.value
                        text = key.label
                    }
                    findViewById<TextView>(R.id.key_sub).apply {
                        when(key.label) {
                            "Letter", "Shift", "Ctrl", "Meta", "Symbol", "Num", "ESC", "DEL", "Enter"->
                                visibility = View.GONE
                            else -> {
                                visibility = View.VISIBLE
                                when(keyboardType) {
                                    NHKeyboard.Type.UPPER_LETTER,  NHKeyboard.Type.LETTER -> {
                                        keyboardSymbol.rows[i].keys[j].apply {
                                            tag = value
                                            text = label
                                        }
                                    }
                                    NHKeyboard.Type.SYMBOL  -> {
                                        keyboardLetter.rows[i].keys[j].apply {
                                            tag = value
                                            text = label
                                        }
                                    }
                                    else -> {
                                        visibility = View.GONE
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}