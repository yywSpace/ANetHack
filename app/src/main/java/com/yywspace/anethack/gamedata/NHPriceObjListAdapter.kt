package com.yywspace.anethack.gamedata

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.R

class NHPriceObjListAdapter(private val objList: List<Item>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** 点击某一行，打开对应物品详情 */
    var onItemClick: ((Item) -> Unit)? = null
    class ObjListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val objName : TextView = view.findViewById(R.id.obj_name)
        val objCost : TextView = view.findViewById(R.id.obj_cost)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_price_obj_item, parent, false)
        return ObjListViewHolder(view)
    }

    override fun getItemCount(): Int {
        return objList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ObjListViewHolder).apply {
            val obj = objList[position]
            objName.text = obj.name
            objCost.text = obj.price.toString()
            itemView.setOnClickListener { onItemClick?.invoke(obj) }
        }
    }
}
