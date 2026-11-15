package com.yywspace.anethack.gamedata

import android.content.Context
import android.text.Spannable
import android.view.MotionEvent
import android.widget.TextView
import com.yywspace.anethack.SharedPreferencesUtils

/** 一条 look 消息里的物品匹配结果；同一结果可能对应多个物品。 */
data class ItemMatch(
    val label: String,
    val items: List<Item>
)

object ItemLink {

    private val link = MessageLink<ItemMatch>(
        MessageLinkKind.ITEM,
        { match -> match.items.first().colorValue }
    ) { line -> resolveItemLine(line) }

    fun highlight(
        context: Context,
        text: Spannable,
        from: Int,
        to: Int,
        onClick: (ItemMatch) -> Unit
    ) {
        if (!SharedPreferencesUtils(context).itemInfo)
            return
        GameData.ensureLoaded(context)
        link.highlight(text, from, to, onClick)
    }

    fun spanAtTouch(widget: TextView, event: MotionEvent): MessageNameSpan<ItemMatch>? =
        link.spanAtTouch(widget, event)

    fun spanAtLine(widget: TextView, event: MotionEvent): MessageNameSpan<ItemMatch>? =
        link.spanAtLine(widget, event)

    private fun resolveItemLine(line: String): ItemMatch? {
        val lookup = GameData.lookupItemsForTitle(line) ?: return null
        val items = lookup.specific.takeIf { it.isNotEmpty() } ?: lookup.category
        if (items.isEmpty())
            return null
        return ItemMatch(line, items)
    }
}