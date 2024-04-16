package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.entity.NHMessage
import com.yywspace.anethack.extensions.showImmersive
import java.util.concurrent.LinkedBlockingDeque

class NHWMessage(wid: Int, private val nh: NetHack) : NHWindow(wid) {
    val messageList = mutableListOf<NHMessage>()
    private var limitMessageList = mutableListOf<NHMessage>()

    private lateinit var messageAdapter:NHWMessageAdapter
    private lateinit var messageRecyclerView:RecyclerView

    private var isMessageRefreshing = false
    private var messageInterval:Long = 0
    private val messageBuffer = mutableListOf<NHMessage>()
    private val messageQueue = LinkedBlockingDeque<NHMessage>()
    private val messageConsumeThread:Thread = Thread {
        while (true) {
            if(!isMessageRefreshing) {
                if(messageBuffer.isEmpty() && messageQueue.isEmpty()) {
                    val message = messageQueue.take()
                    messageBuffer.add(message)
                    messageInterval = System.currentTimeMillis()
                } else {
                        val message = messageQueue.poll()
                        if (message != null)
                            messageBuffer.add(message)
                        if(System.currentTimeMillis() - messageInterval > 300) { // 210
                            Log.d("refreshMessage", "messageBuffer:${messageBuffer}")
                            refreshMessage(messageBuffer)
                        }
                    }
            }
        }
    }


    init {
        messageConsumeThread.start()

        nh.runOnUi (false){ binding, context ->
            messageAdapter = NHWMessageAdapter(limitMessageList).apply {
                omItemViewClick = {
                    displayWindow(true)
                }
            }
            messageRecyclerView = binding.messageBar.apply {
                adapter = messageAdapter
                layoutManager = LinearLayoutManager(context)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshMessage(buffer:List<NHMessage>) {
        isMessageRefreshing = true
        nh.runOnUi (false) { _, _ ->
            messageList.addAll(buffer)
            limitMessageList.addAll(buffer)
            while(limitMessageList.size > 10) {
                limitMessageList.removeFirst()
            }
            while(messageList.size > 50) {
                messageList.removeFirst()
            }
            limitMessageList.forEach { nhMessage ->
                if (buffer.contains(nhMessage))
                    nhMessage.value.nhColor = NHColor.CLR_GREEN
                else
                    nhMessage.value.nhColor = NHColor.NO_COLOR
            }
            messageAdapter.notifyDataSetChanged()
            Log.d("refreshMessage", "message:${buffer}")
            messageRecyclerView.scrollToPosition(limitMessageList.size - 1)
            messageBuffer.clear()
            isMessageRefreshing = false
        }
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
                            val last = messageList.maxBy { it.time }
                            val dialogMessageList = messageList.map  { nhMessage ->
                                nhMessage.copy().apply {
                                    if (time == last.time)
                                        value.nhColor = NHColor.CLR_GREEN
                                    else
                                        value.nhColor = NHColor.CLR_BLACK
                                    isSelectable = true
                                }
                            }

                            val messageAdapter = NHWMessageAdapter(dialogMessageList).apply {
                                omItemViewClick = {

                                }
                            }
                            adapter = messageAdapter
                            layoutManager = LinearLayoutManager(context,
                                LinearLayoutManager.VERTICAL, true).apply {
                                stackFromEnd = true

                            }
                        }
                    }
                MaterialAlertDialogBuilder(context).apply {
                    setTitle(R.string.message_history)
                    setView(dialogTextView)
                    setPositiveButton(R.string.dialog_confirm) { _, _ ->
                        nh.command.sendCommand(NHCommand(27.toChar()))
                    }
                    create()
                    showImmersive()
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
        val message = NHMessage(NHString(msg.trim(), attr), nh.command.lastCmdTime)
        messageQueue.add(message)
    }


    class NHWMessageAdapter(private val messageList:List<NHMessage>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var omItemViewClick:((view:View)->Unit)? = null
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
                itemMessage.text = message.value.toSpannableString()
                itemMessage.setTextIsSelectable(message.isSelectable);
                itemView.setOnClickListener { v ->
                    omItemViewClick?.invoke(v)
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