package com.yywspace.anethack.keybord

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.yywspace.anethack.R
import com.yywspace.anethack.extensions.show

/**
 * 命令按钮的公共 UI：按钮、横向滚动行、编辑弹窗。
 *
 * 命令栏、扩展面板和设置里的编辑页都复用这里的实现，避免三份重复代码。
 */
object CommandUi {

    /** 按样式创建一个命令按钮；[bar] = true 用命令栏样式，否则用扩展面板样式 */
    fun chip(context: Context, parent: ViewGroup, bar: Boolean): TextView =
        LayoutInflater.from(context)
            .inflate(if (bar) R.layout.item_command else R.layout.item_extra_command, parent, false)
                as TextView

    /**
     * 生成一行命令按钮（不含行尾的 "+"）。
     *
     * 点击/长按的行为由调用方决定；编辑页可以在这之后给每个按钮挂拖拽监听。
     */
    fun row(
        context: Context,
        entries: List<CommandEntry>,
        bar: Boolean,
        onClick: (CommandEntry, Int) -> Unit = { _, _ -> },
        onLongClick: (CommandEntry, Int, TextView) -> Boolean = { _, _, _ -> false },
        /** 不为 null 时，在行尾自动追加一个 "+" */
        onAdd: (() -> Unit)? = null
    ): LinearLayout {
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        entries.forEachIndexed { index, entry ->
            val chip = chip(context, line, bar)
            chip.text = entry.label
            chip.setOnClickListener { onClick(entry, index) }
            chip.setOnLongClickListener { onLongClick(entry, index, chip) }
            line.addView(chip)
        }
        if (onAdd != null) {
            line.addView(addChip(context, line, bar, onAdd))
        }
        return line
    }

    /** 行尾的 "+"：点击新增一条命令 */
    private fun addChip(context: Context, parent: ViewGroup, bar: Boolean, onClick: () -> Unit): TextView {
        val chip = chip(context, parent, bar)
        chip.text = "+"
        chip.setOnClickListener { onClick() }
        return chip
    }

    /** 把一行按钮包进横向滚动容器 */
    fun scroller(context: Context, content: View): HorizontalScrollView =
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }

    /**
     * 新增或编辑一条命令。
     *
     * @param entry 为 null 时表示新增（输入框留空）
     * @param immersive 弹窗是否按沉浸式显示
     * @param onDelete 非空时多显示一个"删除"按钮
     */
    fun editDialog(
        context: Context,
        immersive: Boolean,
        entry: CommandEntry?,
        onSave: (CommandEntry) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_command_edit, null)
        val labelInput = view.findViewById<EditText>(R.id.command_edit_label)
        val valueInput = view.findViewById<EditText>(R.id.command_edit_value)
        if (entry != null) {
            labelInput.setText(entry.label)
            valueInput.setText(entry.command)
        }
        val builder = AlertDialog.Builder(context)
            .setTitle(if (entry == null) R.string.command_add_title else R.string.command_edit_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.dialog_cancel, null)
        if (onDelete != null)
            builder.setNeutralButton(R.string.command_delete, null)
        val dialog = builder.show(immersive)
        // 删除是不可撤销的，先问一句
        if (onDelete != null)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle(R.string.command_delete_confirm)
                    .setMessage(
                        entry?.let {
                            context.getString(R.string.command_delete_confirm_msg, it.label, it.command)
                        }.orEmpty()
                    )
                    .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                        onDelete()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show(immersive)
            }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val label = labelInput.text.toString().trim()
            val command = valueInput.text.toString().trim()
            if (label.isEmpty() || command.isEmpty()) {
                Toast.makeText(context, R.string.command_edit_invalid, Toast.LENGTH_SHORT).show()
            } else {
                onSave(CommandEntry(label, command))
                dialog.dismiss()
            }
        }
    }
}
