package com.yywspace.anethack.gamedata

import com.yywspace.anethack.R

/** 价格鉴定的查询模式；业务逻辑只比较枚举，不比较界面文字。 */
enum class PriceMode(
    val labelRes: Int,
    val quoteRes: Int?,
    /** 内部解析键，只用于 TradeInfo.mode；界面文字用 quoteRes/labelRes。 */
    val quoteKey: String?
) {
    BASE(R.string.price_id_mode_base, null, null),
    BUY(R.string.price_id_mode_buy, R.string.price_quote_buy, "buy"),
    SELL(R.string.price_id_mode_sell, R.string.price_quote_sell, "sell");
}

/** 价格鉴定的附加条件。 */
enum class Surcharge(val labelRes: Int) {
    NONE(R.string.price_id_surcharge_none),
    TOURIST(R.string.price_id_surcharge_tourist),
    SHIRT(R.string.price_id_surcharge_shirt),
    CAP(R.string.price_id_surcharge_cap)
}

/** 打开弹窗前，从价格段或消息里解析出的预填值。 */
data class PricePrefill(
    val type: String? = null,
    val mode: PriceMode? = null,
    val price: String? = null
)

/** 一次价格鉴定的完整条件。 */
data class PriceQuery(
    val type: String,
    val mode: PriceMode,
    val surcharge: Surcharge,
    val price: String,
    val charisma: Int
)

/** 价格输入：单个价格或闭区间。 */
data class PriceRange(val from: Int, val to: Int) {
    companion object {
        fun parse(text: String): PriceRange? {
            val parts = text.trim().split('-').map { it.trim() }
            if (parts.size !in 1..2)
                return null
            val from = parts[0].toIntOrNull() ?: return null
            val to = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else from
            if (from < 0 || to < from)
                return null
            return PriceRange(from, to)
        }
    }
}
