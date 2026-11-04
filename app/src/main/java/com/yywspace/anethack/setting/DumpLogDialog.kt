package com.yywspace.anethack.setting

import android.content.Context
import android.widget.ListView
import android.view.ViewGroup
import android.widget.SimpleAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.yywspace.anethack.R
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** dump 日志列表：第一行用户名，第二行开始 ~ 结束时间 */
object DumpLogDialog {

    private data class DumpLog(
        val file: File,
        val name: String,
        val start: LocalDateTime?,
        val range: String
    )

    fun show(context: Context, dir: File, onOpen: (String) -> Unit) {
        val logs = readLogs(dir)
        if (logs.isEmpty()) {
            Toast.makeText(context, R.string.pref_log_dump_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val items = logs.map { mapOf("title" to it.name, "subtitle" to it.range) }
        lateinit var dialog: AlertDialog
        val list = ListView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.6f).toInt()
            )
            adapter = SimpleAdapter(
                context,
                items,
                android.R.layout.simple_list_item_2,
                arrayOf("title", "subtitle"),
                intArrayOf(android.R.id.text1, android.R.id.text2)
            )
            setOnItemClickListener { _, _, position, _ ->
                dialog.dismiss()
                onOpen(logs[position].file.name)
            }
        }
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.pref_log_dump)
            .setView(list)
            .setNegativeButton(R.string.dialog_confirm, null)
            .create()
        dialog.show()
    }

    /** 清空全部 dump 日志（带确认和数量提示） */
    fun clear(context: Context, dir: File) {
        val files = dir.listFiles { it.extension == "log" } ?: emptyArray()
        if (files.isEmpty()) {
            Toast.makeText(context, R.string.pref_log_dump_empty, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.pref_log_clear)
            .setMessage(context.getString(R.string.pref_log_clear_confirm, files.size))
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                files.forEach { it.delete() }
                Toast.makeText(context, R.string.pref_log_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun readLogs(dir: File): List<DumpLog> {
        val parser = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        val display = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return dir.listFiles { it.extension == "log" }?.mapNotNull { file ->
            // 用户名_开始时间_结束时间
            val parts = file.nameWithoutExtension.split("_")
            if (parts.size < 3) return@mapNotNull null
            val name = parts[0]
            val start = parse(parts[1], parser)
            val end = parse(parts[2], parser)
            val range = if (start != null && end != null)
                "${start.format(display)} ~ ${end.format(display)}"
            else
                file.name
            DumpLog(file, name, start, range)
        }?.sortedByDescending { it.start } ?: emptyList()
    }

    private fun parse(value: String, parser: DateTimeFormatter): LocalDateTime? =
        runCatching { LocalDateTime.parse(value, parser) }.getOrNull()
}
