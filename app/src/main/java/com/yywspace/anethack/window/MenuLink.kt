package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.yywspace.anethack.R

/**
 * 菜单标题里的价格段链接：给 "buy 267" / "sell 67" 加下划线，并处理点击。
 *
 * 物品信息不再在这里加行内图标，统一由菜单长按操作处理。
 */
object MenuLink {

    /** 给标题末尾的 [priceQuote]（如 "{buy 267 sell 67}"）里的价格段加链接。 */
    fun highlightPriceQuote(context: Context, text: Spannable, priceQuote: String) {
        if (priceQuote.isEmpty())
            return
        val quoteStart = text.length - priceQuote.length
        if (quoteStart < 0)
            return
        val content = priceQuote.removeSurrounding("{", "}")
        val contentStart = quoteStart + 1 // 跳过 "{"
        priceRegex(context).findAll(content).forEach { match ->
            val start = contentStart + match.range.first
            val end = contentStart + match.range.last + 1
            text.setSpan(
                MenuQuoteSpan(match.value),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** 从价格段中取出本地化后的价格文本，如 ["buy 267", "sell 67"]。 */
    fun priceQuotes(context: Context, priceQuote: String): List<String> {
        if (priceQuote.isEmpty())
            return emptyList()
        return priceRegex(context)
            .findAll(priceQuote.removeSurrounding("{", "}"))
            .map { it.value }
            .toList()
    }

    /** 接管标题 TextView 的价格段点击；普通文字返回 false。 */
    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: TextView, onQuoteClick: (String) -> Unit) {
        var downSpan: MenuQuoteSpan? = null
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downSpan = spanAt(v, event)
                    downSpan != null
                }
                MotionEvent.ACTION_UP -> {
                    val span = downSpan
                    downSpan = null
                    if (span != null) {
                        onQuoteClick(span.quote)
                        true
                    } else false
                }
                else -> downSpan != null
            }
        }
    }

    private fun spanAt(view: View, event: MotionEvent): MenuQuoteSpan? {
        val textView = view as? TextView ?: return null
        val offset = textView.getOffsetForPosition(event.x, event.y)
        val text = textView.text as? Spanned ?: return null
        return text.getSpans(offset, offset, MenuQuoteSpan::class.java).firstOrNull()
    }

    private fun priceRegex(context: Context): Regex {
        val buy = Regex.escape(context.getString(R.string.price_quote_buy))
        val sell = Regex.escape(context.getString(R.string.price_quote_sell))
        return Regex("$buy\\s*[0-9]+(?:-[0-9]+)?|$sell\\s*[0-9]+(?:-[0-9]+)?")
    }
}

/**
 * 价格段 span。
 *
 * 继承 [ClickableSpan] 主要是为了复用标准链接样式（链接色 + 下划线），
 * 同时让 [MenuLink.spanAt] 能通过具体类型区分“价格段”和菜单里其它普通下划线文字。
 */
class MenuQuoteSpan(val quote: String) : ClickableSpan() {

    /** 点击由 [MenuLink.attach] 的触摸监听统一处理。 */
    override fun onClick(widget: View) {
        // no-op
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds) // 链接色 + 下划线
    }
}