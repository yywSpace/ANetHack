package com.yywspace.anethack.gamedata

import android.content.Context
import android.text.Spannable
import android.view.MotionEvent
import android.widget.TextView

/**
 * 消息里出现的怪物名，做成高亮可点击的文本段。
 *
 * 只有 look 指令产生的消息才会被解析，调用方负责判断来源。
 */
object MonsterLink {

    private val link = MessageLink(
        MessageLinkKind.MONSTER,
        { GameData.monsters },
        { listOf(it.name) },
        { it.colorValue }
    )

    fun highlight(
        context: Context,
        text: Spannable,
        from: Int,
        to: Int,
        onClick: (Monster) -> Unit
    ) {
        GameData.ensureLoaded(context)
        link.highlight(text, from, to, onClick)
    }

    fun spanAtTouch(widget: TextView, event: MotionEvent): MessageNameSpan<Monster>? =
        link.spanAtTouch(widget, event)

    fun spanAtLine(widget: TextView, event: MotionEvent): MessageNameSpan<Monster>? =
        link.spanAtLine(widget, event)
}
