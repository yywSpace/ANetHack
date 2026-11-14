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
 * 只负责价格段的 span 和触摸判定，菜单项本身仍由 NHWMenuAdapter 点击。
 */
object MenuLink {

    /** 给标题末尾的 [priceQuote]（如 "{buy 267 sell 67}"）里的价格段加链接。 */
    fun highlight(context: Context, text: Spannable, priceQuote: String) {
        if (priceQuote.isEmpty())
            return
        val quoteStart = text.length - priceQuote.length
        if (quoteStart < 0)
            return
        val content = priceQuote.removeSurrounding("{", "}")
        val contentStart = quoteStart + 1 // 跳过 "{"
        val regex = priceRegex(context)
        regex.findAll(content).forEach { match ->
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

    /** 接管标题 TextView 的价格点击；按在普通文字上时返回 false，让菜单项正常点击。 */
    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: TextView, onClick: (String) -> Unit) {
        var downQuote: MenuQuoteSpan? = null
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downQuote = spanAt(v, event)
                    downQuote != null
                }
                MotionEvent.ACTION_UP -> {
                    val quote = downQuote
                    downQuote = null
                    if (quote != null) {
                        onClick(quote.quote)
                        true
                    } else false
                }
                else -> downQuote != null
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
 *
 * 注意：真正触发价格鉴定的不是这里的 [onClick]，而是 [MenuLink.attach] 安装的 OnTouchListener。
 * 原因是菜单项外层还有整行点击、长按、多选，必须在 ACTION_DOWN 阶段判断是否按在价格上，
 * 并在按中价格时消费事件，避免菜单项同时被选中。
 */
class MenuQuoteSpan(val quote: String) : ClickableSpan() {

    /**
     * 这里故意不执行动作。
     *
     * [MenuLink.attach] 的触摸监听会在 ACTION_UP 时读取 [quote] 并回调；
     * 如果这里也处理，会和菜单项自身点击重复，甚至一次点击打开两次弹窗。
     */
    override fun onClick(widget: View) {
        // no-op：交给 MenuLink.attach
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds) // 标准链接样式：链接色 + 下划线
    }
}
