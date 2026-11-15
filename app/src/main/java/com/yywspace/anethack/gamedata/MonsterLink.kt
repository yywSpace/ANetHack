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

    private val names = NameLineResolver(
        { GameData.monsters },
        { listOf(it.name) }
    )

    private val link = MessageLink(
        MessageLinkKind.MONSTER,
        { it.colorValue }
    ) { line -> names.resolve(line) }

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

/** 名称匹配策略：把一行里出现的已知名称映射为对象。 */
class NameLineResolver<T>(
    private val values: () -> List<T>,
    private val labelsOf: (T) -> List<String>
) {

    @Volatile
    private var nameRegex: Regex? = null

    @Volatile
    private var regexSize = -1

    private var aliasIndex: Map<String, T> = emptyMap()

    fun resolve(line: String): T? {
        val regex = regex() ?: return null
        val match = regex.find(line) ?: return null
        return aliasIndex[match.value.lowercase()]
    }

    private fun regex(): Regex? {
        val pairs = values().flatMap { value ->
            labelsOf(value).map { it.lowercase() to value }
        }
        if (pairs.isEmpty())
            return null
        val cached = nameRegex
        if (cached != null && regexSize == pairs.size)
            return cached
        val index = pairs.toMap()
        val body = index.keys.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        aliasIndex = index
        return Regex("(?<![\\w-])($body)", RegexOption.IGNORE_CASE).also {
            nameRegex = it
            regexSize = pairs.size
        }
    }
}
}
