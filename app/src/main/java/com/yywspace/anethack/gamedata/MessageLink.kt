package com.yywspace.anethack.gamedata

import android.text.Spannable
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

enum class MessageLinkKind {
    MONSTER,
    ITEM
}

/**
 * 消息里的可点击名称匹配器。
 *
 * 只负责逐行解析和 span/触摸处理；每一行由 [resolveLine] 决定对应对象。
 * 具体怎么匹配（名称、类别、外观）由调用方决定。
 */
class MessageLink<T>(
    private val kind: MessageLinkKind,
    private val colorOf: (T) -> Int,
    private val resolveLine: (String) -> T?
) {

    fun highlight(
        text: Spannable,
        from: Int,
        to: Int,
        onClick: (T) -> Unit
    ) {
        if (to <= from)
            return
        var lineStart = from
        while (lineStart <= to) {
            val lineEnd = indexOfNewline(text, lineStart, to)
            if (lineEnd > lineStart) {
                val line = text.subSequence(lineStart, lineEnd).toString()
                val value = resolveLine(line)
                if (value != null) {
                    text.setSpan(
                        MessageNameSpan(value, kind, colorOf(value), onClick),
                        lineStart,
                        lineEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            if (lineEnd >= to)
                break
            lineStart = lineEnd + 1
        }
    }

    fun spanAtTouch(widget: TextView, event: MotionEvent): MessageNameSpan<T>? {
        val spannable = widget.text as? Spanned ?: return null
        val textLayout = widget.layout ?: return null
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        val line = textLayout.getLineForVertical(y.toInt())
        if (y < textLayout.getLineTop(line) || y > textLayout.getLineBottom(line))
            return null
        if (x < textLayout.getLineLeft(line) || x > textLayout.getLineRight(line))
            return null
        val offset = textLayout.getOffsetForHorizontal(line, x)
        return spanAt(spannable, offset, offset)
    }

    fun spanAtLine(widget: TextView, event: MotionEvent): MessageNameSpan<T>? {
        val spannable = widget.text as? Spanned ?: return null
        val textLayout = widget.layout ?: return null
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        val line = textLayout.getLineForVertical(y.toInt())
        if (y < textLayout.getLineTop(line) || y > textLayout.getLineBottom(line))
            return null
        return spanAt(spannable, textLayout.getLineStart(line), textLayout.getLineEnd(line))
    }

    @Suppress("UNCHECKED_CAST")
    private fun spanAt(spannable: Spanned, start: Int, end: Int): MessageNameSpan<T>? =
        spannable.getSpans(start, end, MessageNameSpan::class.java)
            .firstOrNull { it.kind == kind } as? MessageNameSpan<T>

    private fun indexOfNewline(text: CharSequence, from: Int, limit: Int): Int {
        for (i in from until limit) {
            if (text[i] == '\n')
                return i
        }
        return limit
    }
}

/** 可点击名称：颜色、下划线和点击对象都由调用方决定。 */
class MessageNameSpan<T>(
    val value: T,
    val kind: MessageLinkKind,
    private val color: Int,
    private val onClickValue: (T) -> Unit
) : ClickableSpan() {

    override fun onClick(widget: View) {
        onClickValue(value)
    }

    override fun updateDrawState(ds: android.text.TextPaint) {
        ds.color = color
        ds.isUnderlineText = true
    }
}