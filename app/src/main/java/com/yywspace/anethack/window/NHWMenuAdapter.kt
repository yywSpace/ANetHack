package com.yywspace.anethack.window

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.R
import com.yywspace.anethack.entity.NHMenuItem
import java.lang.RuntimeException


class NHWMenuAdapter(private val nhwMenu: NHWMenu) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var onItemClick:((view:View, index:Int, menuItem: NHMenuItem)->Unit)? = null
    var onItemLongClick:((view:View, index:Int, menuItem: NHMenuItem)->Unit)? = null

    inner class OptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemAcc: TextView
        val itemSelectAmount: TextView
        val itemTitle : TextView
        val itemSubtitle : TextView
        val itemCheckBox:CheckBox
        init {
            itemAcc = view.findViewById(R.id.item_accelerator)
            itemSelectAmount = view.findViewById(R.id.item_select_amount)
            itemTitle = view.findViewById(R.id.item_title)
            itemSubtitle = view.findViewById(R.id.item_subtitle)
            itemCheckBox = view.findViewById(R.id.item_checkbox)
        }
    }

    inner class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemHeader : TextView
        init {
            itemHeader = view.findViewById(R.id.item_header)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            OPTION -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.dialog_menu_item, viewGroup, false)
                OptionViewHolder(view)
            }

            TEXT -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.dialog_menu_item_header, viewGroup, false)
                TextViewHolder(view)
            }

            else -> {
                throw RuntimeException("no such view type: $viewType")
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val menuItem = nhwMenu.nhMenuItems[position]
        return if(menuItem.isHeader())
            TEXT
        else
            OPTION
    }
    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val menuItem = nhwMenu.nhMenuItems[position]

        when(getItemViewType(position)) {
            OPTION -> {
                (viewHolder as OptionViewHolder).apply {
                    if(nhwMenu.selectMode != NHWMenu.SelectMode.PickNone) {
                        if (nhwMenu.selectMode == NHWMenu.SelectMode.PickOne) {
                            itemCheckBox.visibility = View.GONE
                        }
                        itemView.setOnLongClickListener {
                            onItemLongClick?.invoke(it, position, menuItem)
                            true
                        }
                        itemView.setOnClickListener {
                            onItemClick?.invoke(it, position, menuItem)
                            if (nhwMenu.selectMode == NHWMenu.SelectMode.PickMany) {
                                menuItem.isSelected = !menuItem.isSelected
                                itemCheckBox.isChecked = menuItem.isSelected
                            }
                        }
                    }

                    if (menuItem.hasSubtitle()) {
                        itemSubtitle.visibility = View.VISIBLE
                        itemSubtitle.text = menuItem.subtitle
                    } else
                        itemSubtitle.visibility = View.GONE

                    if(menuItem.selectedCount > 0) {
                        val selected = "[${menuItem.selectedCount}]"
                        itemSelectAmount.text = selected
                        itemSelectAmount.visibility = View.VISIBLE
                    } else
                        itemSelectAmount.visibility = View.GONE
                    itemAcc.text = menuItem.accelerator.toString()
                    itemTitle.text = menuItem.title.toSpannableString()
                    itemCheckBox.isChecked = menuItem.isSelected
                }
            }
            TEXT -> {
                (viewHolder as TextViewHolder).apply {
                    itemHeader.text = menuItem.title.toString()
                }

            }
        }

    }

    override fun getItemCount() = nhwMenu.nhMenuItems.size

    companion object {
        private const val OPTION = 0
        private const val TEXT = 1
    }
}
