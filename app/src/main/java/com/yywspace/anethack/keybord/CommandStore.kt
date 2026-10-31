package com.yywspace.anethack.keybord

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yywspace.anethack.AssetsUtils
import java.io.File
import java.lang.reflect.Type

/** 命令栏 / 扩展面板里的一个按钮：显示的文字 + 实际发送的命令 */
data class CommandEntry(val label: String, val command: String)

/** 扩展面板的一行：是否启用 + 这一行的命令 */
data class CommandRow(
    val enabled: Boolean = true,
    val commands: MutableList<CommandEntry> = mutableListOf()
)

/** 导入/导出的整体配置：命令栏 + 扩展面板 */
data class CommandConfig(
    val bar: List<CommandEntry>? = null,
    val extra: List<CommandRow>? = null
)

/**
 * 命令面板的持久化：直接读写应用私有目录里的两个 JSON 文件。
 *
 * - 首次启动时从 assets 复制一份默认配置到 [android.content.Context.getFilesDir]；
 * - 之后面板和编辑器都直接改这两个文件，不再经过 SharedPreferences。
 */
object CommandStore {

    private const val DIR = "commands"
    private const val BAR_FILE = "bottom_command_bar.json"
    private const val EXTRA_FILE = "extra_command_panel.json"

    private val gson = Gson()

    private val barType: Type = object : TypeToken<List<CommandEntry>>() {}.type
    private val extraType: Type = object : TypeToken<List<CommandRow>>() {}.type

    /** 底部命令栏的命令 */
    fun readBar(context: Context): List<CommandEntry> {
        val text = readText(context, BAR_FILE) ?: return emptyList()
        if (text.isBlank()) return emptyList()
        return runCatching { gson.fromJson<List<CommandEntry>>(text, barType) }
            .getOrNull().orEmpty()
    }

    fun writeBar(context: Context, entries: List<CommandEntry>) {
        writeText(context, BAR_FILE, gson.toJson(entries, barType))
    }

    /** 扩展面板的分组命令 */
    fun readExtra(context: Context): List<CommandRow> {
        val text = readText(context, EXTRA_FILE) ?: return emptyList()
        if (text.isBlank()) return emptyList()
        return runCatching { gson.fromJson<List<CommandRow>>(text, extraType) }
            .getOrNull().orEmpty()
    }

    /** 把两份配置导出成一段 JSON 文本 */
    fun exportJson(bar: List<CommandEntry>, extra: List<CommandRow>): String =
        gson.toJson(CommandConfig(bar, extra))

    /** 解析导入的 JSON；格式不对返回 null */
    fun importJson(json: String): CommandConfig? =
        runCatching { gson.fromJson(json, CommandConfig::class.java) }.getOrNull()

    fun writeExtra(context: Context, rows: List<CommandRow>) {
        writeText(context, EXTRA_FILE, gson.toJson(rows, extraType))
    }

    /** 读文件；文件不存在时先从 assets 复制默认值过来 */
    private fun readText(context: Context, name: String): String? {
        val target = file(context, name)
        if (!target.exists()) {
            val default = AssetsUtils.readText(context, "$DIR/$name") ?: return null
            runCatching {
                target.parentFile?.mkdirs()
                target.writeText(default)
            }
            return default
        }
        return runCatching { target.readText() }.getOrNull()
    }

    private fun writeText(context: Context, name: String, text: String) {
        runCatching {
            val target = file(context, name)
            target.parentFile?.mkdirs()
            target.writeText(text)
        }
    }

    private fun file(context: Context, name: String): File =
        File(File(context.filesDir, DIR), name)
}