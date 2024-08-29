package com.yywspace.anethack.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.R

class NHPosKeyAdapter(private val keyList: List<String>):
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var onItemClickListener: OnItemClickListener?= null
    inner class PostKeyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemKey: TextView
        init {
            itemKey = view.findViewById(R.id.pos_key_item)
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.popup_window_pos_key_item, parent, false)
        return PostKeyViewHolder(view)
    }

    override fun getItemCount(): Int {
        return keyList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as PostKeyViewHolder).apply {
            itemKey.text = keyList[position]
            itemKey.setOnClickListener {
                onItemClickListener?.onItemClick(it, position)
            }
        }
    }
    interface OnItemClickListener{
        fun onItemClick(view: View, position: Int)
    }
}