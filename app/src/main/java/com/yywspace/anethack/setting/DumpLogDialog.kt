package com.yywspace.anethack.setting

import android.content.Context
import android.widget.ListView
import android.view.ViewGroup
import android.widget.SimpleAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.yywspace.anethack.R
import com.yywspace.anethack.log.DumpLog

/** dump 日志列表：第一行用户名，第二行开始 ~ 结束时间 */
object DumpLogDialog {

    fun show(context: Context, onOpen: (String) -> Unit) {
        val logs = DumpLog.list(context)
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
    fun clear(context: Context) {
        val count = DumpLog.files(context).size
        if (count == 0) {
            Toast.makeText(context, R.string.pref_log_dump_empty, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.pref_log_clear)
            .setMessage(context.getString(R.string.pref_log_clear_confirm, count))
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                DumpLog.clear(context)
                Toast.makeText(context, R.string.pref_log_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
