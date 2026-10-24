package com.yywspace.anethack.gamedata

/**
 * 价格鉴定用的规则，来自 assets/gamedata/price_rules.json。
 *
 * [tradeTypesRegex] 交易信息正则（buy / sell），
 * [objectTypesRegex] 物品类型识别正则（Potion / Scroll / …）。
 */
data class PriceRules(
    val tradeTypesRegex: Map<String, String> = emptyMap(),
    val objectTypesRegex: Map<String, String> = emptyMap()
)
