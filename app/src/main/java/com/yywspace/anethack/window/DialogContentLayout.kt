package com.yywspace.anethack.window

import android.content.Context
import android.util.AttributeSet
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.R

/**
 * 限制最大高度的纵向容器。
 *
 * 先测量固定内容，把剩余高度留给 ScrollView/RecyclerView；
 * 每次 onMeasure 重新读取屏幕高度，横竖屏切换不会残留旧尺寸。
 */
class DialogContentLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 可伸缩的内容；未设置时自动找 ScrollView/RecyclerView。 */
    var flexibleChild: View? = null

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val typed = context.obtainStyledAttributes(
            intArrayOf(android.R.attr.dialogCornerRadius)
        )
        val radius = typed.getDimension(0, 4f * density)
        typed.recycle()
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(ContextCompat.getColor(context, R.color.app_background))
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val screen = resources.displayMetrics.heightPixels
        val margin = resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin)
        val maxHeight = minOf(
            (screen * MAX_HEIGHT_RATIO).toInt(),
            screen - margin * 2
        )
        val paddingX = paddingLeft + paddingRight
        val childWidth = (width - paddingX).coerceAtLeast(0)

        val flexible = flexibleChild ?: (0 until childCount)
            .map { getChildAt(it) }
            .firstOrNull { it is ScrollView || it is RecyclerView }

        var used = paddingTop + paddingBottom
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child === flexible) continue
            val lp = child.layoutParams as MarginLayoutParams
            child.measure(
                getChildMeasureSpec(
                    widthMeasureSpec,
                    paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin,
                    lp.width
                ),
                MeasureSpec.makeMeasureSpec(
                    (maxHeight - used).coerceAtLeast(0),
                    MeasureSpec.AT_MOST
                )
            )
            used += child.measuredHeight + lp.topMargin + lp.bottomMargin
        }

        if (flexible != null) {
            val lp = flexible.layoutParams as MarginLayoutParams
            flexible.measure(
                MeasureSpec.makeMeasureSpec(
                    (childWidth - lp.leftMargin - lp.rightMargin).coerceAtLeast(0),
                    MeasureSpec.EXACTLY
                ),
                MeasureSpec.makeMeasureSpec(
                    (maxHeight - used).coerceAtLeast(0),
                    MeasureSpec.AT_MOST
                )
            )
            used += flexible.measuredHeight + lp.topMargin + lp.bottomMargin
        }

        setMeasuredDimension(width, used.coerceAtMost(maxHeight))
    }

    companion object {
        private const val MAX_HEIGHT_RATIO = 0.850f
    }
}
