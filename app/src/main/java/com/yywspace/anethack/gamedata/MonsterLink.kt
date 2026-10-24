package com.yywspace.anethack.gamedata

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.yywspace.anethack.SharedPreferencesUtils
import com.yywspace.anethack.entity.NHColor

/**
 * 消息里出现的怪物名，做成高亮可点击的文本段。
 *
 * 规则：**整行**文字正好等于某个怪物名时才高亮（例如一行只有 "giant ant"），
 * 句子里夹带的怪物名不处理。高亮颜色用这只怪物自己的颜色。
 *
 * 两处点击判定不同：
 * - [spanAtTouch]：必须点在文字的左右边界内（上方三行消息栏用，点右侧空白不触发）；
 * - [spanAtLine]：只要落在这一行内就算（完整消息列表用，点这一行的空白也触发）。
 */
object MonsterLink {

    /**
     * 在 [text] 上标出"整行就是一个怪物名"的行；点击时回调名字。
     *
     * 设置里关掉"怪物信息"后直接返回，消息列表完全不动（不高亮、不建 span）。
     */
    fun highlight(context: Context, text: Spannable, onClick: (String) -> Unit) {
        if (!SharedPreferencesUtils(context).monsterInfo)
            return
        GameData.ensureLoaded(context)
        var start = 0
        while (start <= text.length) {
            val end = indexOfNewline(text, start)
            markLine(text, start, end, onClick)
            if (end >= text.length)
                break
            start = end + 1
        }
    }

    /**
     * 严格判定：竖直落在该行内、水平落在该行文字的左右边界内，且位置上有怪物名。
     * 点在文字右侧的空白处会返回 null。
     */
    fun spanAtTouch(widget: TextView, event: MotionEvent): MonsterNameSpan? {
        val spannable = widget.text as? Spanned ?: return null
        val textLayout = widget.layout ?: return null
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY

        val line = textLayout.getLineForVertical(y.toInt())
        // 竖直方向必须落在这一行内
        if (y < textLayout.getLineTop(line) || y > textLayout.getLineBottom(line))
            return null
        // 水平方向必须落在这一行的文字范围内（行尾空白不算）
        if (x < textLayout.getLineLeft(line) || x > textLayout.getLineRight(line))
            return null

        val offset = textLayout.getOffsetForHorizontal(line, x)
        return spannable.getSpans(offset, offset, MonsterNameSpan::class.java).firstOrNull()
    }

    /** 宽松判定：触摸点落在这一行内即可（这一行是怪物名时，点空白也算） */
    fun spanAtLine(widget: TextView, event: MotionEvent): MonsterNameSpan? {
        val spannable = widget.text as? Spanned ?: return null
        val textLayout = widget.layout ?: return null
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        val line = textLayout.getLineForVertical(y.toInt())
        if (y < textLayout.getLineTop(line) || y > textLayout.getLineBottom(line))
            return null
        return spannable.getSpans(
            textLayout.getLineStart(line),
            textLayout.getLineEnd(line),
            MonsterNameSpan::class.java
        ).firstOrNull()
    }

    /** 处理 [start, end) 这一行：整行（去掉首尾空白）是怪物名才加 span */
    private fun markLine(
        text: Spannable,
        start: Int,
        end: Int,
        onClick: (String) -> Unit
    ) {
        if (end <= start)
            return
        val line = text.subSequence(start, end).toString()
        val trimmed = line.trim()
        if (trimmed.isEmpty())
            return
        // O(1) 字典查（消息列表滚动时会大量调用）；索引里同时有完整名和基础名
        val monster = GameData.monsterOfLine(trimmed) ?: return
        val lead = line.indexOfFirst { !it.isWhitespace() }
        val trail = line.indexOfLast { !it.isWhitespace() }
        if (lead !in 0..trail)
            return
        text.setSpan(
            MonsterNameSpan(trimmed, NHColor.fromName(monster.color).toColor(), onClick),
            start + lead,
            start + trail + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun indexOfNewline(text: CharSequence, from: Int): Int {
        for (i in from until text.length) {
            if (text[i] == '\n')
                return i
        }
        return text.length
    }
}

/** 可点击的怪物名：用怪物自己的颜色 + 下划线，点击回调名字 */
class MonsterNameSpan(
    val name: String,
    private val color: Int,
    private val onClickName: (String) -> Unit
) : ClickableSpan() {

    override fun onClick(widget: View) {
        onClickName(name)
    }

    override fun updateDrawState(ds: TextPaint) {
        ds.color = color
        ds.isUnderlineText = true
        ds.bgColor = Color.TRANSPARENT
    }
}
