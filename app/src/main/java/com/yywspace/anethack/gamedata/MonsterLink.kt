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
 * 只有 **look 指令产生的消息** 才会被解析（见 NHMessage.fromLook）
 *
 * 两处点击判定不同：
 * - [spanAtTouch]：必须点在文字的左右边界内（上方三行消息栏用，点右侧空白不触发）；
 * - [spanAtLine]：只要落在这一行内就算（完整消息列表用，点这一行的空白也触发）。
 */
object MonsterLink {

    @Volatile
    private var nameRegex: Regex? = null

    @Volatile
    private var regexSize = -1

    /**
     * 在 [text] 的 [from, to) 区间里找怪物名：哪一行出现了怪物名，就把**整行**标成可点击，
     * 点击时回调那一行里匹配到的名字。
     *
     * 调用方只对 look 来源的消息调用本方法。设置里关掉"怪物信息"时直接返回。
     */
    fun highlight(
        context: Context,
        text: Spannable,
        from: Int,
        to: Int,
        onClick: (String) -> Unit
    ) {
        if (!SharedPreferencesUtils(context).monsterInfo)
            return
        if (to <= from)
            return
        GameData.ensureLoaded(context)
        val regex = regex() ?: return
        var lineStart = from
        while (lineStart <= to) {
            val lineEnd = indexOfNewline(text, lineStart, to)
            if (lineEnd > lineStart) {
                val line = text.subSequence(lineStart, lineEnd).toString()
                val match = regex.find(line)
                val monster = match?.let { GameData.monsterOfLine(it.value) }
                if (match != null && monster != null) {
                    // 整行高亮，名字只用来决定颜色和点开哪只怪物
                    text.setSpan(
                        MonsterNameSpan(
                            match.value,
                            NHColor.fromInt(monster.colorCode).toColor(),
                            onClick
                        ),
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

    /** 在 [from, limit) 里找下一个换行，没有则返回 [limit] */
    private fun indexOfNewline(text: CharSequence, from: Int, limit: Int): Int {
        for (i in from until limit) {
            if (text[i] == '\n')
                return i
        }
        return limit
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

    /** 所有怪物名拼成的正则：按长度倒序，长名优先匹配 */
    private fun regex(): Regex? {
        val names = GameData.monsters.map { it.name }
        if (names.isEmpty())
            return null
        val cached = nameRegex
        if (cached != null && regexSize == names.size)
            return cached
        val body = names.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        return Regex("(?<![\\w-])($body)(?![\\w-])", RegexOption.IGNORE_CASE).also {
            nameRegex = it
            regexSize = names.size
        }
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
