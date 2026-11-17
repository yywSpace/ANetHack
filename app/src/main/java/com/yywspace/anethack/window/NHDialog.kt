package com.yywspace.anethack.window

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.button.MaterialButton
import com.yywspace.anethack.R

class NHDialog(
    context: Context,
    private val title: CharSequence?,
    private val content: View,
    private val positiveText: CharSequence?,
    private val onPositive: (() -> Unit)? = null
) : Dialog(context) {

    init {
        setCanceledOnTouchOutside(true)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val verticalMargin = context.resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin)
        val horizontalMargin = context.resources.getDimensionPixelSize(R.dimen.dialog_horizontal_margin)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val cornerTyped = context.obtainStyledAttributes(
                intArrayOf(android.R.attr.dialogCornerRadius)
            )
            val radiusPx = cornerTyped.getDimension(0, dp(4).toFloat())
            cornerTyped.recycle()
            background = GradientDrawable().apply {
                cornerRadius = radiusPx
                setColor(ContextCompat.getColor(context, R.color.app_background))
            }
        }

        title?.let {
            root.addView(TextView(context).apply {
                text = it
                setTextColor(ContextCompat.getColor(context, R.color.app_text))
                setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6
                )
                val titleHorizontal = context.resources.getDimensionPixelSize(
                    R.dimen.dialog_title_padding_horizontal
                )
                val titleVertical = context.resources.getDimensionPixelSize(
                    R.dimen.dialog_title_padding_vertical
                )
                setPadding(titleHorizontal, titleVertical, titleHorizontal, titleVertical)
            })
        }

        val maxHeight = minOf(
            (context.resources.displayMetrics.heightPixels * MAX_HEIGHT_RATIO).toInt(),
            context.resources.displayMetrics.heightPixels - verticalMargin * 2
        )
        val contentPadding = context.resources.getDimensionPixelSize(R.dimen.dialog_content_padding)
        val wrapper = MaxHeightFrameLayout(context, maxHeight).apply {
            setPadding(contentPadding, 0, contentPadding, 0)
            addView(
                content,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(
            wrapper,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        positiveText?.let {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
                setPadding(0, dp(4), dp(12), dp(4))
            }
            row.addView(
                MaterialButton(
                    context, null, androidx.appcompat.R.attr.buttonBarButtonStyle
                ).apply {
                    text = it
                    setOnClickListener {
                        onPositive?.invoke()
                        dismiss()
                    }
                })
            root.addView(row)
        }

        setContentView(root)
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(
                context.resources.displayMetrics.widthPixels - horizontalMargin * 2,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /** 短内容自适应，长内容最多 maxHeightPx。 */
    private class MaxHeightFrameLayout(
        context: Context,
        private val maxHeightPx: Int
    ) : android.widget.FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val bounded = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, bounded)
        }
    }

    companion object {
        private const val MAX_HEIGHT_RATIO = 0.7f
    }
}
