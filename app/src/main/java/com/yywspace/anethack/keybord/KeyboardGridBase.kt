package com.yywspace.anethack.keybord

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.setMargins
import com.yywspace.anethack.R

/**
 * 键盘网格的公共基类：一个 [GridLayout] + 按键视图工厂 + 数字行开关。
 *
 * 标准布局和自定义布局各自继承它，只负责“建自己的结构、装自己的数据”。
 */
abstract class KeyboardGridBase(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 20 列 × 5 行的网格 */
    protected val grid: GridLayout = GridLayout(context).apply {
        columnCount = 20
        rowCount = 5
    }

    protected val keyHeight = resources.getDimensionPixelSize(R.dimen.command_item_height)
    private val keyGap = resources.getDimensionPixelSize(R.dimen.command_item_gap)

    /** 当前布局第 0 行（数字行）的视图 */
    protected val numberViews: MutableList<View> = mutableListOf()

    /** 数字行是否显示 */
    var numberShown: Boolean = false
        private set

    /** 特殊键：没有副标签，长按交给外层处理 */
    protected val specialKeyLabels = setOf(
        "Letter", "Shift", "Ctrl", "Meta", "Symbol", "Num", "Cust",
        "Enter", "ESC", "DEL", "Space"
    )

    var onKeyPress:((key:KeyboardLayout.Key)->Unit)? = null
    var onSpecialKeyLongPress:((key:KeyboardLayout.Key)->Unit)? = null
    var immersiveProvider:(() -> Boolean)? = null

    init {
        KeyboardVibrate.bind(context)
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** 数字行显示/隐藏 */
    protected fun setNumberPanelVisible(visible: Boolean) {
        numberViews.forEach { it.visibility = if (visible) VISIBLE else GONE }
    }

    /** 切换布局时把数字行状态传进来（不涉及点击） */
    fun setNumberShown(visible: Boolean) {
        numberShown = visible
        setNumberPanelVisible(visible)
    }

    /** Num 键：切换数字行并刷新状态点 */
    protected fun toggleNumberPanel(statusImage: ImageView?) {
        numberShown = !numberShown
        setNumberPanelVisible(numberShown)
        statusImage?.setImageResource(
            if (numberShown) R.drawable.dot_selected else R.drawable.dot_unselected
        )
    }

    /** 主题切换后重建视图 */
    abstract fun applyTheme()

    /** 一个键的公共创建：布局、背景、点击/长按分发 */
    protected fun createKeyView(
        keyProvider: () -> KeyboardLayout.Key,
        onLongClick: ((KeyboardLayout.Key) -> Unit)? = null
    ): View = inflate(context, R.layout.keyboard_key_view, null).apply {
        setBackgroundResource(R.drawable.bg_key_selector)
        val statusImage = findViewById<ImageView>(R.id.key_status)
        setOnClickListener {
            val key = keyProvider()
            if (key.command.isEmpty()) return@setOnClickListener
            KeyboardVibrate.vibrate()
            handleKeyClick(key, statusImage)
        }
        if (onLongClick != null) {
            setOnLongClickListener {
                onLongClick(keyProvider())
                true
            }
        }
    }

    /** 一个键的公共外观：状态点、主标签、副标签 */
    protected fun bindKeyView(view: View, label: String, command: String, subLabel: String?) {
        val statusImage = view.findViewById<ImageView>(R.id.key_status)
        statusImage.visibility = if (label in listOf("Num", "Shift")) VISIBLE else INVISIBLE
        // Num 键的状态点跟随数字行状态，切换布局重新绑定时自动刷新
        if (label == "Num") {
            statusImage.setImageResource(
                if (numberShown) R.drawable.dot_selected else R.drawable.dot_unselected
            )
        }
        view.findViewById<TextView>(R.id.key_main).apply {
            text = label
            tag = command
        }
        view.findViewById<TextView>(R.id.key_sub).apply {
            text = subLabel.orEmpty()
            visibility = if (subLabel.isNullOrEmpty()) GONE else VISIBLE
        }
    }

    /**
     * 用隐形占位把网格钉成 5 行 × 20 列。
     *
     * 键带 rowSpan / columnSpan 时，GridLayout 推导出的列宽/行高会不均匀；
     * 先放一层空占位，轨道就固定了。
     */
    private fun pinGridTracks(): MutableList<MutableList<View>> {
        val placeholderRows = mutableListOf<MutableList<View>>()
        for (i in 0 until grid.rowCount) {
            val rowView = mutableListOf<View>()
            for (j in 0 until grid.columnCount) {
                if (i == grid.rowCount - 1 || j == 0) {
                    val placeholder = View(context)
                    val params = GridLayout.LayoutParams(
                        GridLayout.spec(i),
                        GridLayout.spec(j, 1f)
                    ).apply {
                        setMargins(keyGap)
                        // 占位只需要占住轨道，本身不能撑大格子
                        width = 0
                        height = 0
                    }
                    grid.addView(placeholder, params)
                    rowView.add(placeholder)
                }
            }
            placeholderRows.add(rowView)
        }
        return placeholderRows
    }

    /** 清空网格并重新钉好 5×20 轨道，返回占位视图（数字行要用） */
    protected fun resetGrid(): MutableList<MutableList<View>> {
        grid.removeAllViews()
        numberViews.clear()
        return pinGridTracks()
    }

    /** 点击一个键：子类按自己的布局处理，默认把命令交给调用方 */
    protected open fun handleKeyClick(key: KeyboardLayout.Key, statusImage: ImageView?) {
        onKeyPress?.invoke(key)
    }

    protected fun gridParams(cell: KeyboardLayout.Key, height: Int): GridLayout.LayoutParams =
        GridLayout.LayoutParams(
            GridLayout.spec(cell.row, cell.rowSpan),
            GridLayout.spec(cell.column, cell.columnSpan, cell.columnWeight.toFloat())
        ).apply {
            setMargins(keyGap)
            width = 0
            this.height = height
        }
}
