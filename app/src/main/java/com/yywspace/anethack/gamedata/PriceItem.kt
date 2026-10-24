package com.yywspace.anethack.gamedata

/**
 * 价格鉴定用的物品条目，只保留价格鉴定需要的信息：
 * [type] 物品类别（Potion / Scroll / …）、[name] 名称、[cost] 基础价格。
 *
 * 对应 assets/gamedata/price_items.json 里的每一条。
 */
data class PriceItem(
    val type: String,
    val name: String,
    val cost: Int
)
