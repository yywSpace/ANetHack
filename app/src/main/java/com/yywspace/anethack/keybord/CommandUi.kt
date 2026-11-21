package com.yywspace.anethack.keybord

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.yywspace.anethack.R
import com.yywspace.anethack.window.NHDialog
import com.yywspace.anethack.window.NHDialogButton

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
     * 一行命令按钮 + 横向滚动容器。
     *
     * 点击/长按的行为由调用方决定；编辑页可以在这之后给每个按钮挂拖拽监听。
     */
    fun scrolledRow(
        context: Context,
        entries: List<CommandEntry>,
        bar: Boolean,
        onClick: (CommandEntry, Int) -> Unit = { _, _ -> },
        onLongClick: (CommandEntry, Int, TextView) -> Boolean = { _, _, _ -> false },
        /** 不为 null 时，在行尾自动追加一个 "+" */
        onAdd: (() -> Unit)? = null
    ): HorizontalScrollView {
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        entries.forEachIndexed { index, entry ->
            val chip = chip(context, line, bar)
            chip.text = entry.label
            chip.setOnClickListener { onClick(entry, index) }
            chip.setOnLongClickListener { onLongClick(entry, index, chip) }
            line.addView(chip)
        }
        if (onAdd != null) {
            val add = chip(context, line, bar)
            add.text = context.getString(R.string.command_bar_add)
            add.setOnClickListener { onAdd() }
            line.addView(add)
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(line)
        }
    }

    /**
     * 新增或编辑一条命令。
     *
     * @param entry 为 null 时表示新增（输入框留空）
     * @param onDelete 非空时多显示一个"删除"按钮
     */
    fun editDialog(
        context: Context,
        entry: CommandEntry?,
        immersive: Boolean = false,
        onSave: (CommandEntry) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        val root = FrameLayout(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_command_edit, root, false)
        val labelInput = view.findViewById<EditText>(R.id.command_edit_label)
        val valueInput = view.findViewById<EditText>(R.id.command_edit_value)
        if (entry != null) {
            labelInput.setText(entry.label)
            valueInput.setText(entry.command)
        }

        lateinit var dialog: NHDialog
        val deleteButton = onDelete?.let {
            NHDialogButton(
                text = context.getString(R.string.command_delete),
                dismissOnClick = false,
                onClick = { confirmDelete(context, entry, immersive, onDelete, dialog) }
            )
        }
        dialog = NHDialog(
            context = context,
            content = view,
            title = context.getString(
                if (entry == null) R.string.command_add_title else R.string.command_edit_title
            ),
            leftButton = NHDialogButton(context.getString(R.string.dialog_cancel)),
            middleButton = deleteButton,
            rightButton = NHDialogButton(
                text = context.getString(R.string.dialog_confirm),
                dismissOnClick = false,
                onClick = {
                    val label = labelInput.text.toString().trim()
                    val command = valueInput.text.toString().trim()
                    if (label.isEmpty() || command.isEmpty()) {
                        Toast.makeText(
                            context, R.string.command_edit_invalid, Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onSave(CommandEntry(label, command))
                        dialog.dismiss()
                    }
                }
            ),
            immersive = immersive
        )
        dialog.show()
    }

    /** 删除前确认；确认后关闭编辑弹窗。 */
    private fun confirmDelete(
        context: Context,
        entry: CommandEntry?,
        immersive: Boolean,
        onDelete: () -> Unit,
        editor: NHDialog
    ) {
        val message = entry?.let {
            context.getString(R.string.command_delete_confirm_msg, it.label, it.command)
        }.orEmpty()
        NHDialog(
            context = context,
            content = TextView(context).apply {
                text = message
                setPadding(24, 8, 24, 8)
            },
            title = context.getString(R.string.command_delete_confirm),
            leftButton = NHDialogButton(context.getString(R.string.dialog_cancel)),
            rightButton = NHDialogButton(
                text = context.getString(R.string.dialog_confirm),
                onClick = {
                    onDelete()
                    editor.dismiss()
                }
            ),
            immersive = immersive
        ).show()
    }
}
