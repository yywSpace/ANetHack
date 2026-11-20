package com.yywspace.anethack.gamedata

import android.content.Context
import android.text.Spannable
import android.view.MotionEvent
import android.widget.TextView
import com.yywspace.anethack.SharedPreferencesUtils

/** 一条 look 消息里的怪物匹配结果；同名多形态怪物会有多个。 */
data class MonsterMatch(
    val label: String,
    val monsters: List<Monster>
)

/**
 * 消息里出现的怪物名，做成高亮可点击的文本段。
 *
 * 只有 look 指令产生的消息才会被解析，调用方负责判断来源。
 */
object MonsterLink {

    private val namesByLower: Map<String, List<Monster>> by lazy {
        GameData.monsters.groupBy { it.name.lowercase() }
    }

    private val link = MessageLink(
        MessageLinkKind.MONSTER,
        { it.monsters.first().colorValue }
    ) { line ->
        val matched = resolveMonsters(line)
        if (matched.isEmpty()) null else MonsterMatch(line, matched)
    }

    fun highlight(
        context: Context,
        text: Spannable,
        from: Int,
        to: Int,
        onClick: (MonsterMatch) -> Unit
    ) {
        if (!SharedPreferencesUtils(context).monsterInfo)
            return
        GameData.ensureLoaded(context)
        link.highlight(text, from, to, onClick)
    }

    fun spanAtTouch(widget: TextView, event: MotionEvent): MessageNameSpan<MonsterMatch>? =
        link.spanAtTouch(widget, event)

    fun spanAtLine(widget: TextView, event: MotionEvent): MessageNameSpan<MonsterMatch>? =
        link.spanAtLine(widget, event)
    private fun resolveMonsters(line: String): List<Monster> {
        val lower = line.lowercase()
        val match = namesByLower.keys
            .filter { lower.contains(it) }
            .maxByOrNull { it.length }
            ?: return emptyList()
        return namesByLower[match].orEmpty()
    }
}
