package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHExtendCommand
import com.yywspace.anethack.entity.NHExtCmd
import com.yywspace.anethack.extensions.showImmersive


class NHExtCmdChoose(val nh: NetHack) {
    private lateinit var extCmdList: Array<String>
    private var commandList: MutableList<NHExtCmd> = mutableListOf()
    private fun showExtCmdDialog(commandList: MutableList<NHExtCmd>) {
        nh.runOnUi() { _, context ->
            val dialog = AlertDialog.Builder(context).run {
                setTitle(R.string.ext_cmd_select)
                setNeutralButton(R.string.dialog_cancel) { _,_ ->
                    finishExtCmdChoose(-1)
                }
                create()
            }
            val adapter =NHExtCmdAdapter(commandList).apply {
                onItemClick = { _, _, item ->
                    finishExtCmdChoose(item.idx)
                    dialog.dismiss()
                }
            }
            val view = View.inflate(context, R.layout.dialog_extend_command,null)
                .apply {
                    findViewById<RecyclerView>(R.id.ext_cmd_list).apply {
                        layoutManager = LinearLayoutManager(context)
                        this.adapter = adapter
                    }
                    findViewById<SearchView>(R.id.ext_cmd_search).apply {
                        isIconified = false
                        setOnClickListener {
                            isIconified = false
                        }
                        setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                            override fun onQueryTextChange(queryText: String?): Boolean {
                                adapter.filter(queryText?:"")
                                return true
                            }
                            override fun onQueryTextSubmit(queryText: String?): Boolean {
                                adapter.filter(queryText?:"")
                                return true
                            }
                        })
                    }
                }
            dialog.apply {
                setCancelable(false)
                setView(view)
                showImmersive()
            }
        }
    }

    fun waitForExtCmdChoose(extCmdList:Array<String>):Int {
        var cmd = nh.command.findAnyCommand<NHExtendCommand>()
        this.extCmdList = extCmdList
        commandList.clear()
        for(i in extCmdList.indices step 2) {
            if(extCmdList[i].isEmpty()
                || extCmdList[i+1].isEmpty()
                || extCmdList[i] == "?"
                || extCmdList[i] == "#")
                continue

            if(cmd != null && cmd.name == extCmdList[i])
                return i/2
            commandList.add(NHExtCmd(i/2, extCmdList[i], extCmdList[i+1]))
        }
        showExtCmdDialog(commandList)
        cmd = nh.command.waitForAnyCommand()
        return cmd.idx
    }

    private fun finishExtCmdChoose(idx:Int) {
        nh.command.sendCommand(NHExtendCommand(idx))
    }

    private class NHExtCmdAdapter(val commandList: List<NHExtCmd>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var onItemClick:((view: View, index:Int, item: NHExtCmd)->Unit)? = null
        var filteredCmdList: MutableList<NHExtCmd> = commandList.toMutableList()
        inner class ExtCmdViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val itemCommandDesc : TextView
            val itemCommand : TextView
            init {
                itemCommand = view.findViewById(R.id.item_command)
                itemCommandDesc = view.findViewById(R.id.item_command_desc)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.dialog_extend_command_item, parent, false)
                return ExtCmdViewHolder(view)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun filter(keyword:String) {
            filteredCmdList.clear()
            if(keyword.isNotEmpty()) {
                for (cmd in commandList) {
                    if (cmd.name.contains(keyword)) {
                        filteredCmdList.add(cmd)
                    }
                }
            }else{
                filteredCmdList = commandList.toMutableList()
            }
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int {
            return filteredCmdList.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val cmd = filteredCmdList[position]
            (holder as ExtCmdViewHolder).apply {
                itemCommand.text = cmd.name
                itemCommandDesc.text = cmd.desc
                itemView.setOnClickListener {
                    onItemClick?.invoke(it,position, cmd)
                }
            }
        }
    }
}