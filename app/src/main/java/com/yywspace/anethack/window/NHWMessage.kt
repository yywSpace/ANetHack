package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.entity.NHMessage
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.extensions.show
import com.yywspace.anethack.map.NHMessageView
import com.yywspace.anethack.gamedata.MonsterLink
import java.lang.Integer.min
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ceil

class NHWMessage(wid: Int, type:NHWindowType, private val nh: NetHack) : NHWindow(wid, type) {
    val messageList = CopyOnWriteArrayList<NHMessage>()
    private var messageView: NHMessageView = nh.binding.messageView
    private var lastClickTime = 0L

    init {
        messageView.initMessage(this)
        // 只设置回调，不在这里直接操作 View（本类在游戏线程里创建）
        messageView.onMessageClick = {
            if (System.currentTimeMillis() - lastClickTime >= 1000) {
                displayWindow(true)
                lastClickTime = System.currentTimeMillis()
            }
        }
        // 消息里的怪物名：点击后打开怪物详情（弹窗统一从 nh.monsterDialog 取）
        messageView.onMonsterClick = { name ->
            nh.monsterDialog?.showFromMonsterName(name)
        }
    }

    fun getRecentMessageList(size: Int):List<NHMessage> {
        // 避免列表为空时maxBy报NoSuchElementException异常
        if (messageList.isEmpty())
            return emptyList()
        val lastMsg = messageList.maxBy { it.time }
        // 最多取最新 size 条：同一回合的多条消息共享时间戳，若按同批全部取出会
        // 超过消息栏行数，最新消息被底部裁剪看不见
        val msgSize = messageList.size.coerceAtMost(size)
        return messageList.reversed().subList(0, msgSize).map { nhMessage ->
            nhMessage.attach(lastMsg.time)
        }.toList()
    }

    override fun curs(x: Int, y: Int) {

    }

    override fun displayWindow(blocking: Boolean) {
        Log.d("NHWMessage","NHWMessage: displayWindow")
        if(blocking) {
            nh.runOnUi { _, context ->
                val dialogTextView = View.inflate(context, R.layout.dialog_message, null)
                    .apply {
                        findViewById<RecyclerView>(R.id.dialog_message_list).apply {
                            val messageAdapter = NHWMessageAdapter(messageList, 20, nh)
                            adapter = messageAdapter
                            layoutManager = LinearLayoutManager(
                                context,
                                LinearLayoutManager.VERTICAL, false
                            ).apply {
                                stackFromEnd = true
                            }
                        }
                    }
                AlertDialog.Builder(context).apply {
                    setTitle(R.string.message_history)
                    setView(dialogTextView)
                    setPositiveButton(R.string.dialog_confirm, null)
                    setOnDismissListener {
                        nh.command.sendCommand(NHKeyCommand(27.toChar()))
                    }
                    create()
                    show(nh.prefs.immersiveMode)
                }
            }
        }
    }

    override fun clearWindow(isRogueLevel: Int) {
        Log.d("NHWMessage","NHWMessage: clearWindow")
    }

    override fun destroyWindow() {

    }

    override fun putString(attr: Int, msg: String, color: Int) {
        if(msg.isEmpty()) return
        Log.d("NHWMessage","NHWMessage: putString $msg")
        if (messageList.size > nh.prefs.messageHistorySize)
            messageList.removeAt(0)
        messageList.add(NHMessage(NHString(msg.trim(), attr), nh.command.lastCmdTime))
        // 新消息到达 → 唤醒消息绘制线程（事件驱动，取代定时循环）
        messageView.requestRedraw()
    }

    class NHWMessageAdapter(
        private val messageList:List<NHMessage>,
        private val itemSize:Int,
        private val nh: NetHack
    ): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var lastCmdMsg:NHMessage = messageList.maxBy { it.time }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.dialog_message_item, parent, false)
            return MessageViewHolder(view)
        }

        override fun getItemCount(): Int {
            return ceil(messageList.size / itemSize.toDouble()).toInt()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val start = position * itemSize
            val end = min(start + itemSize, messageList.size)
            val subList = messageList.subList(start, end)
            // 旧消息（非最新一批）用"默认文字色"：亮色下深灰、深色下柔和白，避免和背景同色
            val oldMsgColor = if (NHColor.darkTheme) NHColor.CLR_WHITE else NHColor.CLR_BLACK
            val messageText = SpannableStringBuilder()
            for ((idx, message) in subList.withIndex()) {
                messageText.append(
                    message.attach(lastCmdMsg.time, oldMsgColor).toSpannableString()
                )
                if (idx != subList.size -1)
                    messageText.append("\n")
            }
            // 消息里出现的怪物名高亮成可点击，点了弹怪物详情
            val itemContext = holder.itemView.context
            MonsterLink.highlight(itemContext, messageText) { name ->
                nh.monsterDialog?.showFromMonsterName(name)
            }
            (holder as MessageViewHolder).apply {
                itemMessage.text = messageText
                // 长按选择文字时的底色：不要设成透明，否则深色主题下看不见选区
                itemMessage.highlightColor =
                    ContextCompat.getColor(itemContext, R.color.text_selection_highlight)
                // 这一行整行是怪物名时，点这一行的任何位置（含右侧空白）都弹详情
                itemMessage.setOnTouchListener { view, event ->
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        val span = MonsterLink.spanAtLine(itemMessage, event)
                        if (span != null) {
                            span.onClick(view)
                            return@setOnTouchListener true
                        }
                    }
                    false
                }
            }
        }

        private class MessageViewHolder(itemView: View)  : RecyclerView.ViewHolder(itemView) {
            val itemMessage: TextView = itemView.findViewById(R.id.message)
        }
    }

}
