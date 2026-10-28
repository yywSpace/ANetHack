package com.yywspace.anethack.map

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView
import com.yywspace.anethack.R
import com.yywspace.anethack.gamedata.MonsterLink
import com.yywspace.anethack.window.NHWMessage

/**
 * 消息区：最近几条消息拼成一个带颜色的 Spannable 显示。
 * 刷新按帧合并，麻痹连续跑回合时也只会每帧更新一次。
 *
 * 整行就是一个怪物名的那一行会高亮成可点击，点**文字**弹怪物详情；
 * 点其它任何位置（包括这一行的空白处）仍然是原来的行为（打开完整消息列表）。
 */
class NHMessageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private lateinit var nhMessage: NHWMessage
    private var messageInit = false
    private var messageSize = 3
    private val refresher = RefreshCoalescer { render() }

    /** 点击消息区的回调（由 NHWMessage 设置，避免在游戏线程里直接操作 View） */
    var onMessageClick: (() -> Unit)? = null

    /** 点击消息里怪物名的回调（由 NHWMessage 设置，参数是怪物名） */
    var onMonsterClick: ((String) -> Unit)? = null

    init {
        // 字号来自 dimens（px，与原来 TextPaint 的 42px 一致）
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.message_text_size))
        includeFontPadding = false
        setLineSpacing(0f, 1f)
        setOnClickListener { onMessageClick?.invoke() }
    }

    fun initMessage(message: NHWMessage) {
        this.nhMessage = message
        messageInit = true
        requestRedraw()
    }

    /** 新消息到达：切到主线程并合并，同一轮消息循环内最多刷新一次 */
    fun requestRedraw() = refresher.request()

    private fun render() {
        if (!messageInit)
            return
        val messages = nhMessage.getRecentMessageList(messageSize).reversed()
        val builder = SpannableStringBuilder()
        messages.forEachIndexed { index, message ->
            if (index > 0)
                builder.append('\n')
            val start = builder.length
            builder.append(message.toSpannableString())
            // 只解析 look 指令产生的消息（格式可预期），其它消息保持原样
            if (message.fromLook)
                MonsterLink.highlight(context, builder, start, builder.length) { name ->
                    onMonsterClick?.invoke(name)
                }
        }
        val content = builder.toString()
        // 内容没变就不重新布局
        if (content == text)
            return
        text = builder
    }

    /* 只有真正点在怪物名文字上才拦截，否则交给原来的整块点击。*/
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val span = MonsterLink.spanAtTouch(this, event)
            if (span != null) {
                span.onClick(this)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
