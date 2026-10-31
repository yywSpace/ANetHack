package com.yywspace.anethack.keybord

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.yywspace.anethack.R

class NHBottomCommandBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    var onCommand: ((String) -> Unit)? = null
    /** 弹窗是否需要按沉浸式显示 */
    var immersiveProvider: (() -> Boolean)? = null

    private val commandList: LinearLayout
    private val divider: View
    private val keyboardSwitch: ImageView
    private val settingsSwitch: ImageView

    private var entries = mutableListOf<CommandEntry>()

    init {
        orientation = HORIZONTAL
        isClickable = true
        // 命令栏整体的左右留白（含右侧固定的键盘/设置图标）
        val edge = resources.getDimensionPixelSize(R.dimen.command_bar_panel_padding)
        setPaddingRelative(edge, 0, edge, 0)
        LayoutInflater.from(context).inflate(R.layout.view_bottom_command_bar, this, true)
        commandList = findViewById(R.id.command_list)
        divider = findViewById(R.id.command_divider)
        keyboardSwitch = findViewById(R.id.keyboard_switch)
        settingsSwitch = findViewById(R.id.settings_switch)
        keyboardSwitch.setOnClickListener { onCommand?.invoke(KEYBOARD_COMMAND) }
        settingsSwitch.setOnClickListener { onCommand?.invoke(SETTING_COMMAND) }
    }

    /** 重新从本地配置文件读命令并渲染（默认值由 CommandStore 负责从 assets 复制） */
    fun reloadCommands() {
        entries = CommandStore.readBar(context).toMutableList()
        commandList.removeAllViews()
        val line = CommandUi.row(
            context = context,
            entries = entries,
            bar = true,
            onClick = { entry, _ -> onCommand?.invoke(entry.command) },
            onLongClick = { entry, _, _ ->
                showEditDialog(entry)
                true
            }
        )
        // 末尾固定一个"+"按钮，用来新增命令
        val addItem = CommandUi.chip(context, line, bar = true)
        addItem.text = context.getString(R.string.command_bar_add)
        addItem.setOnClickListener { showEditDialog(null) }
        line.addView(addItem)
        commandList.addView(line)
    }

    /** 切换面板按钮当前显示的图标：键盘图标或"更多命令"图标 */
    fun setSwitchIcon(isKeyboardIcon: Boolean) {
        keyboardSwitch.setImageResource(
            if (isKeyboardIcon) R.drawable.ic_keyboard else R.drawable.ic_grid
        )
        keyboardSwitch.contentDescription = context.getString(
            if (isKeyboardIcon) R.string.command_bar_keyboard_switch else R.string.command_bar_extra
        )
    }

    /**
     * 切换深浅色后重新上色，并重建按钮。
     *
     * 按钮里的 `@color/...` 是 inflate 时解析的，Activity 没重建就还是旧颜色，
     * 所以上色和重建放在一起，调用方只需要调这一个方法。
     */
    fun refreshTheme() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.app_divider))
        val tint = ContextCompat.getColor(context, R.color.app_text)
        keyboardSwitch.imageTintList = ColorStateList.valueOf(tint)
        settingsSwitch.imageTintList = ColorStateList.valueOf(tint)
        reloadCommands()
    }

    /** 编辑已有条目；[entry] 为 null 时表示新增（输入框留空） */
    private fun showEditDialog(entry: CommandEntry?) {
        CommandUi.editDialog(
            context = context,
            immersive = immersiveProvider?.invoke() ?: false,
            entry = entry,
            onSave = { saveEntry(entry, it) },
            onDelete = if (entry == null) null else ({ deleteEntry(entry) })
        )
    }

    /** 删除条目并通知调用方保存 */
    private fun deleteEntry(entry: CommandEntry) {
        entries.remove(entry)
        CommandStore.writeBar(context, entries)
        reloadCommands()
    }

    /** 替换（entry 非空）或追加（entry 为空）条目，然后通知调用方保存 */
    private fun saveEntry(oldEntry: CommandEntry?, newEntry: CommandEntry) {
        if (oldEntry == null) {
            entries.add(newEntry)
        } else {
            val index = entries.indexOf(oldEntry)
            if (index >= 0) entries[index] = newEntry else entries.add(newEntry)
        }
        CommandStore.writeBar(context, entries)
        reloadCommands()
    }

    companion object {
        /** 图标触发的内部命令：键盘/面板切换、打开设置 */
        const val KEYBOARD_COMMAND = "Keyboard"
        const val SETTING_COMMAND = "Setting"
    }
}
