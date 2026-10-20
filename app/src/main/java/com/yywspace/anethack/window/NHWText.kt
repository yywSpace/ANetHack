package com.yywspace.anethack.window

import android.util.Log
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
        Log.d("NHWText","NHWText: displayWindow")
        nh.handler.post {
            val dialogTextView = View.inflate( nh.context, R.layout.dialog_text, null)
                .apply {
                    findViewById<TextView>(R.id.text_view).apply {
                        // movementMethod = ScrollingMovementMethod.getInstance()
                        text = buildContent(textList.map { it.toString() }, nh.prefs.textReflow)
                    }
                }
            val dialog = AlertDialog.Builder(nh.context).apply {
                setView(dialogTextView)
                setOnDismissListener {
                    nh.command.sendCommand(NHKeyCommand(27.toChar()))
                }
                setPositiveButton(R.string.dialog_confirm) { _, _ ->

                }
            }.create()
            dialog.show(nh.prefs.immersiveMode)
        }
    }

    override fun clearWindow(isRogueLevel: Int) {

    }

    override fun destroyWindow() {

    }

    override fun putString(attr: Int, msg: String, color: Int) {
        textList.add(NHString(msg, attr))
    }

    companion object {
        /**
         * @param format true 时把 C 侧折行重新拼回段落。
         *        判断依据：缩进层级相同、不是列表项/对齐表格、上一行不是完整句子 → 视为同段续行。
         */
        fun buildContent(textList:List<String>, format:Boolean = false):String {
            if (!format)
                return textList.joinToString("\n")

            val content = StringBuilder()
            var prev: String? = null
            for (raw in textList) {
                val line = raw.trimEnd()
                if (line.isBlank()) {
                    // 空行：段落分隔
                    content.append('\n')
                    prev = null
                    continue
                }
                val p = prev
                when {
                    p == null -> content.append(line)
                    shouldJoin(p, line) -> content.append(' ').append(line.trimStart())
                    else -> content.append('\n').append(line)
                }
                prev = line
            }
            // 合并连续空行并去掉结尾空白
            return content.toString().replace(Regex("\n{3,}"), "\n\n").trimEnd()
        }

        /** 下一行是否只是上一行被硬折后的续行（同一段落内） */
        private fun shouldJoin(prev: String, next: String): Boolean {
            if (prev.isBlank()) return false
            val prevIndent = leadingSpaces(prev)
            val nextIndent = leadingSpaces(next)
            // 缩进层级不同 → 新段落/引用块/示例；
            // 例外：段落首行缩进 1~3 空格、后续行顶格（license、data.base 的排版习惯）
            val firstLineIndent = prevIndent in 1..3 && nextIndent == 0
            if (!firstLineIndent && prevIndent != nextIndent) return false
            // 列表项、带符号或编号开头的行
            if (isListItem(next)) return false
            // 对齐表格/命令表（制表符或连续三个空格分隔的列）
            if (isAligned(prev) || isAligned(next)) return false
            // 上一行是完整句子
            if (prev.trimEnd().last() in ".!?:;\"')]") return false
            return true
        }

        /** 对齐行：正文里含制表符或三个以上连续空格（help/hh/cmdhelp 里的表格） */
        private fun isAligned(line: String): Boolean {
            val body = line.trimStart()
            return body.contains('\t') || body.contains("   ")
        }

        private fun leadingSpaces(line: String): Int =
            line.takeWhile { it == ' ' || it == '\t' }.length

        private fun isListItem(line: String): Boolean {
            val t = line.trimStart()
            if (t.isEmpty()) return true
            if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("> ") || t.startsWith("|"))
                return true
            if (t.startsWith("(") || t.startsWith("[")) return true
            return t.length > 1 && t[0].isDigit() && (t[1] == '.' || t[1] == ')')
        }
    }
}