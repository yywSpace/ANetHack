package com.yywspace.anethack.window

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.extensions.show

class NHWText(wid: Int, type:NHWindowType, private val nh: NetHack) : NHWindow(wid, type) {
    private val textList = mutableListOf<NHString>()
    override fun curs(x: Int, y: Int) {

    }

    override fun displayWindow(blocking: Boolean) {
        showDialog(nh, buildContent(textList.map { it.toString() }, nh.prefs.textReflow), blocking)
    }

    override fun clearWindow(isRogueLevel: Int) {

    }

    override fun destroyWindow() {

    }

    override fun putString(attr: Int, msg: String, color: Int) {
        textList.add(NHString(msg, attr))
    }

    companion object {

        /** 供 NHWText 和 NHWMenu 共用的只读文本弹窗。 */
        fun showDialog(nh: NetHack, content: String, blocking: Boolean = true) {
            nh.handler.post {
                val view = View.inflate(nh.context, R.layout.dialog_text, null)
                view.findViewById<TextView>(R.id.text_view).text = content
                val dialog = AlertDialog.Builder(nh.context)
                    .setView(view)
                    .setPositiveButton(R.string.dialog_confirm, null)
                    .create()
                if (blocking) {
                    dialog.setOnDismissListener {
                        nh.command.sendCommand(NHKeyCommand(27.toChar()))
                    }
                }
                dialog.setCancelable(false)
                dialog.show(nh.prefs.immersiveMode)
            }
        }
        /**
         * @param format true 时把 C 侧折行重新拼回段落。
         *        判断依据：缩进层级相同、不是列表项/对齐表格、上一行不是完整句子 → 视为同段续行。
         */
        fun buildContent(textList:List<String>, format:Boolean = false):String {
            if (!format)
                return textList.joinToString("\n")

            // 按空行切块；块内默认是硬折行，直接拼起来交给 TextView 自动换行。
            // 列表、表格、ASCII 图等预格式化块保留原换行。
            val blocks = mutableListOf<MutableList<String>>()
            var block = mutableListOf<String>()
            for (raw in textList) {
                val line = raw.trimEnd()
                if (line.isBlank()) {
                    if (block.isNotEmpty()) {
                        blocks.add(block)
                        block = mutableListOf()
                    }
                } else {
                    block.add(line)
                }
            }
            if (block.isNotEmpty())
                blocks.add(block)

            return blocks.joinToString("\n\n") { lines ->
                formatBlock(lines)
            }.trimEnd()
        }

        /** 需要保留换行的块：列表、带制表符/连续空格的表格或 ASCII 图。 */
        private fun isPreformattedBlock(lines: List<String>): Boolean =
            lines.any { isPreformatted(it) }

        private fun formatBlock(lines: List<String>): String {
            if (!isPreformattedBlock(lines))
                return lines.joinToString(" ") { it.trim() }
            return lines.joinToString("\n")
        }

        private fun isPreformatted(line: String): Boolean {
            val body = line.trimStart()
            return body.contains('\t') ||
                body.contains("   ") ||
                isListItem(body)
        }

        private fun isListItem(line: String): Boolean {
            val t = line.trimStart()
            if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ "))
                return true
            // 只把 "1. " / "1) " 当作编号列表，避免 3.0.1 这类版本号误判
            return t.length > 2 && t[0].isDigit() &&
                (t[1] == '.' || t[1] == ')') && t[2] == ' '
        }
    }
}
