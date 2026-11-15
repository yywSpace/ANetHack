package com.yywspace.anethack.gamedata

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/** 把现成的资料卡片 View 交给 RecyclerView 回收复用。 */
class InfoCardAdapter<T>(
    private val cardOf: (T) -> View,
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, RecyclerView.ViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return object : RecyclerView.ViewHolder(container) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val container = holder.itemView as FrameLayout
        container.removeAllViews()
        val card = cardOf(getItem(position))
        val bottomMargin = (card.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.bottomMargin ?: 0
        container.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { this.bottomMargin = bottomMargin }
        )
    }
}
