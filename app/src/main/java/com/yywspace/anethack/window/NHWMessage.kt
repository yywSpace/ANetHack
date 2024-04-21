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
import com.yywspace.anethack.NHInfoSurfaceView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.entity.NHMessage
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.extensions.showImmersive
import kotlin.streams.toList

class NHWMessage(wid: Int, private val nh: NetHack) : NHWindow(wid) {
    val messageList = mutableListOf<NHMessage>()
    private var messageView: NHInfoSurfaceView = nh.binding.infoView
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

    fun getMessageList(size: Int):List<NHMessage> {
        val last = messageList.maxBy { it.time }
        return messageList.reversed().stream().limit(size.toLong()).map  { nhMessage ->
            nhMessage.clone().apply {
                if (time == last.time)
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
                            val last = messageList.maxBy { it.time }
                            val dialogMessageList = messageList.map  { nhMessage ->
                                nhMessage.clone().apply {
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
        messageList.add(NHMessage(NHString(msg.trim(), attr), nh.command.lastCmdTime))
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