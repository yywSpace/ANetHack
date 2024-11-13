package com.yywspace.anethack.keybord

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import com.yywspace.anethack.Utils
import kotlin.math.abs


class BottomCommandSheet:GridLayout {
    private var isExpanding = true
    private var peekHeight = Utils.dip2px(context, 40f)
    private var viewHeight = 0
    private val commandList = mutableListOf<MutableList<Command>>()
    var onCommandPress:((String)->Unit)? = null
    private var lastTouchX:Float = 0f
    private var lastTouchY:Float = 0f
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
        return typedArray.getDrawable(0);
    }

    private fun initCommandView(command: Command):View {
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
        // 默认不展开
        setViewHeight(peekHeight)
    }

    private fun animateHeight(animDuration: Long, startHeight:Int, endHeight:Int) {
        val heightAnimation = ValueAnimator.ofInt(startHeight, endHeight)
        heightAnimation.duration = animDuration / 2
        heightAnimation.startDelay = 0
        heightAnimation.addUpdateListener { animation ->
            setViewHeight(animation.animatedValue as Int)
        }
        heightAnimation.start()
    }

    private fun setViewHeight(height: Int) {
        layoutParams.height = height
        requestLayout()
    }

    // 拦截滑动事件
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dY= event.y-lastTouchY
                val dX= event.x-lastTouchX
                return abs(dY) >(abs(dX))
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                var offsetY = (event.y - lastTouchY).toInt()
                if (bottom - top - offsetY <= peekHeight)
                    offsetY = bottom - top - peekHeight
                if (bottom - top - offsetY >= viewHeight)
                    offsetY = bottom - top - viewHeight
                layout(left, top + offsetY, right, bottom)
                setViewHeight(height)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if(isExpanding) {
                    if (height - peekHeight >= viewHeight / 4f) {
                        isExpanding = false
                        animateHeight(190, height, viewHeight)
                    }
                    else
                        animateHeight(200, height, peekHeight)
                }else {
                    if (viewHeight - height >= viewHeight / 4f) {
                        isExpanding = true
                        animateHeight(190, height, peekHeight)
                    }
                    else
                        animateHeight(200, height, viewHeight)
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private data class Command(var label:String, var cmd:String)

}