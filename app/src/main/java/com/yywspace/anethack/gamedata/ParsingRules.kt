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
 * [objectTypesRegex]：物品类别正则。
 * - key 是类别名（Potion / Ring / Weapon / Helm ...）
 * - value 同时覆盖未鉴定外观和已鉴定全称：
 *   a cloudy potion      → Potion
 *   potion of healing    → Potion
 * scroll of enchant armor → Scroll
 * ring mail            → Suit
 * - Gem 规则额外排除 wand，避免把法杖外观误判成宝石
 *
 * [monsterNameSuffixRegex]：怪物名的多形态后缀，如 (human)/(animal)。
 */
data class ParsingRules(
    val tradeTypesRegex: Map<String, String> = emptyMap(),
    val objectTypesRegex: Map<String, String> = emptyMap(),
    val monsterNameSuffixRegex: String = ""
) {
    @Transient
    private var compiledTypeRegexes: List<Pair<String, Regex>>? = null

    /** 去掉怪物名的多形态后缀："werejackal (human)" → "werejackal"。 */
    fun monsterBaseName(name: String): String {
        if (monsterNameSuffixRegex.isBlank())
            return name
        return name.replace(
            Regex(monsterNameSuffixRegex, RegexOption.IGNORE_CASE),
            ""
        ).trim()
    }

    /**
     * 从物品显示名称/外观判断类别。
     *
     * 选择规则：
     * 1. 优先选择匹配到文本结尾的类别；
     * 2. 同一位置有多个时，取匹配更长的类别。
     *
     * 这样不需要为 "scroll of enchant armor" 这类组合写代码特例：
     * Scroll 规则匹配整段，Suit 只匹配末尾的 armor，前者更长。
     *
     * 例子：
     * a cloudy potion         → Potion
     * potion of healing       → Potion
     * scroll of enchant armor → Scroll
     * ring mail               → Suit
     * meat ring               → Food
     */
    fun itemTypeOf(text: String): String? {
        val regexes = compiledTypeRegexes ?: objectTypesRegex.map { (type, regex) ->
            type to Regex(regex, RegexOption.IGNORE_CASE)
        }.also { compiledTypeRegexes = it }

        var suffix: Pair<Int, String>? = null
        var longest: Pair<Int, String>? = null
        regexes.forEach { (type, regex) ->
            regex.findAll(text).forEach { match ->
                val len = match.range.last - match.range.first + 1
                if (match.range.last + 1 == text.length &&
                    (suffix == null || len > suffix!!.first)) {
                    suffix = len to type
                }
                if (longest == null || len > longest!!.first) {
                    longest = len to type
                }
            }
        }
        return suffix?.second ?: longest?.second
    }

    /**
     * 从交易消息中解析价格、买卖方向和物品类别。
     * 解析不到交易信息时返回 null。
     */
    fun tradeInfoOf(info: String): TradeInfo? {
        var parsed: TradeInfo? = null
        tradeTypesRegex.forEach { (tradeType, regex) ->
            val matcher = Pattern.compile(regex).matcher(info)
            while (matcher.find()) {
                val objDesc = matcher.group("objDesc") ?: ""
                parsed = TradeInfo(
                    price = matcher.group("objPrice"),
                    mode = tradeType,
                    itemType = itemTypeOf(objDesc)
                )
            }
        }
        return parsed
    }
}
