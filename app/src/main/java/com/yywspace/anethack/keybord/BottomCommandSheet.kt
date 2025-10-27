package com.yywspace.anethack.keybord

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.GridLayout
import android.widget.TextView
import com.yywspace.anethack.Utils
import kotlin.math.abs

class BottomCommandSheet: GridLayout {
    private var initialY = 0f
    private val peekHeight: Int = Utils.dip2px(context,40f)
    private var currentHeight = peekHeight
    private var viewHeight: Int = 0
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var isDragging = false
    private var dragDir:DragDir = DragDir.UP
    private val commandList = mutableListOf<MutableList<Command>>()
    var onCommandPress:((String)->Unit)? = null

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    private fun refreshCommandList(commands:String) {
        commandList.clear()
        commands.split("\n").forEach { row ->
            val cmdRowList = mutableListOf<Command>()
            row.split(" ").forEach { cmdStr ->
                val cmdArray = cmdStr.split("|")
                val cmd = Command(cmdArray[0], cmdArray[0])
                if(cmdArray.size == 2)
                    cmd.label = cmdArray[1]
                cmdRowList.add(cmd)
            }
            commandList.add(cmdRowList)
        }
    }

    private fun getItemBackground(): Drawable? {
        val attrValue = android.R.attr.selectableItemBackground
        val typedValue = TypedValue()
        context.theme
            .resolveAttribute(attrValue, typedValue, true)
        val typedArray =
            context.theme.obtainStyledAttributes(typedValue.resourceId, intArrayOf(attrValue))
        return typedArray.getDrawable(0)
    }

    private fun initCommandView(command: Command): View {
        val textView = TextView(context)
            .apply {
                setTextColor(Color.BLACK)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                gravity = Gravity.CENTER
                background = getItemBackground()
                text = command.label
                setOnClickListener {
                    onCommandPress?.invoke(command.cmd)
                }
            }
        return textView
    }

    fun initBottomCommandSheet(commandPanel:String) {
        refreshCommandList(commandPanel)
        removeAllViews()
        commandList.forEachIndexed { i, commands ->
            commands.forEachIndexed { j, command ->
                val params = LayoutParams(
                    spec(i, 1,0f), spec(j, 1, 1f)
                ).apply {
                    width = 0
                    height = peekHeight
                }
                val view = initCommandView(command)
                addView(view, params)
            }
        }
        // 获取真实高度
        measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        viewHeight = measuredHeight
        currentHeight = peekHeight
        layoutParams.height = peekHeight
        requestLayout()
    }


    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialY = event.x
                initialY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                // 拦截滑动事件
                val dy= event.y - initialY
                if (abs(dy) > touchSlop) {
                    isDragging = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                // 滑动处理
                val dy = event.y - initialY
                dragDir = if (dy < 0f) DragDir.UP else DragDir.DOWN
                currentHeight = (currentHeight - dy.toInt()).coerceIn(peekHeight, viewHeight)
                layoutParams.height = currentHeight
                requestLayout()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    if (dragDir == DragDir.UP) {
                        animateHeightChange(viewHeight)
                    } else {
                        animateHeightChange(peekHeight)
                    }
                    isDragging = false
                }
            }
        }
        return true
    }

    private fun animateHeightChange(targetHeight: Int) {
        val valueAnimator = ValueAnimator.ofInt(height, targetHeight)
        // 根据剩余路径计算动画时间
        valueAnimator.duration = (
                200 * abs(currentHeight - targetHeight) / abs(viewHeight - peekHeight)
                ).toLong()
        // 更新最新高度值
        currentHeight = targetHeight
        valueAnimator.addUpdateListener { animator ->
            layoutParams.height = animator.animatedValue as Int
            requestLayout()
        }
        valueAnimator.start()
    }

    enum class DragDir {
        UP,
        DOWN
    }
    private data class Command(var label:String, var cmd:String)
}