package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
                Log.d("NHWMessage","messageView: setOnClickListener")
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
            nhMessage.clone().apply {
                if (time == lastMsg.time)
                    value.nhColor = NHColor.CLR_GREEN
                else
                    value.nhColor = NHColor.CLR_WHITE
            }
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
                            val messageAdapter = NHWMessageAdapter(messageList).apply {
                                omMessageClick = { _, _ ->

                                }
                            }
                            adapter = messageAdapter
                            layoutManager = LinearLayoutManager(context,
                                LinearLayoutManager.VERTICAL, true).apply {
                                stackFromEnd = true
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


    class NHWMessageAdapter(private val messageList:List<NHMessage>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var omMessageClick:((view:View, message:NHMessage)->Unit)? = null
        private var lastCmdMsg:NHMessage = messageList.maxBy { it.time }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.dialog_message_item, parent, false)
            return MessageViewHolder(view)
        }

        override fun getItemCount(): Int {
            return messageList.size
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val message = messageList[position]
            (holder as MessageViewHolder).apply {
                message.apply {
                    if (time == lastCmdMsg.time)
                        value.nhColor = NHColor.CLR_GREEN
                    else
                        value.nhColor = NHColor.CLR_BLACK
                    itemMessage.text = value.toSpannableString()
                    itemMessage.setTextIsSelectable(true);
                }

                itemView.setOnClickListener { v ->
                    omMessageClick?.invoke(v, message)
                }
            }
        }

       private inner class MessageViewHolder(itemView: View)  : RecyclerView.ViewHolder(itemView) {
           val itemMessage: TextView

           init {
               itemMessage = itemView.findViewById(R.id.message)
           }
       }
    }

}