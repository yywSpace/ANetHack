package com.yywspace.anethack.map

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.R
import com.yywspace.anethack.entity.NHMessage
import com.yywspace.anethack.gamedata.ItemMatch
import com.yywspace.anethack.gamedata.MonsterMatch
import com.yywspace.anethack.window.NHWMessage

/**
 * 消息区：一个 RecyclerView 同时负责收起和展开。
 * 收起时高度是三行，展开时展开到最多 25% 屏幕。
 */
class NHMessageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private lateinit var nhMessage: NHWMessage
    private var messageInit = false
    var isExpanded = false
        private set

    private var expansionAnimator: android.animation.ValueAnimator? = null
    private var lastHeight = 0
    private var lastAdapterCount = 0
    private var lastLatestBatchStart = -1
    private var lastLayoutHeight = 0
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            toggleExpanded()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onMessageLongClick?.invoke()
        }
    })


    private val itemTouchListener = object : SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            // item 区域交给 item 处理 span 点击和普通点击
            if (rv.findChildViewUnder(e.x, e.y) != null) return false
            gestureDetector.onTouchEvent(e)
            return false
        }
    }

    var onMessageLongClick: (() -> Unit)? = null
    var onMonsterClick: ((MonsterMatch) -> Unit)? = null
    var onItemClick: ((ItemMatch) -> Unit)? = null

    init {
        layoutManager = LinearLayoutManager(context)
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = true
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(resources.getDimensionPixelSize(R.dimen.message_text_size))
        isScrollbarFadingEnabled = true
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        scrollBarDefaultDelayBeforeFade = 300
        scrollBarFadeDuration = 200
        addOnItemTouchListener(itemTouchListener)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            if (!isExpanded) setHeight(collapsedHeight())
        }
    }

    fun initMessage(message: NHWMessage) {
        this.nhMessage = message
        messageInit = true
        post { refresh() }
    }

    fun onNewMessage() {
        if (!messageInit) return
        val collapse = isExpanded
        post {
            refresh()
            if (collapse && isExpanded) collapse()
        }
    }

    fun toggleExpanded() {
        if (isExpanded) collapse() else expand()
    }

    fun expand() {
        if (isExpanded) return
        isExpanded = true
        isScrollbarFadingEnabled = false
        invalidate()
        refresh()
        animateHeight(expandedHeight())
    }

    fun collapse() {
        if (!isExpanded) return
        animateHeight(collapsedHeight()) {
            isExpanded = false
            isScrollbarFadingEnabled = true
            invalidate()
            scrollToBottom()
        }
    }

    override fun getBottomFadingEdgeStrength(): Float = 0f

    /**
     * 收回动画期间，RecyclerView 的高度不断变小。
     * 高度变小后内容本身不会自动上移，所以这里按“上一帧高度 - 当前高度”
     * 主动滚动相同的距离，让文字跟着抽屉一起向上收回。
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (lastLayoutHeight > height) {
            scrollBy(0, lastLayoutHeight - height)
        }
        lastLayoutHeight = height
    }

    /** 最新一批消息在列表中的起始下标。 */
    private fun latestBatchStart(list: List<NHMessage> = nhMessage.messageList): Int {
        val last = list.lastOrNull { !it.history } ?: return -1
        var i = list.lastIndex
        while (i >= 0 && list[i].source === last.source) i--
        return i + 1
    }

    private fun refresh() {
        if (!messageInit) return
        val messages = nhMessage.messageList.toList()
        val oldCount = lastAdapterCount
        val messageAdapter = adapter as? NHWMessage.NHWMessageAdapter
        if (messageAdapter == null) {
            adapter = nhMessage.createHistoryAdapter()
            lastAdapterCount = adapter?.itemCount ?: 0
            lastLatestBatchStart = latestBatchStart(messages)
        } else {
            messageAdapter.updateMessages(messages)
            val count = messages.size
            when {
                count > oldCount -> {
                    messageAdapter.notifyItemRangeInserted(oldCount, count - oldCount)
                    val start = latestBatchStart(messages)
                    if (lastLatestBatchStart in 0..<start) {
                        // 只刷新上一批绿色消息，让它们恢复普通颜色
                        messageAdapter.notifyItemRangeChanged(
                            lastLatestBatchStart,
                            start - lastLatestBatchStart
                        )
                    }
                    lastLatestBatchStart = start
                }
                count < oldCount -> {
                    messageAdapter.notifyItemRangeRemoved(count, oldCount - count)
                    lastLatestBatchStart = latestBatchStart(messages)
                }
                else -> {
                    // 达到 500 条上限后是“删头 + 追尾”，数量不变但位置整体移动
                    messageAdapter.notifyItemRangeChanged(0, count)
                    lastLatestBatchStart = latestBatchStart(messages)
                }
            }
            lastAdapterCount = count
        }
        scrollToBottom()
    }

    /** 滚到内容最底部，保证最新消息完整可见。 */
    private fun scrollToBottom() {
        val last = (adapter?.itemCount ?: 0) - 1
        if (last < 0) return
        scrollToPosition(last)
        post {
            val manager = layoutManager as? LinearLayoutManager ?: return@post
            val child = manager.findViewByPosition(last) ?: return@post
            val offset = child.bottom - height + paddingBottom
            if (offset > 0) scrollBy(0, offset)
        }
    }

    private fun collapsedHeight(): Int {
        val lineHeight = resources.getDimensionPixelSize(R.dimen.message_text_size)
        return lineHeight * 3 + paddingTop + paddingBottom +
            resources.getDimensionPixelSize(R.dimen.message_text_size)
    }

    private fun expandedHeight(): Int =
        (resources.displayMetrics.heightPixels * 0.25f).toInt()

    private fun setHeight(value: Int) {
        val target = value.coerceAtLeast(collapsedHeight())
        if (layoutParams.height != target) {
            layoutParams.height = target
            requestLayout()
        }
        lastHeight = target
    }

    private fun animateHeight(target: Int, onEnd: (() -> Unit)? = null) {
        expansionAnimator?.cancel()
        val start = lastHeight.takeIf { it > 0 } ?: collapsedHeight()
        expansionAnimator = android.animation.ValueAnimator.ofInt(start, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val h = it.animatedValue as Int
                setHeight(h)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

}
