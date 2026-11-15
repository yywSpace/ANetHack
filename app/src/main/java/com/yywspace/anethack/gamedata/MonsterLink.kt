package com.yywspace.anethack.gamedata

import android.content.Context
import android.text.Spannable
import android.view.MotionEvent
import android.widget.TextView

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

    private val names = NameLineResolver(
        { GameData.monsters },
        { listOf(it.name) }
    )

    private val link = MessageLink(
        MessageLinkKind.MONSTER,
        { it.monsters.first().colorValue }
    ) { line ->
        val matched = names.resolve(line)
        if (matched.isEmpty()) null else MonsterMatch(line, matched)
    }

    fun highlight(
        context: Context,
        text: Spannable,
        from: Int,
        to: Int,
        onClick: (MonsterMatch) -> Unit
    ) {
        GameData.ensureLoaded(context)
        link.highlight(text, from, to, onClick)
    }

    fun spanAtTouch(widget: TextView, event: MotionEvent): MessageNameSpan<MonsterMatch>? =
        link.spanAtTouch(widget, event)

    fun spanAtLine(widget: TextView, event: MotionEvent): MessageNameSpan<MonsterMatch>? =
        link.spanAtLine(widget, event)
}

/** 名称匹配策略：把一行里出现的已知名称映射为同类对象列表。 */
class NameLineResolver<T>(
    private val values: () -> List<T>,
    private val labelsOf: (T) -> List<String>
) {

    @Volatile
    private var nameRegex: Regex? = null

    @Volatile
    private var regexSize = -1

    private var aliasIndex: Map<String, List<T>> = emptyMap()

    fun resolve(line: String): List<T> {
        val regex = regex() ?: return emptyList()
        val match = regex.find(line) ?: return emptyList()
        return aliasIndex[match.value.lowercase()].orEmpty()
    }

    private fun regex(): Regex? {
        val groups = LinkedHashMap<String, MutableList<T>>()
        values().forEach { value ->
            labelsOf(value).forEach { label ->
                groups.getOrPut(label.lowercase()) { mutableListOf() }.add(value)
            }
        }
        if (groups.isEmpty())
            return null
        val cached = nameRegex
        if (cached != null && regexSize == groups.size)
            return cached
        val body = groups.keys.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        aliasIndex = groups
        return Regex("(?<![\\w-])($body)", RegexOption.IGNORE_CASE).also {
            nameRegex = it
            regexSize = groups.size
        }
    }
}