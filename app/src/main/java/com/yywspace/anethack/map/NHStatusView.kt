package com.yywspace.anethack.map

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.Utils
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.entity.NHStatus.StatusField

/**
 * 状态栏：每行用若干个 TextView 拼出来，字段值变化时只更新涉及的 TextView。
 * 比原来的 SurfaceView 简单（不需要绘制线程/锁/手动高度），并且天然支持后续加点击交互。
 */
class NHStatusView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var nh: NetHack
    private lateinit var status: NHStatus
    private var statusInit = false

    /** 字段 → 对应的 TextView（结构固定，只在 init 时创建一次） */
    private val fieldViews = HashMap<StatusField, TextView>()
    /** 字段值签名：文本 + 颜色，没变就跳过 setText */
    private val valueCache = HashMap<StatusField, String>()
    private val refresher = RefreshCoalescer { render() }

    private val rows = listOf(
        listOf(StatusField.BL_TITLE, StatusField.BL_ALIGN),
        listOf(
            StatusField.BL_HP, StatusField.BL_STR, StatusField.BL_DX, StatusField.BL_CO,
            StatusField.BL_IN, StatusField.BL_WI, StatusField.BL_CH
        ),
        listOf(
            StatusField.BL_ENE, StatusField.BL_GOLD, StatusField.BL_AC,
            StatusField.BL_XP, StatusField.BL_TIME
        ),
        listOf(
            StatusField.BL_LEVELDESC, StatusField.BL_HUNGER, StatusField.BL_CAP,
            StatusField.BL_CONDITION
        )
    )

    init {
        orientation = VERTICAL
        val gap = Utils.dip2px(context, 5f)
        rows.forEach { rowFields ->
            val rowLayout = LinearLayout(context).apply { orientation = HORIZONTAL }
            addView(rowLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            rowFields.forEach { field ->
                val textView = TextView(context).apply {
                    // 字号来自 dimens（px，与原来 TextPaint 的 42px 一致）
                    setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        resources.getDimension(R.dimen.status_text_size)
                    )
                    isSingleLine = true
                    includeFontPadding = false
                    setLineSpacing(0f, 1f)
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                        .apply { marginEnd = gap }
                }
                rowLayout.addView(textView)
                fieldViews[field] = textView
            }
        }
    }

    fun initStatus(nh: NetHack, nhStatus: NHStatus) {
        this.nh = nh
        this.status = nhStatus
        statusInit = true
        invalidateFields()
    }

    /** 状态有变化：切到主线程并合并，同一轮消息循环内最多刷新一次 */
    fun requestRedraw() = refresher.request()

    private fun invalidateFields() {
        valueCache.clear()
        requestRedraw()
    }

    private fun render() {
        if (!statusInit) return
        rows.forEach { rowFields ->
            rowFields.forEach { field ->
                val textView = fieldViews[field] ?: return@forEach
                val spannable = getStatus(field).second
                if (spannable.isEmpty()) {
                    textView.visibility = GONE
                    return@forEach
                }
                textView.visibility = VISIBLE
                val signature = valueSignature(field, spannable)
                if (valueCache[field] == signature)
                    return@forEach
                valueCache[field] = signature
                textView.text = spannable
            }
        }
    }

    /** 字段内容（和原来 SurfaceView 版本保持一致） */
    private fun getStatus(field: StatusField): Pair<StatusField, Spannable> {
        return when (field) {
            StatusField.BL_HP -> {
                val hp = status.hitPoints.toSpannableString()
                val hpMax = status.maxHitPoints.toSpannableString()
                Pair(field, SpannableStringBuilder(hp).append(hpMax))
            }
            StatusField.BL_ENE -> {
                val pw = status.power.toSpannableString()
                val pwMax = status.maxPower.toSpannableString()
                Pair(field, SpannableStringBuilder(pw).append(pwMax))
            }
            StatusField.BL_XP -> {
                val xp = status.expLevel.toSpannableString()
                val exp = status.expPoints.toSpannableString()
                val hd = status.hitDice.toSpannableString()
                if (xp.isEmpty())
                    Pair(field, hd)
                else
                    Pair(field, SpannableStringBuilder(xp).append(exp))
            }
            StatusField.BL_CONDITION ->
                Pair(field, status.getConditionSpannable())
            StatusField.BL_TITLE ->
                // 标题同时作为 HP 条：按当前血量百分比给前段字符上色
                Pair(field, buildTitleWithHpBar(status.getField(field).toSpannableString()))
            else -> Pair(field, status.getField(field).toSpannableString())
        }
    }

    /** HP 条：前 percent% 的字符用血量颜色，剩余部分用浅灰 */
    private fun buildTitleWithHpBar(titleSpannable: Spannable): Spannable {
        val hp = status.hitPoints
        val title = SpannableStringBuilder(titleSpannable)
        val percent = hp.percent
        val barEnd = title.length * percent / 100
        title.setSpan(
            BackgroundColorSpan(hp.color), 0, barEnd,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )
        title.setSpan(
            BackgroundColorSpan(Color.argb(200, 220, 220, 220)), barEnd, title.length,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )
        return title
    }

    /** 值签名：文本 + 颜色；文本相同但颜色变化时也要更新 */
    private fun valueSignature(field: StatusField, spannable: Spannable): String {
        val fg = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
            .joinToString(",") { it.foregroundColor.toString() }
        val bg = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)
            .joinToString(",") { it.backgroundColor.toString() }
        return if (field == StatusField.BL_TITLE)
            spannable.toString() + "|" + status.hitPoints.percent + "|$fg|$bg"
        else
            "$spannable|$fg|$bg"
    }
}
