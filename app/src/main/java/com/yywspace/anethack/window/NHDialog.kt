package com.yywspace.anethack.window

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.yywspace.anethack.R

/** 系统弹窗：负责创建标题/内容/按钮，并交给容器排版。 */
class NHDialog(
    context: Context,
    private val content: View,
    private val title: CharSequence? = null,
    private val positiveText: CharSequence? = null,
    private val onPositive: (() -> Unit)? = null,
    private val negativeText: CharSequence? = null,
    private val onNegative: (() -> Unit)? = null,
    private val neutralText: CharSequence? = null,
    private val onNeutral: (() -> Unit)? = null,
    private val immersive: Boolean = false
) : Dialog(context) {

    init {
        setCanceledOnTouchOutside(true)
        if (immersive) {
            setOnShowListener { hideSystemBars() }
        }
    }

    override fun show() {
        if (immersive) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
        }
        super.show()
    }

    private fun hideSystemBars() {
        window?.apply {
            val controller = WindowCompat.getInsetsController(this, decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val horizontalMargin = context.resources.getDimensionPixelSize(R.dimen.dialog_horizontal_margin)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val contentView = content
        val contentPadding = context.resources.getDimensionPixelSize(R.dimen.dialog_content_padding)
        contentView.setPadding(contentPadding, 0, contentPadding, 0)

        val container = MaxHeightLinearLayout(context, contentView)

        title?.let {
            container.addView(TextView(context).apply {
                text = it
                setTextColor(ContextCompat.getColor(context, R.color.app_text))
                setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6
                )
                val h = context.resources.getDimensionPixelSize(R.dimen.dialog_title_padding_horizontal)
                val v = context.resources.getDimensionPixelSize(R.dimen.dialog_title_padding_vertical)
                setPadding(h, v, h, v)
            })
        }

        container.addView(contentView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        if (positiveText != null || negativeText != null || neutralText != null) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
                setPadding(0, dp(4), dp(12), dp(4))
            }
            fun addButton(text: CharSequence?, action: (() -> Unit)?) {
                if (text == null) return
                row.addView(MaterialButton(
                    context, null, androidx.appcompat.R.attr.buttonBarButtonStyle
                ).apply {
                    this.text = text
                    setOnClickListener {
                        action?.invoke()
                        dismiss()
                    }
                })
            }
            addButton(neutralText, onNeutral)
            addButton(negativeText, onNegative)
            addButton(positiveText, onPositive)
            container.addView(row)
        }

        setContentView(container)
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(
                context.resources.displayMetrics.widthPixels - horizontalMargin * 2,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /** 只负责容器和测量，不创建标题/按钮。 */
    private class MaxHeightLinearLayout(
        context: Context,
        private val contentView: View
    ) : LinearLayout(context) {

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

        private val maxHeight: Int
            get() {
                val available = resources.displayMetrics.heightPixels
                val margin = resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin)
                return minOf(
                    (available * MAX_HEIGHT_RATIO).toInt(),
                    available - margin * 2
                )
            }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val childWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
            var used = paddingTop + paddingBottom

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child === contentView) continue
                child.measure(
                    MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(
                        (maxHeight - used).coerceAtLeast(0),
                        MeasureSpec.AT_MOST
                    )
                )
                used += child.measuredHeight
            }

            val remaining = (maxHeight - used).coerceAtLeast(0)
            contentView.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(remaining, MeasureSpec.AT_MOST)
            )
            used += contentView.measuredHeight
            setMeasuredDimension(width, used.coerceAtMost(maxHeight))
        }
    }

    companion object {
        private const val MAX_HEIGHT_RATIO = 0.8f
    }
}
