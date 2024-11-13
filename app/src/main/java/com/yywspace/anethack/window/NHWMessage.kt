package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yywspace.anethack.map.NHMessageSurfaceView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.entity.NHMessage
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.extensions.show
import java.util.concurrent.CopyOnWriteArrayList

class NHWMessage(wid: Int, type:NHWindowType, private val nh: NetHack) : NHWindow(wid, type) {
    val messageList = CopyOnWriteArrayList<NHMessage>()
    private var messageView: NHMessageSurfaceView = nh.binding.messageView
    private var lastClickTime = 0L

    init {
        messageView.initMessage(nh,this)
        messageView.setOnClickListener {
            if (System.currentTimeMillis() - lastClickTime >= 1000) {
                displayWindow(true)
                lastClickTime = System.currentTimeMillis();
            }
        }
    }

    fun getRecentMessageList(size: Int):List<NHMessage> {
        // 避免列表为空时maxBy报NoSuchElementException异常
        if (messageList.isEmpty())
            return emptyList()
        val lastMsg = messageList.maxBy { it.time }
        val newestCnt = messageList.count { it.time.isEqual(lastMsg.time) }
        val msgSize = messageList.size.coerceAtMost(size)
        return messageList.reversed().subList(0, if (newestCnt > msgSize) newestCnt else msgSize).map  { nhMessage ->
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
                        val lastMsg = messageList.maxBy { it.time }
                        val messageText = SpannableStringBuilder()
                        for (message in messageList) {
                            messageText.append(
                                message.attach(lastMsg.time, NHColor.CLR_BLACK).toSpannableString()
                            ).append("\n")
                        }
                        findViewById<TextView>(R.id.dialog_message_text).text = messageText
                        findViewById<ScrollView>(R.id.dialog_message_scroll).apply {
                            post {
                                fullScroll(ScrollView.FOCUS_DOWN)
                            }
                        }
                    }

                AlertDialog.Builder(context).apply {
                    setTitle(R.string.message_history)
                    setView(dialogTextView)
                    setPositiveButton(R.string.dialog_confirm, null)
                    setOnDismissListener {
                        nh.command.sendCommand(NHCommand(27.toChar()))
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
            messageList.removeFirst()
        messageList.add(NHMessage(NHString(msg.trim(), attr), nh.command.lastCmdTime))
    }
}