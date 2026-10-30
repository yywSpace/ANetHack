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
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.yywspace.anethack.R
import com.yywspace.anethack.extensions.show

/** 命令栏里的一条命令：按钮上显示的文字 + 发送的指令 */
data class CommandEntry(val label: String, val command: String)

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
    /** 点击"更多命令"图标：由调用方展开或收起额外命令面板 */
    var onExtraCommands: (() -> Unit)? = null
    /** 弹窗是否需要按沉浸式显示 */
    var immersiveProvider: (() -> Boolean)? = null

    private val commandList: LinearLayout
    private val divider: View
    private val keyboardSwitch: ImageView
    private val extraCommandSwitch: ImageView

    /** 当前配置里的条目 */
    private var entries = mutableListOf<CommandEntry>()

    /**
     * 序列化时自己写 JSON：Gson 走反射取字段，键顺序取决于 JVM 返回字段的顺序，
     * 这里显式按 label、command 写，保证存下来的配置顺序固定。
     */
    private val gson: Gson = GsonBuilder()
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
        extraCommandSwitch = findViewById(R.id.extra_command_switch)
        keyboardSwitch.setOnClickListener { onCommand?.invoke(KEYBOARD_COMMAND) }
        // 长按键盘图标 → 打开设置
        keyboardSwitch.setOnLongClickListener {
            onCommand?.invoke(SETTING_COMMAND)
            true
        }
        extraCommandSwitch.setOnClickListener { onExtraCommands?.invoke() }
        // 长按"更多命令"图标 → 打开设置
        extraCommandSwitch.setOnLongClickListener {
            onCommand?.invoke(SETTING_COMMAND)
            true
        }
    }

    /** 用配置串刷新命令按钮；空串时回落到内置默认值 */
    fun setCommands(commands: String?) {
        val text = commands.orEmpty()
        entries = (if (text.isBlank()) defaultCommands else parseEntries(text))
            .filter { it.command !in pinnedCommands }
            .toMutableList()
        // 设置入口永远排在最后：用户删掉或改名后这里会补回来，避免找不到设置
        val existingSetting = entries.firstOrNull { it.command == SETTING_COMMAND }
        entries.removeAll { it.command == SETTING_COMMAND }
        entries.add(existingSetting ?: CommandEntry(SETTING_COMMAND, SETTING_COMMAND))
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

    /** 切换深浅色后重新上色：背景、分隔线、键盘/更多命令/设置三个图标 */
    fun refreshTheme() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.app_divider))
        val tint = ContextCompat.getColor(context, R.color.app_text)
        keyboardSwitch.imageTintList = ColorStateList.valueOf(tint)
        extraCommandSwitch.imageTintList = ColorStateList.valueOf(tint)
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
        val text = gson.toJson(entries)
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
        val text = gson.toJson(entries)
        onCommandsChanged?.invoke(text)
        setCommands(text)
    }

    companion object {
        const val KEYBOARD_COMMAND = "Keyboard"
        const val SETTING_COMMAND = "Setting"
        /** 固定在右侧、不参与编辑和滚动的条目 */
        val pinnedCommands = setOf(KEYBOARD_COMMAND)

        /**
         * 内置默认命令：配置为空或解析失败时使用。
         * 直接写在代码里，省掉 strings.xml 里那一层层转义。
         */
        val defaultCommands: List<CommandEntry> = listOf(
            CommandEntry("Dig", "aM\"pick-axe\""),
            CommandEntry("Lamp", "aM\"candle|lantern|lamp\""),
            CommandEntry("Cure", "aM\"unicorn horn\""),
            CommandEntry("Scare", "aM\"tooled horn|bugle\"y"),
            CommandEntry("Elber", "#engrave#-nL\"Elbereth\":"),
            CommandEntry("20s", "20s"),
            CommandEntry("FRest", "m."),
            CommandEntry("Setting", "Setting")
        )
    }
}
