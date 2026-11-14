package com.yywspace.anethack.gamedata

/**
 * 价格鉴定用的规则，来自 assets/gamedata/price_rules.json。
 *
 * [tradeTypesRegex] 交易信息正则（buy / sell），
 * [objectTypesRegex] 只用于随机外观/未鉴定消息的类型兜底；
 * 有完整名称或固定外观时，NHPriceID 会先按物品数据识别。
 */
data class PriceRules(
    val tradeTypesRegex: Map<String, String> = emptyMap(),
    val objectTypesRegex: Map<String, String> = emptyMap()
)
