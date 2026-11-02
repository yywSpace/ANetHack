package com.yywspace.anethack.keybord

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.yywspace.anethack.R

/**
 * “更多命令”面板：和命令栏一样横向滚动，但配置完全独立（本地文件里的 extra_command_panel.json）。
 *
 * 配置是 JSON 数组，每项是 { enabled, commands }：enabled 为 false 的整行不显示。
 * 点击按钮触发 [onCommand]，长按可编辑或删除；新增和启用开关在设置里的编辑页。
 */
class NHExtraCommandPanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    /** 点击按钮：把命令交给调用方发送 */
    var onCommand: ((String) -> Unit)? = null
    /** 弹窗是否需要按沉浸式显示 */
    var immersiveProvider: (() -> Boolean)? = null

    /** 从配置文件读出来的全部行（含未启用的），编辑时按它整体写回 */
    private var allRows: List<CommandRow> = emptyList()

    private val rows: LinearLayout

    init {
        orientation = VERTICAL
        // 内容容器只是逐行放按钮，直接代码创建，不必单独写布局文件
        rows = LinearLayout(context).apply { orientation = VERTICAL }
        addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * 切换深浅色后重新上色，并重建按钮。
     *
     * 格子里的 `@color/...` 是 inflate 时解析的，Activity 没重建就还是旧颜色，
     * 所以上色和重建放在一起，调用方只调这一个方法。
     */
    fun refreshTheme() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        reload()
    }

    /**
     * 重新读取配置并生成按钮；只渲染启用的行。
     *
     * 每一行放进一个横向滚动视图里，组与组之间纵向排列 —— 这样既能分组，也不受屏幕宽度限制。
     */
    fun reload() {
        rows.removeAllViews()
        allRows = CommandStore.readExtra(context)
        allRows.filter { it.enabled }.forEachIndexed { _, row ->
            val scroller = CommandUi.scrolledRow(
                context = context,
                entries = row.commands,
                bar = false,
                onClick = { entry, _ ->
                    KeyboardVibrate.vibrate()
                    onCommand?.invoke(entry.command)
                },
                onLongClick = { entry, _, _ ->
                    showEditor(entry)
                    true
                },
                onAdd = {
                    KeyboardVibrate.vibrate()
                    showAddEditor(row)
                }
            )
            val gap = resources.getDimensionPixelSize(R.dimen.command_item_gap)
            val params = LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, gap, 0, gap) }
            rows.addView(scroller, params)
        }
    }

    private fun saveRows(newRows: List<CommandRow>) {
        CommandStore.writeExtra(context, newRows)
        reload()
    }

    /** 修改：在所有行里替换同一条命令 */
    private fun replace(oldEntry: CommandEntry, newEntry: CommandEntry) {
        saveRows(allRows.map { row ->
            row.copy(commands = row.commands.map { if (it == oldEntry) newEntry else it }.toMutableList())
        })
    }

    /** 删除：从所在行移除，空行一并去掉 */
    private fun delete(entry: CommandEntry) {
        saveRows(
            allRows.map { row -> row.copy(commands = row.commands.filter { it != entry }.toMutableList()) }
                .filter { it.commands.isNotEmpty() }
        )
    }

    /** 在行尾新增一条命令 */
    private fun showAddEditor(row: CommandRow) {
        CommandUi.editDialog(
            context = context,
            immersive = immersiveProvider?.invoke() ?: false,
            entry = null,
            onSave = { entry ->
                saveRows(
                    allRows.map {
                        if (it === row) it.copy(commands = (it.commands + entry).toMutableList()) else it
                    }
                )
            }
        )
    }

    /** 编辑 / 删除一条命令（只能在设置里新增，所以这里不处理 entry == null） */
    private fun showEditor(entry: CommandEntry) {
        CommandUi.editDialog(
            context = context,
            immersive = immersiveProvider?.invoke() ?: false,
            entry = entry,
            onSave = { replace(entry, it) },
            onDelete = { delete(entry) }
        )
    }
}
