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
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.yywspace.anethack.R
import com.yywspace.anethack.extensions.show

/** 命令栏里的一条命令：发送的指令 + 按钮上显示的文字 */
data class CommandEntry(val value: String, val label: String)

/**
 * 底部命令栏：命令按钮一行横向滚动，键盘/设置固定在最右侧。
 * 点击按钮触发 [onCommand]，长按按钮弹出编辑框（改显示名和命令），
 * 最右侧的 "+" 用来新增；编辑后通过 [onCommandsChanged] 交回给调用方保存。
 *
 * 配置以 JSON 数组保存（[CommandEntry] 的序列）
 */
class NHBottomCommandPanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    /** 点击按钮（含固定的键盘、设置） */
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

    private val gson = Gson()

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
        entries = (if (text.isBlank()) defaultCommands else parseEntries(text))
            .filter { it.value !in pinnedCommands }
            .toMutableList()
        commandList.removeAllViews()
        entries.forEach { entry ->
            val item = LayoutInflater.from(context)
                .inflate(R.layout.item_command, commandList, false) as TextView
            item.text = entry.label
            item.setOnClickListener { onCommand?.invoke(entry.value) }
            item.setOnLongClickListener {
                showEditDialog(entry)
                true
            }
            commandList.addView(item)
        }
        // 末尾固定一个"+"：滑到最后可以在这里新增命令
        val addItem = LayoutInflater.from(context)
            .inflate(R.layout.item_command, commandList, false) as TextView
        addItem.text = context.getString(R.string.command_bar_add)
        addItem.setOnClickListener { showEditDialog(null) }
        commandList.addView(addItem)
    }

    /** 主题切换后重新解析背景、分隔线和图标颜色 */
    fun refreshTheme() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.app_divider))
        val tint = ContextCompat.getColor(context, R.color.app_text)
        keyboardSwitch.imageTintList = ColorStateList.valueOf(tint)
        settingsSwitch.imageTintList = ColorStateList.valueOf(tint)
    }

    /** 解析配置：JSON 数组；解析不出来时回落到内置默认值 */
    private fun parseEntries(text: String): List<CommandEntry> =
        runCatching {
            gson.fromJson(text, Array<CommandEntry>::class.java)
        }.getOrNull()?.toList() ?: defaultCommands

    /** 编辑已有条目；[entry] 为 null 时表示新增（输入框留空） */
    private fun showEditDialog(entry: CommandEntry?) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_command_edit, null)
        val labelInput = view.findViewById<EditText>(R.id.command_edit_label)
        val valueInput = view.findViewById<EditText>(R.id.command_edit_value)
        if (entry != null) {
            labelInput.setText(entry.label)
            valueInput.setText(entry.value)
        }
        val immersive = immersiveProvider?.invoke() ?: false
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (entry == null) R.string.command_add_title else R.string.command_edit_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .show(immersive)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val label = labelInput.text.toString().trim()
            val value = valueInput.text.toString().trim()
            if (label.isEmpty() || value.isEmpty()) {
                Toast.makeText(context, R.string.command_edit_invalid, Toast.LENGTH_SHORT).show()
            } else {
                saveEntry(entry, CommandEntry(value, label))
                dialog.dismiss()
            }
        }
    }

    /** 替换（entry 非空）或追加（entry 为空）条目，然后通知调用方保存 */
    private fun saveEntry(oldEntry: CommandEntry?, newEntry: CommandEntry) {
        if (oldEntry == null) {
            entries.add(newEntry)
        } else {
            val index = entries.indexOf(oldEntry)
            if (index >= 0) entries[index] = newEntry else entries.add(newEntry)
        }
        val text = gson.toJson(entries)
        onCommandsChanged?.invoke(text)
        setCommands(text)
    }

    companion object {
        const val KEYBOARD_COMMAND = "Keyboard"
        const val SETTING_COMMAND = "Setting"
        /** 固定在右侧、不参与编辑和滚动的条目 */
        val pinnedCommands = setOf(KEYBOARD_COMMAND, SETTING_COMMAND)

        /**
         * 内置默认命令：配置为空或解析失败时使用。
         * 直接写在代码里，省掉 strings.xml 里那一层层转义。
         */
        val defaultCommands: List<CommandEntry> = listOf(
            CommandEntry("SaM\"candle|lantern|lamp\"", "Lamp"),
            CommandEntry("SaM\"unicorn horn\"", "Cure"),
            CommandEntry("SaM\"pick-axe\"", "Dig"),
            CommandEntry("S#engrave#-nL\"Elbereth\":", "Elber"),
            CommandEntry("S20s", "20s"),
            CommandEntry(".", "Rest"),
            CommandEntry("SS", "Save"),
            CommandEntry("#quit", "Quit"),
            CommandEntry("PriceID", "Price"),
            CommandEntry("Monster", "Monster"),
        )
    }
}
