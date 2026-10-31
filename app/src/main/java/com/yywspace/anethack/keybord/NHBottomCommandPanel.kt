package com.yywspace.anethack.keybord

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import androidx.core.content.ContextCompat
import com.yywspace.anethack.AssetsUtils
import com.yywspace.anethack.R
import com.yywspace.anethack.extensions.show

/** 命令栏里的一条命令：按钮上显示的文字 + 发送的指令 */
data class CommandEntry(val label: String, val command: String) {
    companion object {
        /** 序列化时固定写成 label → command 的顺序，不依赖反射 */
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(CommandEntry::class.java, object : JsonSerializer<CommandEntry> {
                override fun serialize(
                    src: CommandEntry,
                    type: java.lang.reflect.Type,
                    context: JsonSerializationContext
                ): JsonObject = JsonObject().apply {
                    addProperty("label", src.label)
                    addProperty("command", src.command)
                }
            })
            .create()
    }
}

/**
 * 底部命令栏：命令按钮一行横向滚动，右侧固定键盘、更多命令、设置三个图标。
 * 点击按钮触发 [onCommand]，长按按钮弹出编辑框（改显示名和命令），
 * 最右侧的 "+" 用来新增；编辑后通过 [onCommandsChanged] 交回给调用方保存。
 *
 * 配置以 JSON 数组保存（[CommandEntry] 的序列）
 */
class NHBottomCommandPanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    /** 固定按钮：键盘、更多命令、设置 */
    var onCommand: ((String) -> Unit)? = null
    /** 编辑完成后的新配置串，由调用方写入偏好 */
    var onCommandsChanged: ((String) -> Unit)? = null
    /** 弹窗是否需要按沉浸式显示 */
    var immersiveProvider: (() -> Boolean)? = null

    private val commandList: LinearLayout
    private val divider: View
    private val keyboardSwitch: ImageView
    private val settingsSwitch: ImageView

    /** 当前配置里的条目 */

    private var entries = mutableListOf<CommandEntry>()


    init {
        orientation = HORIZONTAL
        isClickable = true
        // 命令栏整体的左右留白（含右侧固定的键盘/设置图标）
        val edge = resources.getDimensionPixelSize(R.dimen.command_bar_panel_padding)
        setPaddingRelative(edge, 0, edge, 0)
        LayoutInflater.from(context).inflate(R.layout.view_bottom_command_panel, this, true)
        commandList = findViewById(R.id.command_list)
        divider = findViewById(R.id.command_divider)
        keyboardSwitch = findViewById(R.id.keyboard_switch)
        settingsSwitch = findViewById(R.id.settings_switch)
        keyboardSwitch.setOnClickListener { onCommand?.invoke(KEYBOARD_COMMAND) }
        settingsSwitch.setOnClickListener { onCommand?.invoke(SETTING_COMMAND) }
    }

    /** 用配置串刷新命令按钮；空串时回落到内置默认值 */
    fun setCommands(commands: String?) {
        val text = commands.orEmpty()
        entries = (if (text.isBlank()) defaultCommands(context) else parseEntries(text))
            .filter { it.command !in pinnedCommands }
            .toMutableList()
        commandList.removeAllViews()
        entries.forEach { entry ->
            val item = LayoutInflater.from(context)
                .inflate(R.layout.item_command, commandList, false) as TextView
            item.text = entry.label
            item.setOnClickListener { onCommand?.invoke(entry.command) }
            item.setOnLongClickListener {
                showEditDialog(entry)
                true
            }
            commandList.addView(item)
        }
        // 末尾固定一个"+"按钮，用来新增命令
        val addItem = LayoutInflater.from(context)
            .inflate(R.layout.item_command, commandList, false) as TextView
        addItem.text = context.getString(R.string.command_bar_add)
        addItem.setOnClickListener { showEditDialog(null) }
        commandList.addView(addItem)
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

    /** 切换深浅色后重新上色：背景、分隔线、切换面板/设置图标 */
    fun refreshTheme() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.app_divider))
        val tint = ContextCompat.getColor(context, R.color.app_text)
        keyboardSwitch.imageTintList = ColorStateList.valueOf(tint)
        settingsSwitch.imageTintList = ColorStateList.valueOf(tint)
    }


    /** 写回配置：字段固定为 label → command */
    private fun toJson(entries: List<CommandEntry>): String = CommandEntry.gson.toJson(entries)

    /** 解析配置：JSON 数组；解析不出来时回落到内置默认值 */
    private fun parseEntries(text: String): List<CommandEntry> =
        runCatching {
            parseEntries(text)
        }.getOrNull()?.toList() ?: defaultCommands(context)

    /** 编辑已有条目；[entry] 为 null 时表示新增（输入框留空） */
    private fun showEditDialog(entry: CommandEntry?) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_command_edit, null)
        val labelInput = view.findViewById<EditText>(R.id.command_edit_label)
        val valueInput = view.findViewById<EditText>(R.id.command_edit_value)
        if (entry != null) {
            labelInput.setText(entry.label)
            valueInput.setText(entry.command)
        }
        val immersive = immersiveProvider?.invoke() ?: false
        val builder = AlertDialog.Builder(context)
            .setTitle(if (entry == null) R.string.command_add_title else R.string.command_edit_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.dialog_cancel, null)
        // 编辑已有命令时多给一个"删除"
        if (entry != null)
            builder.setNeutralButton(R.string.command_delete) { _, _ -> deleteEntry(entry) }
        val dialog = builder.show(immersive)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val label = labelInput.text.toString().trim()
            val value = valueInput.text.toString().trim()
            if (label.isEmpty() || value.isEmpty()) {
                Toast.makeText(context, R.string.command_edit_invalid, Toast.LENGTH_SHORT).show()
            } else {
                saveEntry(entry, CommandEntry(label, value))
                dialog.dismiss()
            }
        }
    }

    /** 删除条目并通知调用方保存 */
    private fun deleteEntry(entry: CommandEntry) {
        entries.remove(entry)
        val text = toJson(entries)
        onCommandsChanged?.invoke(text)
        setCommands(text)
    }

    /** 替换（entry 非空）或追加（entry 为空）条目，然后通知调用方保存 */
    private fun saveEntry(oldEntry: CommandEntry?, newEntry: CommandEntry) {
        if (oldEntry == null) {
            entries.add(newEntry)
        } else {
            val index = entries.indexOf(oldEntry)
            if (index >= 0) entries[index] = newEntry else entries.add(newEntry)
        }
        val text = toJson(entries)
        onCommandsChanged?.invoke(text)
        setCommands(text)
    }

    companion object {
        const val KEYBOARD_COMMAND = "Keyboard"
        const val SETTING_COMMAND = "Setting"
        /** 固定在右侧、不参与编辑和滚动的条目 */
        val pinnedCommands = setOf(KEYBOARD_COMMAND, SETTING_COMMAND)

        /** 默认命令所在的 asset */
        private const val DEFAULT_FILE = "commands/bottom_command_panel.json"

        /** 默认命令，来自 assets/commands/bottom_command_panel.json */
        /** 默认命令，来自 assets/commands/bottom_command_panel.json */
        fun defaultCommands(context: Context): List<CommandEntry> =
            AssetsUtils.readJson(
                context, DEFAULT_FILE, Array<CommandEntry>::class.java
            )?.toList().orEmpty()
    }
}
