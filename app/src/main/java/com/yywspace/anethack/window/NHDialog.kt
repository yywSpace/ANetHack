package com.yywspace.anethack.window

import android.app.Dialog
import android.content.Context
import android.graphics.Color
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

data class NHDialogButton(
    val text: CharSequence,
    val dismissOnClick: Boolean = true,
    val onClick: (() -> Unit)? = null
)

/** 系统弹窗：负责创建标题/内容/按钮，并交给容器排版。 */
class NHDialog(
    context: Context,
    private val content: View,
    private val title: CharSequence? = null,
    private val leftButton: NHDialogButton? = null,
    private val middleButton: NHDialogButton? = null,
    private val rightButton: NHDialogButton? = null,
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

        val container = DialogContentLayout(context).apply {
            flexibleChild = contentView
        }

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

        if (leftButton != null || middleButton != null || rightButton != null) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            fun addButton(button: NHDialogButton?, gravity: Int) {
                val slot = LinearLayout(context).apply {
                    this.gravity = gravity
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                button?.let { btn ->
                    slot.addView(MaterialButton(
                        context, null, androidx.appcompat.R.attr.buttonBarButtonStyle
                    ).apply {
                        text = btn.text
                        setOnClickListener {
                            btn.onClick?.invoke()
                            if (btn.dismissOnClick) dismiss()
                        }
                    })
                }
                row.addView(slot)
            }
            addButton(leftButton, android.view.Gravity.START)
            addButton(middleButton, android.view.Gravity.CENTER)
            addButton(rightButton, android.view.Gravity.END)
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
}