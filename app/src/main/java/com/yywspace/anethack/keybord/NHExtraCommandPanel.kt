package com.yywspace.anethack.keybord

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.yywspace.anethack.AssetsUtils
import com.yywspace.anethack.R
import com.yywspace.anethack.SharedPreferencesUtils
import com.yywspace.anethack.extensions.show

/**
 * "更多命令"面板：和命令栏一样横向滚动，但配置完全独立（偏好里的 commandPanelExtra）。
 *
 * 配置是二维 JSON 数组：外层每个数组就是一组命令，界面上每组一行，组内放不下自动换行，
 * 不按数量自动折行。点击按钮触发 [onCommand]，长按可编辑或删除；新增只能在设置里改配置。
 */
class NHExtraCommandPanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    /** 点击按钮：把命令交给调用方发送 */
    var onCommand: ((String) -> Unit)? = null
    /** 弹窗是否需要按沉浸式显示 */
    var immersiveProvider: (() -> Boolean)? = null


    private companion object {
        /** 默认命令所在的 asset */
        private const val DEFAULT_FILE = "commands/extra_command_panel.json"
    }

    private val rows: LinearLayout
    private lateinit var prefs: SharedPreferencesUtils

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_extra_command_panel, this, true)
        rows = findViewById(R.id.extra_command_rows)
    }



    /**
     * 切换深浅色后重新上色。
     *
     * 格子里的 `@color/...` 是在 inflate 时解析的，Activity 没有重建就还是旧颜色，
     * 所以这里重设面板背景并重建一遍按钮。
     */
    fun refreshTheme() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        reload()
    }

    /**
     * 重新读取配置并生成按钮。
     *
     * 配置的每个内层数组对应一行：一行放进一个横向滚动视图里，
     * 组与组之间纵向排列 —— 这样既能分组，也不受屏幕宽度限制。
     */
    fun reload() {
        prefs = SharedPreferencesUtils(context)
        rows.removeAllViews()
        extraRows().forEachIndexed { index, entries ->
            val scroller = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_NEVER
            }
            val line = LinearLayout(context).apply { orientation = HORIZONTAL }
            entries.forEach { entry ->
                val item = LayoutInflater.from(context)
                    .inflate(R.layout.item_extra_command, line, false) as TextView
                item.text = entry.label
                item.setOnClickListener { onCommand?.invoke(entry.command) }
                item.setOnLongClickListener {
                    showEditor(entry)
                    true
                }
                line.addView(item)
            }
            scroller.addView(line)
            // 组与组之间留和格子一样的间距
            if (index > 0) {
                scroller.layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.command_item_spacing)
                }
            }
            rows.addView(scroller)
        }
    }

    /** 读配置里的分组；为空时用默认分组，并把默认值立即落盘，避免后续编辑只保存一条 */
    private fun extraRows(): List<List<CommandEntry>> {
        val raw = prefs.commandPanelExtra.orEmpty()
        if (raw.isBlank()) {
            val defaults = defaultRows()
            prefs.commandPanelExtra = toJson(defaults)
            return defaults
        }
        return parseRows(raw).takeIf { it.isNotEmpty() } ?: defaultRows()
    }

    /** 解析偏好里的分组配置；解析失败返回空列表 */
    private fun parseRows(json: String): List<List<CommandEntry>> =
        runCatching {
            CommandEntry.gson.fromJson(json, Array<Array<CommandEntry>>::class.java)
                ?.map { it.toList() }
        }.getOrNull().orEmpty()

    /** 写回分组配置：字段固定为 label → command */
    private fun toJson(rows: List<List<CommandEntry>>): String = CommandEntry.gson.toJson(rows)

    private fun saveRows(newRows: List<List<CommandEntry>>) {
        prefs.commandPanelExtra = toJson(newRows)
        reload()
    }

    /** 修改：找到所在分组后替换 */
    private fun replace(oldEntry: CommandEntry, newEntry: CommandEntry) {
        val newRows = extraRows().map { row ->
            row.map { if (it == oldEntry) newEntry else it }
        }
        saveRows(newRows)
    }

    /** 删除：从组里移除，空组一并去掉 */
    private fun delete(entry: CommandEntry) {
        saveRows(extraRows().map { row -> row.filter { it != entry } }.filter { it.isNotEmpty() })
    }

    /** 编辑 / 删除一条命令（只能在设置里新增，所以这里不处理 entry == null） */
    private fun showEditor(entry: CommandEntry) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_command_edit, null)
        val labelInput = view.findViewById<EditText>(R.id.command_edit_label)
        val valueInput = view.findViewById<EditText>(R.id.command_edit_value)
        labelInput.setText(entry.label)
        valueInput.setText(entry.command)
        AlertDialog.Builder(context)
            .setTitle(R.string.command_edit_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val label = labelInput.text.toString().trim()
                val command = valueInput.text.toString().trim()
                if (label.isNotEmpty() && command.isNotEmpty())
                    replace(entry, CommandEntry(label, command))
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .setNeutralButton(R.string.command_delete) { _, _ -> delete(entry) }
            .show(immersiveProvider?.invoke() ?: false)
    }

    /** 默认分组，来自 assets/commands/extra_command_panel.json */
    private fun readDefaultRows(context: Context): List<List<CommandEntry>> =
        AssetsUtils.readJson(
            context, DEFAULT_FILE, Array<Array<CommandEntry>>::class.java
        )?.map { it.toList() }.orEmpty()

    /** 首次打开时填充的示例：来自 assets/commands/extra_command_panel.json，外层是分组 */
    private fun defaultRows(): List<List<CommandEntry>> = readDefaultRows(context)
}
