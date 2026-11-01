package com.yywspace.anethack.keybord

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yywspace.anethack.AssetsUtils
import java.io.File
import java.lang.reflect.Type

/**
 * 键盘布局的读取入口：标准布局和键盘模板来自 assets，自定义布局存在应用私有目录。
 *
 * 这里只负责“把布局读出来 / 把自定义布局写回去”，具体渲染仍然由 NHKeyboardView 负责。
 */
object KeyboardStore {

    private const val ROWS_DIR = "commands"
    private const val ROWS_FILE = "custom_keyboard_rows.json"
    private const val ASSET_ROWS = "commands/custom_keyboard_rows.json"
    private const val CUSTOM_LAYOUT = "keyboard_custom.json"

    private const val LETTER = "keyboard_letter.json"
    private const val UPPER_LETTER = "keyboard_upper_letter.json"
    private const val SYMBOL = "keyboard_symbol.json"
    private const val CTRL = "keyboard_ctrl.json"
    private const val META = "keyboard_meta.json"
    private const val TEMPLATE = "keyboard_view_template.json"
    private const val CUSTOM_TEMPLATE = "keyboard_custom_template.json"

    private val gson = Gson()
    private val type: Type = object : TypeToken<List<List<CommandEntry>>>() {}.type

    private fun rowsFile(context: Context): File = File(File(context.filesDir, ROWS_DIR), ROWS_FILE)

    // ---------------- 标准布局（assets） ----------------
    fun readLetter(context: Context): KeyboardLayout = readAssetLayout(context, LETTER)

    fun readUpperLetter(context: Context): KeyboardLayout = readAssetLayout(context, UPPER_LETTER)

    fun readSymbol(context: Context): KeyboardLayout = readAssetLayout(context, SYMBOL)

    fun readCtrl(context: Context): KeyboardLayout = readAssetLayout(context, CTRL)

    fun readMeta(context: Context): KeyboardLayout = readAssetLayout(context, META)

    /** 键盘网格模板：决定每一行有多少格、每个格占多少列 */
    fun readTemplate(context: Context): KeyboardLayout = readAssetLayout(context, TEMPLATE)

    /** 自定义键盘模板：中间三行各合并成一个大格子，用来放可滑动的命令行 */
    fun readCustomTemplate(context: Context): KeyboardLayout = readAssetLayout(context, CUSTOM_TEMPLATE)

    private fun readAssetLayout(context: Context, fileName: String): KeyboardLayout =
        KeyboardLayout.fromJson(AssetsUtils.readText(context, "keyboard/$fileName").orEmpty())

    // ---------------- 自定义布局（私有目录） ----------------
    /** 自定义布局的固定行（第 0/4 行）：和标准布局一样从 assets 读成 KeyboardLayout */
    fun readCustom(context: Context): KeyboardLayout = readAssetLayout(context, CUSTOM_LAYOUT)

    /** 中间三行存本地文件（和命令面板的 JSON 一样） */
    fun readCustomRows(context: Context): List<List<CommandEntry>> {
        val json = readRowsJson(context)
        if (json.isBlank()) return emptyList()
        return runCatching { gson.fromJson<List<List<CommandEntry>>>(json, type) }.getOrNull().orEmpty()
    }

    fun writeCustomRows(context: Context, rows: List<List<CommandEntry>>) {
        writeRowsJson(context, gson.toJson(rows, type))
    }

    private fun readRowsJson(context: Context): String {
        val file = rowsFile(context)
        if (!file.exists()) {
            val asset = AssetsUtils.readText(context, ASSET_ROWS).orEmpty()
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(asset)
            }
            return asset
        }
        return runCatching { file.readText() }.getOrDefault("")
    }

    private fun writeRowsJson(context: Context, json: String) {
        runCatching {
            val file = rowsFile(context)
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
    }
}
