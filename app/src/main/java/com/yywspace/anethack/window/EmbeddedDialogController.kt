package com.yywspace.anethack.window

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.yywspace.anethack.R

/**
 * 统一管理 Activity 内嵌对话框容器。
 *
 * 所有内嵌窗口只通过这里显示/关闭，业务代码不再直接操作容器。
 * 支持简单的对话框栈：后显示的覆盖前一个，关闭后恢复前一个。
 */
class EmbeddedDialogController(
    private val container: FrameLayout
) {
    data class Options(
        /** true：外部区域可以点击，事件会继续传给底层界面 */
        val outsideTouchable: Boolean = false,
        /** true：显示 dialog_scrim 遮罩 */
        val dim: Boolean = true
    )

    private data class Entry(
        val tag: String,
        val view: View,
        val options: Options,
        val onDismiss: (() -> Unit)?
    )

    private val stack = ArrayDeque<Entry>()

    fun isShowing(tag: String): Boolean = stack.any { it.tag == tag }

    fun show(
        tag: String,
        view: View,
        options: Options = Options(),
        onDismiss: (() -> Unit)? = null
    ) {
        // 同一个 tag 不能重复入栈
        dismiss(tag)

        stack.lastOrNull()?.view?.visibility = View.GONE

        addView(view)
        stack.addLast(Entry(tag, view, options, onDismiss))
        applyOptions(options)
        container.visibility = View.VISIBLE
    }

    fun dismiss(tag: String) {
        val entry = stack.firstOrNull { it.tag == tag } ?: return
        stack.remove(entry)
        container.removeView(entry.view)
        entry.onDismiss?.invoke()

        val top = stack.lastOrNull()
        if (top == null) {
            container.removeAllViews()
            container.visibility = View.INVISIBLE
            applyOptions(Options())
        } else {
            top.view.visibility = View.VISIBLE
            applyOptions(top.options)
        }
    }

    fun clear() {
        while (stack.isNotEmpty()) {
            val entry = stack.removeLast()
            container.removeView(entry.view)
            entry.onDismiss?.invoke()
        }
        container.removeAllViews()
        container.visibility = View.INVISIBLE
        applyOptions(Options())
    }

    private fun addView(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        container.addView(view, params)
    }

    private fun applyOptions(options: Options) {
        container.isClickable = !options.outsideTouchable
        container.isFocusable = !options.outsideTouchable
        container.setBackgroundColor(
            if (options.dim)
                ContextCompat.getColor(container.context, R.color.dialog_scrim)
            else
                Color.TRANSPARENT
        )
    }
}
