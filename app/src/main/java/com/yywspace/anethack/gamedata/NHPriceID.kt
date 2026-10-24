package com.yywspace.anethack.gamedata

import android.content.Context
import java.util.regex.Pattern

/**
 * 价格鉴定：从 [GameData] 取一次物品数据，之后买价/卖价推算、类型解析都在这类里完成。
 */
class NHPriceID(context: Context) {

    /** 本次鉴定使用的物品数据（构造时从 GameData 取一次） */
    private val items: List<PriceItem>

    init {
        GameData.ensureLoaded(context)
        items = GameData.priceItems
    }

    /** 所有物品类型（按名称排序） */
    fun getObjTypes(): List<String> = items.map { it.type }.distinct().sorted()

    /** 某类型的物品，按基础价格、名称排序 */
    private fun itemsOf(type: String): List<PriceItem> =
        items.filter { it.type == type }.sortedWith(objComparator)

    fun getObjBySellPrice(type: String, price: String, sucker: Boolean): List<PriceItem> {
        if (price.isEmpty())
            return emptyList()
        val skpPrice = price.toIntOrNull() ?: return emptyList()
        return itemsOf(type).filter { obj ->
            calcSellPrice(obj.cost, false, sucker) == skpPrice ||
                    calcSellPrice(obj.cost, true, sucker) == skpPrice
        }
    }

    /** 价格区间（"2-5"），逐个价格查询后合并，按名称+基础价格去重，按基础价格排序 */
    fun getObjBySellPrice(type: String, prices: List<String>, sucker: Boolean): List<PriceItem> =
        prices.flatMap { getObjBySellPrice(type, it, sucker) }
            .distinctBy { it.name to it.cost }
            .sortedWith(objComparator)

    fun getObjByBuyPrice(
        type: String, price: String, charisma: String, sucker: Boolean
    ): List<PriceItem> {
        if (charisma.isEmpty() || price.isEmpty())
            return emptyList()
        val ch = charisma.toIntOrNull() ?: return emptyList()
        val skpPrice = price.toIntOrNull() ?: return emptyList()
        return itemsOf(type).filter { obj ->
            calcBuyPrice(obj.cost, ch, false, sucker) == skpPrice ||
                    calcBuyPrice(obj.cost, ch, true, sucker) == skpPrice
        }
    }

    /** 价格区间（"2-5"），逐个价格查询后合并，按名称+基础价格去重，按基础价格排序 */
    fun getObjByBuyPrice(
        type: String, prices: List<String>, charisma: String, sucker: Boolean
    ): List<PriceItem> =
        prices.flatMap { getObjByBuyPrice(type, it, charisma, sucker) }
            .distinctBy { it.name to it.cost }
            .sortedWith(objComparator)

    fun getObjByBasePrice(type: String, basePrice: String): List<PriceItem> =
        if (basePrice.isEmpty())
            itemsOf(type)
        else
            itemsOf(type).filter { it.cost.toString() == basePrice }

    /** 从物品描述（如 "a smoky potion"）解析所属类型，解析不出返回空串 */
    fun parseObjType(objDesc: String): String {
        GameData.priceRules.objectTypesRegex.forEach { (type, regex) ->
            if (Pattern.compile(regex).matcher(objDesc).find())
                return type
        }
        return ""
    }

    /**
     * 解析交易信息，返回 tradePrice / tradeMode / objType。
     * 支持的形式：
     *   you see here a smoky potion (for sale, 67 zorkmids).
     *   a smoky potion (unpaid, 67 zorkmids)
     *   Tipor offers 2 gold pieces for you lembas water. Sell it?
     */
    fun parseTradeInfo(info: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        GameData.priceRules.tradeTypesRegex.forEach { (tradeType, regex) ->
            val matcher = Pattern.compile(regex).matcher(info)
            while (matcher.find()) {
                val objDesc = matcher.group("objDesc") ?: ""
                result["tradePrice"] = matcher.group("objPrice") ?: ""
                result["tradeMode"] = tradeType
                val objType = parseObjType(objDesc)
                if (objType.isNotEmpty())
                    result["objType"] = objType
            }
        }
        return result
    }

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

    companion object {
        /** 物品列表顺序：先基础价格，再名称 */
        private val objComparator = compareBy<PriceItem>({ it.cost }, { it.name })
    }
}
