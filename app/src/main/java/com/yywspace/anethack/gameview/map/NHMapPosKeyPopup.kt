package com.yywspace.anethack.gameview.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.R

/** 地图长按位置上的 Look / Run / Travel 菜单。 */
class NHMapTileActionPopup(
    private val context: Context,
    private val onLook: (Point) -> Unit,
    private val onRun: (Float, Float) -> Unit,
    private val onTravel: (Point) -> Unit
) {
    private var popupWindow: PopupWindow? = null

    private enum class Action { LOOK, RUN, TRAVEL }

    @SuppressLint("InflateParams")
    fun show(anchor: View, x: Float, y: Float, point: Point, tileHeight: Float) {
        popupWindow?.dismiss()

        val actions = listOf(Action.LOOK, Action.RUN, Action.TRAVEL)
        val posKeyList = actions.map { action ->
            context.getString(
                when (action) {
                    Action.LOOK -> R.string.map_popup_look
                    Action.RUN -> R.string.map_popup_run
                    Action.TRAVEL -> R.string.map_popup_travel
                }
            )
        }
        val window = PopupWindow(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val content = LayoutInflater.from(context)
            .inflate(R.layout.popup_window_pos_key, null)
        content.findViewById<RecyclerView>(R.id.pos_key_list).apply {
            adapter = PosKeyAdapter(posKeyList).apply {
                onItemClickListener = object : PosKeyAdapter.OnItemClickListener {
                    override fun onItemClick(view: View, position: Int) {
                        when (actions[position]) {
                            Action.LOOK -> onLook(point)
                            Action.RUN -> onRun(x, y)
                            Action.TRAVEL -> onTravel(point)
                        }
                        window.dismiss()
                    }
                }
            }
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        }
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        window.apply {
            contentView = content
            isOutsideTouchable = true
            showAtLocation(
                anchor,
                Gravity.NO_GRAVITY,
                (x - content.measuredWidth / 2).toInt(),
                (y - content.measuredHeight - tileHeight / 2).toInt()
            )
        }
        popupWindow = window
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    private class PosKeyAdapter(
        private val keyList: List<String>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var onItemClickListener: OnItemClickListener? = null

        class PostKeyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val itemKey: TextView = view.findViewById(R.id.pos_key_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.popup_window_pos_key_item, parent, false)
            return PostKeyViewHolder(view)
        }

        override fun getItemCount(): Int = keyList.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder as PostKeyViewHolder).apply {
                itemKey.text = keyList[position]
                itemKey.setOnClickListener {
                    onItemClickListener?.onItemClick(it, position)
                }
            }
        }

        interface OnItemClickListener {
            fun onItemClick(view: View, position: Int)
        }
    }
}
