package com.yywspace.anethack.map

import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.window.NHWMessage

/**
 * 消息区：最近几条消息拼成一个带颜色的 Spannable 显示。
 * 刷新按帧合并，麻痹连续跑回合时也只会每帧更新一次。
 */
class NHMessageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private lateinit var nh: NetHack
    private lateinit var nhMessage: NHWMessage
    private var messageInit = false
    private var messageSize = 3
    private val refresher = RefreshCoalescer { render() }

    /** 点击消息区的回调（由 NHWMessage 设置，避免在游戏线程里直接操作 View） */
    var onMessageClick: (() -> Unit)? = null

    init {
        // 字号来自 dimens（px，与原来 TextPaint 的 42px 一致）
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.message_text_size))
        includeFontPadding = false
        setLineSpacing(0f, 1f)
        setOnClickListener { onMessageClick?.invoke() }
    }

    fun initMessage(nh: NetHack, message: NHWMessage) {
        this.nh = nh
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
            builder.append(message.toSpannableString())
        }
        val content = builder.toString()
        // 内容没变就不重新布局
        if (content == text)
            return
        text = builder
    }
}
