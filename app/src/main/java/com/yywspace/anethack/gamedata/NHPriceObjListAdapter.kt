package com.yywspace.anethack.gamedata

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.R

class NHPriceObjListAdapter : ListAdapter<Item, NHPriceObjListAdapter.ObjListViewHolder>(Diff) {

    /** 点击某一行，打开对应物品详情 */
    var onItemClick: ((Item) -> Unit)? = null

    class ObjListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val objName: TextView = view.findViewById(R.id.obj_name)
        val objCost: TextView = view.findViewById(R.id.obj_cost)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ObjListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_price_obj_item, parent, false)
        return ObjListViewHolder(view)
    }

    override fun onBindViewHolder(holder: ObjListViewHolder, position: Int) {
        val obj = getItem(position)
        holder.objName.text = obj.name
        holder.objCost.text = obj.price.toString()
        holder.itemView.setOnClickListener { onItemClick?.invoke(obj) }
    }

    private object Diff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
            oldItem.name == newItem.name && oldItem.itemClass == newItem.itemClass

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
            oldItem == newItem
    }
}
