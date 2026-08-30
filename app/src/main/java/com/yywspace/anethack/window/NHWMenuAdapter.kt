package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NHTileSet
import com.yywspace.anethack.R
import com.yywspace.anethack.entity.NHMenuItem


class NHWMenuAdapter(private val nhwMenu: NHWMenu, private val tileSet:NHTileSet) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var onItemClick:((view:View, index:Int, menuItem: NHMenuItem)->Unit)? = null
    var onItemLongClick:((view:View, index:Int, menuItem: NHMenuItem)->Unit)? = null
    /** 点击菜单项的大括号价格文本（如 "sell 67"）回调，传入菜单项和点击的价格段 */
    var onPriceQuoteClick:((menuItem: NHMenuItem, quote: String)->Unit)? = null

    inner class OptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val itemAcc: TextView = view.findViewById(R.id.item_accelerator)
        private val itemSelectAmount: TextView = view.findViewById(R.id.item_select_amount)
        private val itemTitle : TextView = view.findViewById(R.id.item_title)
        private val itemSubtitle : TextView = view.findViewById(R.id.item_subtitle)
        private val itemCheckBox:CheckBox = view.findViewById(R.id.item_checkbox)
        private val itemTile:ImageView = view.findViewById(R.id.item_tile)
        /** 本次触摸按下时是否落在价格文本上，以及对应的价格段 */
        private var downOnQuote = false
        private var downQuote: String? = null

        @SuppressLint("ClickableViewAccessibility")
        fun bind(position: Int, menuItem: NHMenuItem) {
            if (!tileSet.isTTY() && menuItem.glyph != NHTileSet.TILE_UNEXPLORED) {
                val bitmap = tileSet.getTile(menuItem.glyph)
                itemTile.setImageBitmap(bitmap)
                itemTile.visibility = View.VISIBLE
            } else
                itemTile.visibility = View.GONE
            if (nhwMenu.selectMode == NHWMenu.SelectMode.PickOne) {
                itemCheckBox.visibility = View.GONE
            }
            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(it, position, menuItem)
                if (nhwMenu.selectMode == NHWMenu.SelectMode.PickMany) {
                    menuItem.isSelected = true
                    itemCheckBox.isChecked = true
                }
                true
            }
            itemView.setOnClickListener {
                onItemClick?.invoke(it, position, menuItem)
                if (nhwMenu.selectMode == NHWMenu.SelectMode.PickMany) {
                    menuItem.isSelected = !menuItem.isSelected
                    itemCheckBox.isChecked = menuItem.isSelected
                    // 根据子item状态更新header
                    notifyItemChanged(getHeaderPosition(position))
                }
            }
            downOnQuote = false
            downQuote = null
            // 价格文本紧跟标题（同一 TextView 内，折行跟随）：
            // 按下在价格上 → 消费本次触摸（up 触发回调）；否则冒泡给 itemView 正常点击
            itemTitle.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downQuote = quoteAt(v, event)
                        downOnQuote = downQuote != null
                        downOnQuote
                    }
                    MotionEvent.ACTION_UP -> {
                        if (downOnQuote) {
                            downOnQuote = false
                            downQuote?.let { onPriceQuoteClick?.invoke(menuItem, it) }
                            downQuote = null
                            true
                        } else false
                    }
                    else -> downOnQuote
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
            itemTitle.text = buildTitle(menuItem)
            itemCheckBox.isChecked = menuItem.isSelected
        }

        /** 点击坐标落在的价格段文本（如 "sell 67"），不在价格上返回 null */
        private fun quoteAt(v: View, event: MotionEvent): String? {
            val off = (v as TextView).getOffsetForPosition(event.x, event.y)
            val text = v.text
            if (text is Spanned) {
                text.getSpans(off, off, ClickableSpan::class.java).firstOrNull()?.let { span ->
                    return text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
                }
            }
            return null
        }

        /** 标题文本：保留颜色，大括号内每个价格段（buy/sell）设为可点击链接 */
        private fun buildTitle(menuItem: NHMenuItem): CharSequence {
            val spannable = SpannableStringBuilder(menuItem.title.toSpannableString())
            if (menuItem.priceQuote.isNotEmpty()) {
                val quoteStart = spannable.length
                spannable.append(" ${menuItem.priceQuote}") // priceQuote 已带大括号（如 "{buy 267 sell 67}"）
                // 大括号内的每个价格段（如 "buy 267" / "sell 67"）分别可点击
                val content = menuItem.priceQuote.removeSurrounding("{", "}")
                val contentStart = quoteStart + 2 // 跳过 " {" 
                Regex("buy\\s*[0-9]+(?:-[0-9]+)?|sell\\s*[0-9]+(?:-[0-9]+)?").findAll(content).forEach { match ->
                    val absStart = contentStart + match.range.first
                    val absEnd = contentStart + match.range.last + 1
                    spannable.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            // 点击由 OnTouchListener 处理，这里无需动作
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            super.updateDrawState(ds) // 标准链接样式：下划线 + 链接色
                        }
                    }, absStart, absEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            return spannable
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val itemHeader : TextView = view.findViewById(R.id.item_header)
        private val itemGroupCheckBox : CheckBox = view.findViewById(R.id.item_group_checkbox)
        fun bind(position: Int, menuItem: NHMenuItem) {
            itemHeader.text = menuItem.title.toString()
            val subItems = getSubItems(position)
            if (subItems.size <= 1)
                itemGroupCheckBox.visibility = View.INVISIBLE
            else
                itemGroupCheckBox.visibility = View.VISIBLE

            menuItem.isSelected = subItems.count { it.isSelected } == subItems.size
            itemGroupCheckBox.isChecked = menuItem.isSelected
            itemView.setOnClickListener {
                if (menuItem.isSelected)
                    for (item in subItems) item.isSelected = false
                else
                    for (item in subItems) item.isSelected = true
                notifyItemRangeChanged(position+1, subItems.size)
                menuItem.isSelected = !menuItem.isSelected
                itemGroupCheckBox.isChecked = menuItem.isSelected
            }
        }
    }

    class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val itemText : TextView = view.findViewById(R.id.item_text)

        fun bind(position: Int, menuItem: NHMenuItem) {
            itemText.text = menuItem.title.toString()
            // 因为PickNone的都没有标题，导致上方空间很小，第一个元素加一个换行
            if (position == 0) {
                @SuppressLint("SetTextI18n")
                itemText.text = "\n${itemText.text}"
            }
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            OPTION -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.dialog_menu_item, viewGroup, false)
                OptionViewHolder(view)
            }

            HEADER -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.dialog_menu_item_header, viewGroup, false)
                HeaderViewHolder(view)
            }
            TEXT -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.dialog_menu_item_text, viewGroup, false)
                TextViewHolder(view)
            }
            else -> {
                throw RuntimeException("no such view type: $viewType")
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val menuItem = nhwMenu.nhMenuItems[position]
        if (nhwMenu.selectMode == NHWMenu.SelectMode.PickNone || menuItem.isHint())
            return TEXT
        return if(menuItem.isHeader()) HEADER else OPTION
    }
    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val menuItem = nhwMenu.nhMenuItems[position]

        when(getItemViewType(position)) {
            OPTION -> {
                (viewHolder as OptionViewHolder).bind(position, menuItem)
            }
            HEADER -> {
                (viewHolder as HeaderViewHolder).bind(position, menuItem)
            }
            TEXT -> {
                (viewHolder as TextViewHolder).bind(position, menuItem)
            }
        }

    }

    override fun getItemCount() = nhwMenu.nhMenuItems.size

    private fun getSubItems(headerPos: Int):List<NHMenuItem> {
        if (!nhwMenu.nhMenuItems[headerPos].isHeader()
            || headerPos == nhwMenu.nhMenuItems.size -1
            || nhwMenu.selectMode == NHWMenu.SelectMode.PickOne
            ) {
            return emptyList()
        }
        val subList = mutableListOf<NHMenuItem>()
        for (i in headerPos + 1 until nhwMenu.nhMenuItems.size) {
            val item = nhwMenu.nhMenuItems[i]
            if (!item.isHeader())
                subList.add(item)
            else
                break
        }
        return subList
    }

    private fun getHeaderPosition(itemPos: Int):Int {
        for (i in (0.. itemPos).reversed()) {
            if (nhwMenu.nhMenuItems[i].isHeader())
                return i
        }
        return itemPos
    }

    companion object {
        private const val OPTION = 0
        private const val HEADER = 1
        private const val TEXT = 2
    }
}
