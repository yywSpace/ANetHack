package com.yywspace.anethack.gamedata

import android.content.Context
import android.text.Spannable
import android.view.MotionEvent
import android.widget.TextView

/**
 * 消息里出现的物品名，做成高亮可点击的文本段。
 *
 * 物品数据已经保存完整名称，例如 spellbook of healing。
 */
object ItemLink {

    private val link = MessageLink(
        MessageLinkKind.ITEM,
        { GameData.items },
        { listOf(it.name) },
        { it.colorValue }
    )

    fun highlight(
        context: Context,
        text: Spannable,
        from: Int,
        to: Int,
        onClick: (Item) -> Unit
    ) {
        GameData.ensureLoaded(context)
        link.highlight(text, from, to, onClick)
    }

    fun spanAtTouch(widget: TextView, event: MotionEvent): MessageNameSpan<Item>? =
        link.spanAtTouch(widget, event)

    fun spanAtLine(widget: TextView, event: MotionEvent): MessageNameSpan<Item>? =
        link.spanAtLine(widget, event)
}
