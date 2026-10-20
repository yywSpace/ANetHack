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
import com.yywspace.anethack.R
import com.yywspace.anethack.extensions.show

/**
 * 底部命令栏：命令按钮一行横向滚动，键盘/设置固定在最右侧。
 * 点击按钮触发 [onCommand]，长按按钮弹出编辑框（改显示名和命令），
 * 编辑后通过 [onCommandsChanged] 交回给调用方保存偏好。
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

    /** 当前配置里的条目（`value|Label`），编辑时按它定位 */
    private var entries = mutableListOf<String>()

    init {
        orientation = HORIZONTAL
        isClickable = true
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
            .ifEmpty { context.getString(R.string.pref_keyboard_command_panel_default) }
        entries = text.split(" ")
            .filter { it.isNotBlank() && it.substringBefore('|') !in pinnedCommands }
            .toMutableList()
        commandList.removeAllViews()
        entries.forEach { entry ->
            val value = entry.substringBefore('|')
            val item = LayoutInflater.from(context)
                .inflate(R.layout.item_command, commandList, false) as TextView
            item.text = entry.substringAfter('|', value)
            item.setOnClickListener { onCommand?.invoke(value) }
            item.setOnLongClickListener {
                showEditDialog(entry)
                true
            }
            commandList.addView(item)
        }
    }

    /** 主题切换后重新解析背景、分隔线和图标颜色 */
    fun refreshTheme() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.app_divider))
        val tint = ContextCompat.getColor(context, R.color.app_text)
        keyboardSwitch.imageTintList = ColorStateList.valueOf(tint)
        settingsSwitch.imageTintList = ColorStateList.valueOf(tint)
    }

    /** 长按命令按钮：编辑显示名和命令 */
    private fun showEditDialog(entry: String) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_command_edit, null)
        val labelInput = view.findViewById<EditText>(R.id.command_edit_label)
        val valueInput = view.findViewById<EditText>(R.id.command_edit_value)
        labelInput.setText(entry.substringAfter('|', entry))
        valueInput.setText(entry.substringBefore('|'))
        val immersive = immersiveProvider?.invoke() ?: false
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.command_edit_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .show(immersive)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val label = labelInput.text.toString().trim()
            val value = valueInput.text.toString().trim()
            if (label.isEmpty() || value.isEmpty() || value.any { it.isWhitespace() }) {
                Toast.makeText(context, R.string.command_edit_invalid, Toast.LENGTH_SHORT).show()
            } else {
                saveEntry(entry, "$value|$label")
                dialog.dismiss()
            }
        }
    }

    /** 替换配置里对应的条目并通知调用方保存 */
    private fun saveEntry(oldEntry: String, newEntry: String) {
        val index = entries.indexOf(oldEntry)
        if (index >= 0) entries[index] = newEntry else entries.add(newEntry)
        val text = entries.joinToString(" ")
        onCommandsChanged?.invoke(text)
        setCommands(text)
    }

    companion object {
        const val KEYBOARD_COMMAND = "Keyboard"
        const val SETTING_COMMAND = "Setting"
        /** 固定在右侧、不参与编辑和滚动的条目 */
        val pinnedCommands = setOf(KEYBOARD_COMMAND, SETTING_COMMAND)
    }
}