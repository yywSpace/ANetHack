package com.yywspace.anethack.gamedata

import java.util.regex.Pattern


/** 一条交易消息解析出的价格、方向和物品类别。 */
data class TradeInfo(
    val price: String? = null,
    val mode: String? = null,
    val itemType: String? = null
)

/**
 * 文本解析/识别规则，来自 assets/gamedata/parsing_rules.json。
 *
 * [tradeTypesRegex]：交易消息正则。
 * - key 是买卖方向（buy / sell）
 * - 必须带命名组 objDesc（物品描述）和 objPrice（价格）
 *
 * [objectTypesRegex]：例外规则，不是完整类别表。
 * - 精确物品名称/外观由 Item 数据负责，不写进这里
 * - 这里只保留无法从物品名称命中的通用词/未鉴定外观：
 *   a cloudy potion      → Potion
 *   a sapphire ring      → Ring
 *   a crystal wand       → Wand
 * - Gem 只保留 gem/stone 这类通用词；具体宝石名称由物品名称匹配，
 *   不把法杖等随机外观写进 Gem 规则

 */
data class ParsingRules(
    val tradeTypesRegex: Map<String, String> = emptyMap(),
    val objectTypesRegex: Map<String, String> = emptyMap(),
) {
    @Transient
    private var compiledTypeRegexes: List<Pair<String, Regex>>? = null

    /**
     * 从物品显示名称/外观判断类别。
     *
     * 类别正则只保留通用类别词，因此按“最早出现的类别词”选择：
     * scroll of enchant armor → Scroll（scroll 在 armor 之前）
     * ring of protection      → Ring
     * ring mail               → 精确名称由调用方优先处理
     *
     * 同一位置有多个命中时，取匹配更长的。
     */
    fun itemTypeOf(text: String): String? {
        val regexes = compiledTypeRegexes ?: objectTypesRegex.map { (type, regex) ->
            type to Regex(regex, RegexOption.IGNORE_CASE)
        }.also { compiledTypeRegexes = it }

        var bestStart = Int.MAX_VALUE
        var bestLength = 0
        var bestType: String? = null
        regexes.forEach { (type, regex) ->
            val match = regex.find(text) ?: return@forEach
            val start = match.range.first
            val length = match.range.last - match.range.first + 1
            if (start < bestStart || (start == bestStart && length > bestLength)) {
                bestStart = start
                bestLength = length
                bestType = type
            }
        }
        return bestType
    }

    /**
     * 从交易消息中解析价格、买卖方向和物品类别。
     * 解析不到交易信息时返回 null。
     */
    fun tradeInfoOf(
        info: String,
        resolveType: (String) -> String?
    ): TradeInfo? {
        var parsed: TradeInfo? = null
        tradeTypesRegex.forEach { (tradeType, regex) ->
            val matcher = Pattern.compile(regex).matcher(info)
            while (matcher.find()) {
                val objDesc = matcher.group("objDesc") ?: ""
                parsed = TradeInfo(
                    price = matcher.group("objPrice"),
                    mode = tradeType,
                    itemType = resolveType(objDesc)
                )
            }
        }
        return parsed
    }
}
