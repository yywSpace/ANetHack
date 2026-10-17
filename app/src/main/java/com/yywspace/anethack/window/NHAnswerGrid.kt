package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.yywspace.anethack.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 把问答的候选字符画成一片格子。
 *
 * 一次问答只用一个 View：不 inflate 子 View、不用 RecyclerView，
 * 绘制成本只是每个字符一次 drawText。
 */
class NHAnswerGrid(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val answers = mutableListOf<Pair<Char, Int>>()
    private var pressedIndex = -1
    private var longPressFired = false
    private var columns = 1

    private val cellRect = RectF()
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val cellColor = ContextCompat.getColor(context, R.color.purple_500)
    private val pressedColor = ContextCompat.getColor(context, R.color.purple_700)
    private val textColor = ContextCompat.getColor(context, R.color.white)

    private val density = resources.displayMetrics.density
    private val cellHeight = 38f * density
    private val cellGap = 1.5f * density
    private val cornerRadius = 6f * density
    private val minCellWidth = 48f * density
    private val textSize = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)

    /** 点击某个格子：下标、该格子的字符与数量 */
    var onPick: ((index: Int, answer: Pair<Char, Int>) -> Unit)? = null

    /** 长按某个格子：下标（用于改数量） */
    var onLongPress: ((index: Int) -> Unit)? = null

    private val longPressRunnable = Runnable {
        if (pressedIndex >= 0) {
            longPressFired = true
            if (answers[pressedIndex].second > 0)
                onLongPress?.invoke(pressedIndex)
            pressedIndex = -1
            invalidate()
        }
    }

    init {
        textPaint.textSize = textSize
    }

    /** 每次提问时刷新整片格子 */
    fun submit(items: List<Pair<Char, Int>>) {
        answers.clear()
        answers.addAll(items)
        pressedIndex = -1
        longPressFired = false
        removeCallbacks(longPressRunnable)
        requestLayout()
        invalidate()
    }

    /** 长按改完数量后局部刷新 */
    fun updateCount(index: Int, count: Int) {
        if (index !in answers.indices)
            return
        answers[index] = answers[index].copy(second = count)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = width - paddingLeft - paddingRight
        // 一个字符不需要大格子：按最小格宽尽量多排几列，格子多时行数少、更紧凑
        columns = if (answers.isEmpty()) 1 else
            min(answers.size, max(3, (available / minCellWidth).toInt()))
        val rows = ceil(answers.size / columns.toFloat()).toInt()
        val height = (rows * cellHeight + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(width, max(height, 0))
    }

    override fun onDraw(canvas: Canvas) {
        val baseline = (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        answers.forEachIndexed { index, answer ->
            val rect = cellRect(index)
            cellPaint.color = if (index == pressedIndex) pressedColor else cellColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
            textPaint.color = textColor
            canvas.drawText(label(answer), rect.centerX(), rect.centerY() - baseline, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = indexAt(event.x, event.y)
                if (index < 0)
                    return false
                pressedIndex = index
                longPressFired = false
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedIndex >= 0 && indexAt(event.x, event.y) != pressedIndex) {
                    pressedIndex = -1
                    removeCallbacks(longPressRunnable)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                val index = indexAt(event.x, event.y)
                if (!longPressFired && index >= 0 && index == pressedIndex)
                    onPick?.invoke(index, answers[index])
                pressedIndex = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                pressedIndex = -1
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    private fun cellRect(index: Int): RectF {
        val column = index % columns
        val row = index / columns
        val width = (getWidth() - paddingLeft - paddingRight) / columns.toFloat()
        val left = paddingLeft + column * width
        val top = paddingTop + row * cellHeight
        cellRect.set(left + cellGap, top + cellGap,
            left + width - cellGap, top + cellHeight - cellGap)
        return cellRect
    }

    private fun indexAt(x: Float, y: Float): Int {
        if (answers.isEmpty())
            return -1
        val width = (getWidth() - paddingLeft - paddingRight) / columns.toFloat()
        if (width <= 0f)
            return -1
        val column = ((x - paddingLeft) / width).toInt()
        val row = ((y - paddingTop) / cellHeight).toInt()
        if (column < 0 || column >= columns || row < 0)
            return -1
        val index = row * columns + column
        return if (index in answers.indices) index else -1
    }

    private fun label(answer: Pair<Char, Int>): String = when {
        answer.first.code == 27 -> context.getString(R.string.button_esc)
        answer.second > 1 -> "[${answer.second}]${answer.first}"
        else -> answer.first.toString()
    }
}
