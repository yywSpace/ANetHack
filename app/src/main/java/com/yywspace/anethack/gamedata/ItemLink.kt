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
/** 一条消息里的物品名匹配结果；同一外观可能对应多个物品。 */
data class ItemMatch(
    val label: String,
    val items: List<Item>
)

object ItemLink {

    private val link = MessageLink(
        MessageLinkKind.ITEM,
        { matches() },
        { listOf(it.label) },
        { it.items.first().colorValue }
    )

    fun highlight(
        context: Context,
        text: Spannable,
        from: Int,
        to: Int,
        onClick: (ItemMatch) -> Unit
    ) {
        GameData.ensureLoaded(context)
        link.highlight(text, from, to, onClick)
    }

    fun spanAtTouch(widget: TextView, event: MotionEvent): MessageNameSpan<ItemMatch>? =
        link.spanAtTouch(widget, event)

    fun spanAtLine(widget: TextView, event: MotionEvent): MessageNameSpan<ItemMatch>? =
        link.spanAtLine(widget, event)

    private fun matches(): List<ItemMatch> {
        val groups = LinkedHashMap<String, MutableList<Item>>()
        fun add(label: String, item: Item) {
            groups.getOrPut(label) { mutableListOf() }.add(item)
        }
        GameData.items.forEach { item ->
            add(item.name, item)
            item.appearance
                ?.takeIf { it != item.name }
                ?.let { add(it, item) }
        }
        return groups.map { (label, items) -> ItemMatch(label, items) }
    }
}
