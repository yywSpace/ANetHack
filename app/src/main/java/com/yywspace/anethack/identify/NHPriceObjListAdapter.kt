package com.yywspace.anethack.identify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.R
class NHPriceObjListAdapter(private val objList: List<Map<String,String>>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var onItemClick:((view: View, index:Int, item: Map<String,String>)->Unit)? = null
    inner class ObjListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val objName : TextView
        val objCost : TextView
        init {
            objName = view.findViewById(R.id.obj_name)
            objCost = view.findViewById(R.id.obj_cost)
        }
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
            objName.text = obj["Name"]
            objCost.text = obj["Cost"]
            itemView.setOnClickListener {
                onItemClick?.invoke(it, position, obj)
            }
        }
    }
}