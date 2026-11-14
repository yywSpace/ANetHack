package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NHTileSet
import com.yywspace.anethack.R
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.entity.NHMenuItem
import com.yywspace.anethack.gamedata.Item


class NHWMenuAdapter(private val nhwMenu: NHWMenu, private val tileSet:NHTileSet) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var onItemClick:((view:View, index:Int, menuItem: NHMenuItem)->Unit)? = null
    var onItemLongClick:((view:View, index:Int, menuItem: NHMenuItem)->Unit)? = null
    /** 点击菜单项的大括号价格文本（如 "sell 67"）回调，传入菜单项和点击的价格段 */
    var onPriceQuoteClick:((menuItem: NHMenuItem, quote: String)->Unit)? = null
    /** 点击菜单标题里的物品名，打开物品弹窗 */
    var onItemNameClick:((item: Item)->Unit)? = null

    inner class OptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val itemAcc: TextView = view.findViewById(R.id.item_accelerator)
        private val itemSelectAmount: TextView = view.findViewById(R.id.item_select_amount)
        private val itemTitle : TextView = view.findViewById(R.id.item_title)
        private val itemSubtitle : TextView = view.findViewById(R.id.item_subtitle)
        private val itemCheckBox:CheckBox = view.findViewById(R.id.item_checkbox)
        private val itemTile:ImageView = view.findViewById(R.id.item_tile)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(position: Int, menuItem: NHMenuItem) {
            if (!tileSet.isTTY() && (menuItem.flags and NHTileSet.MG_UNEXPL) == 0) {
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
            MenuLink.attach(
                itemTitle,
                onQuoteClick = { quote -> onPriceQuoteClick?.invoke(menuItem, quote) },
                onItemClick = { item -> onItemNameClick?.invoke(item) }
            )

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

        /** 标题文本：保留颜色，大括号内每个价格段（buy/sell）设为可点击链接 */
        private fun buildTitle(menuItem: NHMenuItem): CharSequence {
            val spannable = SpannableStringBuilder(menuItem.title.toSpannableString())
            // 游戏没给颜色（NO_COLOR）时用主题里的菜单文字色：
            // 浅色下是深灰、深色下是浅灰，不再统一折成 CLR_BLACK
            if (menuItem.title.nhColor == NHColor.NO_COLOR) {
                val color = ContextCompat.getColor(itemView.context, R.color.menu_item_text)
                spannable.setSpan(
                    ForegroundColorSpan(color), 0, spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // 先给标题里的物品名加链接，再追加价格段；两者互不重叠
            if (nhwMenu.itemLinkEnabled)
                MenuLink.highlightItems(itemView.context, spannable)

            if (menuItem.priceQuote.isNotEmpty()) {
                spannable.append(" ${menuItem.priceQuote}") // priceQuote 已带大括号（如 "{buy 267 sell 67}"）
                // 价格鉴定关掉时只显示原文，不加链接样式（避免出现下划线却点不动）
                if (nhwMenu.priceIdEnabled)
                    MenuLink.highlightPriceQuote(itemView.context, spannable, menuItem.priceQuote)
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
