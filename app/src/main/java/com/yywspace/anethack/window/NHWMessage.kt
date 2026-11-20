package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.entity.NHMessage
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.gameview.NHMessageView
import com.yywspace.anethack.gamedata.ItemLink
import com.yywspace.anethack.gamedata.ItemMatch
import com.yywspace.anethack.gamedata.MonsterLink
import com.yywspace.anethack.gamedata.MonsterMatch
import java.lang.Integer.min
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ceil

class NHWMessage(wid: Int, type:NHWindowType, private val nh: NetHack) : NHWindow(wid, type) {

    private companion object {
        /** 消息最多保留多少条 */
        const val MESSAGE_HISTORY_SIZE = 500
    }

    val messageList = CopyOnWriteArrayList<NHMessage>()
    private var messageView: NHMessageView = nh.binding.messageView

    init {
        messageView.initMessage(this)
        // 只设置回调，不在这里直接操作 View（本类在游戏线程里创建）
        messageView.onMessageLongClick = {
            displayWindow(true)
        }
        // 消息里的怪物名：点击后打开怪物详情（弹窗统一从 nh.monsterDialog 取）
        messageView.onMonsterClick = { match -> showMonsterMatch(match) }
        // 消息里的物品名：点击后打开对应物品详情
        messageView.onItemClick = { item -> showItemMatch(item) }
    }

    /** 展开态用的全量消息适配器，每条消息一个 item。 */
    fun createHistoryAdapter(): NHWMessageAdapter =
        NHWMessageAdapter(
            messageList.toList(),
            1,
            nh,
            onMonsterClick = { match -> showMonsterMatch(match) },
            onItemClick = { item -> showItemMatch(item) },
            onMessageClick = { messageView.toggleExpanded() },
            onMessageLongClick = { displayWindow(true) },
            inMessageBar = true
        )


    fun getRecentMessageList(size: Int):List<NHMessage> {
        val snapshot = messageList.toList()
        if (snapshot.isEmpty())
            return emptyList()
        val lastMsg = snapshot.lastOrNull { !it.history }
        val msgSize = snapshot.size.coerceAtMost(size)
        val newColor = NHColor.fromInt(nh.prefs.messageNewColor)
        return snapshot.asReversed().take(msgSize).map { nhMessage ->
            nhMessage.attach(lastMsg?.source, newColor = newColor)
        }
    }

    override fun curs(x: Int, y: Int) {

    }

    override fun displayWindow(blocking: Boolean) {
        if(blocking) {
            nh.runOnUi { _, context ->
                val dialogTextView = View.inflate(context, R.layout.dialog_message, null)
                    .apply {
                        findViewById<RecyclerView>(R.id.dialog_message_list).apply {
                            val messageAdapter = NHWMessageAdapter(
                                messageList.toList(),
                                20,
                                nh,
                                onMonsterClick = { match -> showMonsterMatch(match) },
                                onItemClick = { item -> showItemMatch(item) }
                            )
                            adapter = messageAdapter
                            layoutManager = LinearLayoutManager(
                                context,
                                LinearLayoutManager.VERTICAL, false
                            ).apply {
                                stackFromEnd = true
                            }
                        }
                    }
                val dialog = NHDialog(
                    context = context,
                    content = dialogTextView,
                    title = context.getString(R.string.message_history),
                    rightButton = NHDialogButton(context.getString(R.string.dialog_confirm)),
                    immersive = nh.prefs.immersiveMode
                )
                dialog.setOnDismissListener {
                    nh.command.sendCommand(NHKeyCommand(27.toChar()))
                }
                dialog.show()
            }
        }
    }

    override fun clearWindow(isRogueLevel: Int) {
    }

    override fun destroyWindow() {

    }

    override fun putString(attr: Int, msg: String, color: Int) {
        if(msg.isEmpty()) return
        while (messageList.size >= MESSAGE_HISTORY_SIZE)
            messageList.removeAt(0)
        // 消息来源：游戏当前正在处理的指令（行走、look、命令栏指令等都会记下来）
        messageList.add(
            NHMessage(
                NHString(msg.trim(), attr),
                nh.command.lastConsumedCommand
            )
        )
        // 新消息到达 → 唤醒消息绘制线程（事件驱动，取代定时循环）
        messageView.onNewMessage()
    }

    class NHWMessageAdapter(
        private var messageList: List<NHMessage>,
        private val itemSize: Int,
        private val nh: NetHack,
        private val onMonsterClick: (MonsterMatch) -> Unit,
        private val onItemClick: (ItemMatch) -> Unit,
        private val onMessageClick: (() -> Unit)? = null,
        private val onMessageLongClick: (() -> Unit)? = null,
        private val inMessageBar: Boolean = false
    ): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.dialog_message_item, parent, false)
            return MessageViewHolder(view)
        }

        override fun getItemCount(): Int {
            return ceil(messageList.size / itemSize.toDouble()).toInt()
        }

        /** 用稳定快照替换当前数据，避免绑定期间游戏线程继续修改列表。 */
        fun updateMessages(newList: List<NHMessage>) {
            messageList = newList
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            // 游戏线程可能同时追加；绑定阶段使用稳定快照，避免 COWSubList 并发修改
            val snapshot = messageList.toList()
            val start = position * itemSize
            val end = min(start + itemSize, snapshot.size)
            if (start >= end) return
            val subList = snapshot.subList(start, end)
            // 旧消息（非最新一批）用"默认文字色"：亮色下深灰、深色下柔和白，避免和背景同色
            val itemContext = holder.itemView.context
            // 游戏消息栏背景始终是黑色，旧消息统一用白色；历史弹窗跟随主题
            val oldMsgColor =
                if (inMessageBar || NHColor.darkPalette) NHColor.CLR_WHITE else NHColor.CLR_BLACK
            val messageText = SpannableStringBuilder()
            for ((idx, message) in subList.withIndex()) {
                if (idx != 0)
                    messageText.append("\n")
                val lineStart = messageText.length
                messageText.append(
                    message.attach(snapshot.lastOrNull { !it.history }?.source, oldMsgColor, NHColor.fromInt(nh.prefs.messageNewColor)).toSpannableString()
                )
                // 只解析 look 指令产生的消息（格式可预期），其它消息保持原样
                if (message.fromInspect) {
                    MonsterLink.highlight(itemContext, messageText, lineStart, messageText.length) { match ->
                        onMonsterClick(match)
                    }
                    ItemLink.highlight(itemContext, messageText, lineStart, messageText.length) { item ->
                        onItemClick(item)
                    }
                }
            }
            (holder as MessageViewHolder).apply {
                val selectable = !inMessageBar
                itemMessage.setTextIsSelectable(selectable)
                itemMessage.text = messageText
                if (inMessageBar) {
                    itemMessage.isClickable = true
                    itemMessage.setOnClickListener { onMessageClick?.invoke() }
                    itemMessage.setOnLongClickListener {
                        onMessageLongClick?.invoke()
                        true
                    }
                }
                // 长按选择文字时的底色：不要设成透明，否则深色主题下看不见选区
                itemMessage.highlightColor =
                    ContextCompat.getColor(itemContext, R.color.text_selection_highlight)
                // 这一行整行是怪物名时，点这一行的任何位置（含右侧空白）都弹详情
                itemMessage.setOnTouchListener { view, event ->
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        // 消息栏只响应文字本身；历史 dialog 允许点整行
                        val itemSpan = if (inMessageBar) {
                            ItemLink.spanAtTouch(itemMessage, event)
                        } else {
                            ItemLink.spanAtLine(itemMessage, event)
                        }
                        if (itemSpan != null) {
                            itemSpan.onClick(view)
                            return@setOnTouchListener true
                        }

                        val monsterSpan = if (inMessageBar) {
                            MonsterLink.spanAtTouch(itemMessage, event)
                        } else {
                            MonsterLink.spanAtLine(itemMessage, event)
                        }
                        if (monsterSpan != null) {
                            monsterSpan.onClick(view)
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

    private fun showMonsterMatch(match: MonsterMatch) {
        if (match.monsters.size == 1)
            nh.monsterDialog?.showMonster(match.monsters.first())
        else
            nh.monsterDialog?.showMonsters(match.monsters)
    }

    private fun showItemMatch(match: ItemMatch) {
        if (match.items.size == 1)
            nh.itemDialog?.showItem(match.items.first().name, match.items.first().itemClass)
        else
            nh.itemDialog?.showItems(match.items)
    }
}
