package com.yywspace.anethack.keybord

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.yywspace.anethack.R

class NHKeyboardView : ViewGroup{
    lateinit var keyboard:NHKeyboard
    var keyboardView:MutableList<MutableList<View>> = mutableListOf()
    var gap = 10
    private var firstMeasure = true
    var onKeyPress:((key:NHKeyboard.Key)->Unit)? = null

    constructor(context: Context?) : this(context, null, 0)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val rw = MeasureSpec.getSize(widthMeasureSpec)
        val rh = MeasureSpec.getSize(heightMeasureSpec)
        var childHeight = 0
        for (rowIdx in 0 until keyboardView.size) {
            var minHeight = Int.MAX_VALUE
            val rowViews = keyboardView[rowIdx]
            val row = keyboard.rows[rowIdx]
            for (keyIdx in 0 until rowViews.size) {
                val view = rowViews[keyIdx]
                val key = row.keys[keyIdx]
                measureChild(view, rw, rh)
                if(firstMeasure) {
                    if (row.type == NHKeyboard.Row.Type.SPAN) {
                        val keyBaseWidth = (rw - (keyboard.columnCount + 1) * gap) / keyboard.columnCount
                        view.layoutParams.width = keyBaseWidth * key.columnSpan + (key.columnSpan - 1) * gap
                        view.layoutParams.height = view.measuredHeight * key.rowSpan + (key.rowSpan - 1) * gap
                    } else {
                        val weightSum = row.keys.sumOf { it.columnWeight }
                        val keyWidth = (rw - (row.keys.size + 1) * gap) * key.columnWeight / weightSum
                        view.layoutParams.width = keyWidth.toInt()
                        view.layoutParams.height = view.measuredHeight
                    }
                }
                if(minHeight > view.layoutParams.height)
                    minHeight = view.layoutParams.height
                measureChild(view, rw, rh)
            }
            childHeight += minHeight + gap
        }

        firstMeasure = false
        val vh = childHeight
        setMeasuredDimension(rw, vh);
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (rowIdx in 0 until  keyboardView.size) {
            val rowViews = keyboardView[rowIdx]
            val row = keyboard.rows[rowIdx]
            var rowWidth = 0
            for (keyIdx in 0 until rowViews.size) {
                val view = rowViews[keyIdx]
                val key = row.keys[keyIdx]
                if (row.type == NHKeyboard.Row.Type.SPAN) {
                    val avgHeight = (view.measuredHeight - gap * (key.rowSpan - 1)) / key.rowSpan
                    val avgWidth =
                        (measuredWidth - (keyboard.columnCount + 1) * gap) / keyboard.columnCount
                    val left = key.column * avgWidth + gap * (key.column + 1)
                    val top = key.row * avgHeight + gap * (key.row + 1)
                    val right = left + view.measuredWidth
                    val bottom = top + view.measuredHeight
                    view.layout(left, top, right, bottom)
                } else {
                    val avgHeight = view.measuredHeight
                    val left = rowWidth + gap
                    val top = key.row * avgHeight + gap * (key.row + 1)
                    val right = left + view.measuredWidth
                    rowWidth +=  view.measuredWidth + gap
                    val bottom = top + view.measuredHeight
                    view.layout(left, top, right, bottom)
                }
            }
        }
    }

    fun setNHKeyboard(keyboard:NHKeyboard) {
        this.keyboard = keyboard
        firstMeasure = true
        removeAllViews()
        keyboardView.clear()
        keyboard.rows.forEach {  row ->
            val rowView = mutableListOf<View>()
            row.keys.forEach { key ->
                val view = TextView(context).apply {
                    setPadding(0,15,0,15)
                    setBackgroundResource(R.drawable.btn_bg_selector)
                    tag = key
                    gravity = Gravity.CENTER
                    maxLines = 1
                    text = key.label
                    setOnClickListener {
                        onKeyPress?.invoke(key)
                    }
                }
                rowView.add(view)
                addView(view)
            }
            keyboardView.add(rowView)
        }
        invalidate()
    }
}