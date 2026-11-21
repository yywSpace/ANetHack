package com.yywspace.anethack.window

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHMenuCommand
import com.yywspace.anethack.entity.NHMenuItem
import com.yywspace.anethack.entity.NHSpell

/**
 * 法术专用表格弹窗。
 *
 * TableLayout 统一列宽，整行可以共用水波纹。
 */
object NHSpellMenu {
    const val TAG = "spell_menu"

    /** 展示法术弹窗并等待核心返回选择结果。 */
    fun select(nh: NetHack, menu: NHWMenu, how: Int): LongArray {
        val selectMode = NHWMenu.SelectMode.fromInt(how)
        val title = menu.title
        val items = menu.nhMenuItems.toList()

        // 自动化命令：和普通菜单共用同一套匹配和提示逻辑
        NHWMenu.resolveCommand(
            nh, items, nh.command.findMenuCommand(), selectMode
        )?.let { return it }
        nh.runOnUi { _, context ->
            val view = View.inflate(context, R.layout.dialog_spell_menu, null)
            view.findViewById<TextView>(R.id.spell_title).apply {
                text = title
                visibility = if (title.isBlank()) View.GONE else View.VISIBLE
            }
            val table = view.findViewById<TableLayout>(R.id.spell_table)
            buildTable(table, items) { item ->
                if (selectMode != NHWMenu.SelectMode.PickNone) {
                    nh.dialogs.dismiss(TAG)
                    nh.command.sendCommand(
                        NHMenuCommand(
                            item.accelerator,
                            mutableListOf(item.identifier, item.selectedCount),
                            menu.wid
                        )
                    )
                }
            }
            view.findViewById<View>(R.id.spell_cancel).setOnClickListener {
                nh.dialogs.dismiss(TAG)
                nh.command.sendCommand(
                    NHMenuCommand(27.toChar(), mutableListOf(-1), menu.wid)
                )
            }
            nh.dialogs.show(TAG, view)
        }

        var selected = nh.command.waitForAnyCommand<NHMenuCommand>()
        while (selected.wid != menu.wid)
            selected = nh.command.waitForAnyCommand<NHMenuCommand>()
        return selected.selectedItems.toLongArray()
    }

    private fun buildTable(
        table: TableLayout,
        items: List<NHMenuItem>,
        onClick: (NHMenuItem) -> Unit
    ) {
        val context = table.context
        table.removeAllViews()
        val header = items.firstOrNull { it.isHeader() }?.title?.value.orEmpty()
        table.addView(buildHeader(context, NHSpell.headerLabels(header)))

        val ripple = TypedValue().also {
            context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, it, true
            )
        }
        items.filterNot { it.isHeader() }.forEach { item ->
            table.addView(buildRow(context, item, ripple.resourceId, onClick))
        }
    }

    private fun buildHeader(context: Context, labels: List<String>): TableRow {
        return TableRow(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                text(context, labels.getOrNull(0).orEmpty())
                    .apply {
                        gravity = Gravity.CENTER
                        layoutParams = TableRow.LayoutParams(
                            TableRow.LayoutParams.WRAP_CONTENT,
                            TableRow.LayoutParams.WRAP_CONTENT
                        ).apply {
                            span = 2
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    }
            )
            addView(columnCell(context, labels.getOrNull(1).orEmpty()))
            addView(columnCell(context, labels.getOrNull(2).orEmpty()))
            addView(columnCell(context, labels.getOrNull(3).orEmpty()))
        }
    }

    private fun buildRow(
        context: Context,
        item: NHMenuItem,
        rippleRes: Int,
        onClick: (NHMenuItem) -> Unit
    ): TableRow {
        val content = NHSpell.parse(spellText(item))
        return TableRow(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 40)
            setBackgroundResource(rippleRes)
            isClickable = true
            setOnClickListener { onClick(item) }

            addView(
                text(context, item.accelerator.toString()).apply {
                    layoutParams = cellParams(dp(context, 18))
                }
            )
            addView(nameColumn(context, item, content))
            addView(columnCell(context, content?.level.orEmpty()))
            addView(
                columnCell(context, content?.category.orEmpty()).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            )
            addView(columnCell(context, content?.fail.orEmpty()))
        }
    }

    /** 子标题保存的是被剥掉的 (gone)，解析前拼回标题。 */
    private fun spellText(item: NHMenuItem): String = buildString {
        append(item.title.value)
        if (item.hasSubtitle())
            append(" ").append(item.subtitle)
    }

    private fun nameColumn(
        context: Context,
        item: NHMenuItem,
        content: NHSpell?
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 2))
            layoutParams = cellParams(TableRow.LayoutParams.WRAP_CONTENT)

            addView(
                text(context, content?.name ?: item.title.toString()).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            )

            val parts = buildList {
                content?.retention?.takeIf { it.isNotEmpty() }?.let(::add)
                content?.turns?.let(::add)
            }
            if (parts.isNotEmpty()) {
                addView(
                    text(context, parts.joinToString("  ")).apply {
                        textSize = 12f
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setTextColor(
                            ContextCompat.getColor(context, R.color.app_text_secondary)
                        )
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                )
            }
        }
    }

    private fun columnCell(context: Context, value: String): TextView =
        text(context, value).apply {
            layoutParams = cellParams(TableRow.LayoutParams.MATCH_PARENT)
        }

    private fun text(
        context: Context,
        value: String
    ): TextView = TextView(context).apply {
        text = value
        gravity = Gravity.CENTER
    }

    private fun cellParams(width: Int): TableRow.LayoutParams =
        TableRow.LayoutParams(width, TableRow.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
