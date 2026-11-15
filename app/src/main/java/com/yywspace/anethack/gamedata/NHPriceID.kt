package com.yywspace.anethack.gamedata

import android.content.Context

/**
 * 价格鉴定：从 [GameData] 取一次物品数据，之后买价/卖价推算、类型解析都在这类里完成。
 */
class NHPriceID(context: Context) {

    /** 本次鉴定使用的物品数据（构造时从 GameData 取一次） */
    private val items: List<Item>

    init {
        GameData.ensureLoaded(context)
        items = GameData.items
    }

    /** 所有物品类型（按名称排序） */
    fun getObjTypes(): List<String> = items.map { GameData.itemType(it) }.distinct().sorted()

    /** 某类型的物品，按基础价格、名称排序 */
    private fun itemsOf(type: String): List<Item> =
        items.filter { GameData.itemType(it) == type }.sortedWith(objComparator)

    /** 从物品描述（如 "a smoky potion"）解析所属类型，解析不出返回空串。 */
    fun parseObjType(objDesc: String): String =
        GameData.itemTypeOf(objDesc) ?: ""

    /** 交易信息解析委托给规则类。 */
    fun tradeInfoOf(info: String): TradeInfo? =
        GameData.parsingRules.tradeInfoOf(info) { GameData.itemTypeOf(it) }

    private fun calcBuyPrice(price: Int, ch: Int, surcharge: Boolean, sucker: Boolean): Int {
        var m = 1
        var d = 1
        var pri = price.toDouble()
        if (surcharge) { m *= 4; d *= 3 }
        if (sucker) { m *= 4; d *= 3 }
        when {
            ch >= 19 -> { m *= 1; d *= 2 } // 50%
            ch >= 18 -> { m *= 2; d *= 3 } // 67%
            ch >= 16 -> { m *= 3; d *= 4 } // 75%
            ch >= 11 -> { m *= 1; d *= 1 } // 100%
            ch >= 8 -> { m *= 4; d *= 3 } // 133%
            ch >= 6 -> { m *= 3; d *= 2 } // 150%
            else -> { m *= 2; d *= 1 } // 200%
        }
        pri *= m
        if (d > 1) {
            pri *= 10
            pri /= d
            pri += 5
            pri /= 10
            pri = kotlin.math.floor(pri)
        }
        if (pri <= 0)
            pri = 1.0
        return pri.toInt()
    }

    private fun calcSellPrice(price: Int, surcharge: Boolean, sucker: Boolean): Int {
        var m = 1
        var d = if (sucker) 3 else 2
        var pri = price.toDouble()
        if (price > 1 && surcharge) {
            m *= 3; d *= 4
        }
        if (pri > 1) {
            pri *= m
            pri *= 10
            pri /= d
            pri += 5
            pri /= 10
            pri = kotlin.math.floor(pri)
            if (pri < 1)
                pri = 1.0
        }
        return pri.toInt()
    }

    /** 按完整查询条件筛选物品。 */
    fun search(query: PriceQuery): List<Item> {
        val range = PriceRange.parse(query.price)
        val surcharge = query.surcharge != Surcharge.NONE
        val source = itemsOf(query.type)
        return when (query.mode) {
            PriceMode.BASE -> {
                if (query.price.isBlank())
                    source
                else
                    range?.let { r -> source.filter { it.price in r.from..r.to } } ?: emptyList()
            }
            PriceMode.BUY -> {
                if (range == null || query.charisma <= 0)
                    return emptyList()
                source.filter { item ->
                    inRange(calcBuyPrice(item.price, query.charisma, false, surcharge), range) ||
                        inRange(calcBuyPrice(item.price, query.charisma, true, surcharge), range)
                }
            }
            PriceMode.SELL -> {
                if (range == null)
                    return emptyList()
                source.filter { item ->
                    inRange(calcSellPrice(item.price, false, surcharge), range) ||
                        inRange(calcSellPrice(item.price, true, surcharge), range)
                }
            }
        }
    }

    private fun inRange(value: Int, range: PriceRange): Boolean =
        value in range.from..range.to

    companion object {
        /** 物品列表顺序：先基础价格，再名称 */
        private val objComparator = compareBy<Item>({ it.price }, { it.name })
    }
}
