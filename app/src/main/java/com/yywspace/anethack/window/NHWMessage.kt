package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.text.method.ScrollingMovementMethod
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

class NHWMessage(wid: Int, private val nh: NetHack) : NHWindow(wid) {
    val messageList = mutableListOf<NHMessage>()
    private var limitMessageList = mutableListOf<NHMessage>()
    private lateinit var messageAdapter:NHWMessageAdapter
    private lateinit var messageRecyclerView:RecyclerView

    init {
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
                    show()
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
        nh.runOnUi (false){ _, _ ->
            val message = NHMessage(NHString(msg.trim(), attr), nh.command.lastCmdTime)
            messageList.add(message)
            limitMessageList.add(message)
            messageAdapter.notifyItemInserted(limitMessageList.size - 1)
            if(limitMessageList.size > 10) {
                limitMessageList.removeFirst()
                messageAdapter.notifyItemRemoved(0)
            }
            if(messageList.size > 50) {
                messageList.removeFirst()
            }
            limitMessageList.forEachIndexed { index, nhMessage ->
                if (nhMessage.time == nh.command.lastCmdTime)
                    nhMessage.value.nhColor = NHColor.CLR_GREEN
                else
                    nhMessage.value.nhColor = NHColor.NO_COLOR
                messageAdapter.notifyItemChanged(index)
            }
            messageRecyclerView.scrollToPosition(limitMessageList.size - 1)
        }
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