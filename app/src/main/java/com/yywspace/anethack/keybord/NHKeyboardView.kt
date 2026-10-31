package com.yywspace.anethack.keybord

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import com.yywspace.anethack.AssetsUtils
import com.yywspace.anethack.R


class NHKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {
    private var keyboardView:MutableList<MutableList<View>> = mutableListOf()
    private var placeholderView:MutableList<MutableList<View>> = mutableListOf()

    private var keyboardType:KeyboardLayout.Type = KeyboardLayout.Type.NONE
    private lateinit var keyboard:KeyboardLayout
    private lateinit var keyboardLetter:KeyboardLayout
    private lateinit var keyboardSymbol:KeyboardLayout
    private lateinit var keyboardCtrl:KeyboardLayout
    private lateinit var keyboardMeta:KeyboardLayout
    private lateinit var keyboardCustom:KeyboardLayout
    private lateinit var keyboardUpperLetter:KeyboardLayout

    private var keyHeight = 0 // 按键高度（px），init 里从 dimens / 自定义属性读
    private var keyGap = 0 // 按键四周边距，init 里从 dimens 读
    private var isUpper = false
    private var isNumberPanelShow = false
    var onKeyPress:((key:KeyboardLayout.Key)->Unit)? = null
    var onSpecialKeyLongPress:((key:KeyboardLayout.Key)->Unit)? = null


    init {
        // 按键高度可在布局里用 app:key_height 配置
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.NHKeyboardView)
        try {
            keyHeight = typedArray.getDimensionPixelSize(
                R.styleable.NHKeyboardView_key_height,
                resources.getDimensionPixelSize(R.dimen.command_item_height)
            )
            keyGap = resources.getDimensionPixelSize(R.dimen.command_item_gap)
        } finally {
            typedArray.recycle()
        }
        columnCount = 20
        rowCount = 5
        KeyboardVibrate.bind(context)
        initKeyboardView()
        initNumPanel()
        initKeyboardData()
        refreshKeyboardLayout()
    }

    fun refreshKeyboardLayout() {
        switchKeyboardLayout(keyboardType)
    }

    /**
     * 明暗主题切换：面板背景和按键颜色都要按新主题重新解析。
     * 背景是已有的 drawable，只能重新设置；按键需要重建视图（延后一点，避免和页面重建抢主线程）。
     */
    fun applyTheme() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        postDelayed({
            // 按键背景是 drawable，重新解析需要重建按键视图
            removeAllViews()
            keyboardView.clear()
            placeholderView.clear()
            initKeyboardView()
            initNumPanel()
            refreshKeyboardLayout()
        }, 100)
    }

    private fun initKeyboardData() {
        val keyboardLetterJson = AssetsUtils.readText(context, "keyboard/keyboard_letter.json").orEmpty()
        keyboardLetter = KeyboardLayout.fromJson(keyboardLetterJson)
        val keyboardLetterUpperJson = AssetsUtils.readText(context, "keyboard/keyboard_upper_letter.json").orEmpty()
        keyboardUpperLetter = KeyboardLayout.fromJson(keyboardLetterUpperJson)
        val keyboardSymbolJson = AssetsUtils.readText(context, "keyboard/keyboard_symbol.json").orEmpty()
        keyboardSymbol = KeyboardLayout.fromJson(keyboardSymbolJson)
        val keyboardCtrlJson = AssetsUtils.readText(context, "keyboard/keyboard_ctrl.json").orEmpty()
        keyboardCtrl = KeyboardLayout.fromJson(keyboardCtrlJson)
        val keyboardMetaJson = AssetsUtils.readText(context, "keyboard/keyboard_meta.json").orEmpty()
        keyboardMeta = KeyboardLayout.fromJson(keyboardMetaJson)
        val keyboardCustomJson = AssetsUtils.readText(context, "keyboard/keyboard_custom.json").orEmpty()
        keyboardCustom = KeyboardLayout.fromJson(keyboardCustomJson)
    }

    private fun initNumPanel() {
        keyboardView[0].forEach {
            it.visibility = if (!isNumberPanelShow) GONE else VISIBLE
        }
        placeholderView[0].forEach {
            it.visibility = if (!isNumberPanelShow) GONE else VISIBLE
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun initKeyView(x:Int, y:Int):View{
        val view = inflate(context, R.layout.keyboard_key_view, null).apply {
            setBackgroundResource(R.drawable.bg_key_selector)
            val statusImage = findViewById<ImageView>(R.id.key_status)
            setOnClickListener {
                KeyboardVibrate.vibrate()
                val k = this@NHKeyboardView.keyboard.rows[x].keys[y]
                when(k.value) {
                    "Letter" -> {
                        if(isUpper)
                            switchKeyboardLayout(KeyboardLayout.Type.UPPER_LETTER)
                        else
                            switchKeyboardLayout(KeyboardLayout.Type.LETTER)
                    }
                    "Shift" -> {
                        if(isUpper) {
                            statusImage.setImageResource(R.drawable.dot_unselected)
                            switchKeyboardLayout(KeyboardLayout.Type.LETTER)
                        } else {
                            statusImage.setImageResource(R.drawable.dot_selected)
                            switchKeyboardLayout(KeyboardLayout.Type.UPPER_LETTER)
                        }
                        isUpper = !isUpper
                    }
                    "Ctrl" ->
                        switchKeyboardLayout(KeyboardLayout.Type.CTRL)
                    "Meta" ->
                        switchKeyboardLayout(KeyboardLayout.Type.META)
                    "Symbol" ->
                        switchKeyboardLayout(KeyboardLayout.Type.SYMBOL)
                    "Num" -> {
                        keyboardView[0].forEach {
                            it.visibility = if (isNumberPanelShow) GONE else VISIBLE
                        }
                        placeholderView[0].forEach {
                            it.visibility = if (isNumberPanelShow) GONE else VISIBLE
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
                val k = this@NHKeyboardView.keyboard.rows[x].keys[y]
                if (k.label in listOf( "Letter", "Shift", "Ctrl", "Meta", "Symbol", "Num", "Space", "ESC", "DEL")) {
                    onSpecialKeyLongPress?.invoke(k)
                    return@setOnLongClickListener true
                }
                when(keyboardType) {
                    KeyboardLayout.Type.UPPER_LETTER, KeyboardLayout.Type.LETTER -> {
                        onKeyPress?.invoke(keyboardSymbol.rows[x].keys[y])
                    }
                    KeyboardLayout.Type.SYMBOL -> {
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
        val keyboardTemplateJson = AssetsUtils.readText(context, "keyboard/keyboard_view_template.json").orEmpty()
        val keyboardTemplate = KeyboardLayout.fromJson(keyboardTemplateJson)
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
                    height = keyHeight
                }
                addView(view, params)
                rowView.add(view)
            }
            keyboardView.add(rowView)
        }
    }


    private fun switchKeyboardLayout(keyboardType:KeyboardLayout.Type) {
        when(keyboardType) {
            KeyboardLayout.Type.LETTER ->
                setKeyboardLayout(keyboardLetter, KeyboardLayout.Type.LETTER)
            KeyboardLayout.Type.UPPER_LETTER ->
                setKeyboardLayout(keyboardUpperLetter, KeyboardLayout.Type.UPPER_LETTER)
            KeyboardLayout.Type.SYMBOL ->
                setKeyboardLayout(keyboardSymbol, KeyboardLayout.Type.SYMBOL)
            KeyboardLayout.Type.META ->
                setKeyboardLayout(keyboardMeta, KeyboardLayout.Type.META)
            KeyboardLayout.Type.CTRL ->
                setKeyboardLayout(keyboardCtrl, KeyboardLayout.Type.CTRL)
            KeyboardLayout.Type.CUSTOM, KeyboardLayout.Type.NONE ->
                setKeyboardLayout(keyboardLetter, KeyboardLayout.Type.LETTER)
        }
    }
    private fun setKeyboardLayout(keyboard:KeyboardLayout, keyboardType:KeyboardLayout.Type) {
        this.keyboard = keyboard
        this.keyboardType = keyboardType
        keyboard.rows.forEachIndexed {  i, row ->
            row.keys.forEachIndexed { j, key ->
                keyboardView[i][j].apply {
                    findViewById<ImageView>(R.id.key_status).apply {
                        visibility = if(key.label in listOf("Num", "Shift"))
                                VISIBLE
                            else
                                INVISIBLE
                    }
                    findViewById<TextView>(R.id.key_main).apply {
                        tag = key.value
                        text = key.label
                    }
                    findViewById<TextView>(R.id.key_sub).apply {
                        when(key.label) {
                            "Letter", "Shift", "Ctrl", "Meta", "Symbol", "Num", "Space", "ESC", "DEL"->
                                visibility = GONE
                            else -> {
                                visibility = VISIBLE
                                when(keyboardType) {
                                    KeyboardLayout.Type.UPPER_LETTER,  KeyboardLayout.Type.LETTER -> {
                                        keyboardSymbol.rows[i].keys[j].apply {
                                            tag = value
                                            text = label
                                        }
                                    }
                                    KeyboardLayout.Type.SYMBOL  -> {
                                        keyboardLetter.rows[i].keys[j].apply {
                                            tag = value
                                            text = label
                                        }
                                    }
                                    else -> {
                                        visibility = GONE
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