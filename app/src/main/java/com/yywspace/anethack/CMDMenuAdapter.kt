package com.yywspace.anethack

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.window.NHWMenu
import com.yywspace.anethack.window.NHWMenuAdapter
import com.yywspace.anethack.window.NHWMenuItem

class CMDMenuAdapter(private val cmdList: List<String>):
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var onItemClick:((view: View, index:Int, value:String)->Unit)? = null

    inner class CMDViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemAcc: TextView
        val itemSelectAmount: TextView
        val itemTitle : TextView
        val itemSubtitle : TextView
        val itemCheckBox: CheckBox
        init {
            itemAcc = view.findViewById(R.id.item_accelerator)
            itemSelectAmount = view.findViewById(R.id.item_select_amount)
            itemTitle = view.findViewById(R.id.item_title)
            itemSubtitle = view.findViewById(R.id.item_subtitle)
            itemCheckBox = view.findViewById(R.id.item_checkbox)
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_menu_item, parent, false)
        return CMDViewHolder(view)
    }

    override fun getItemCount(): Int {
        return cmdList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as CMDViewHolder).apply {
            itemCheckBox.visibility = View.GONE
            itemSubtitle.visibility = View.GONE
            itemSelectAmount.visibility = View.GONE
            itemView.setOnClickListener {
                onItemClick?.invoke(it, position, cmdList[position])
            }
            itemTitle.setTextColor(Color.BLACK)
            itemTitle.text = cmdList[position]
            itemAcc.text = ""
        }
    }

}