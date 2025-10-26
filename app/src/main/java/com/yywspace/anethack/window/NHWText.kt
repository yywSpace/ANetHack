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
                        text = buildContent(textList.map { it.toString() }, false)
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
        fun buildContent(textList:List<String>, format:Boolean = false):String {
            if (!format)
                return textList.joinToString("\n")

            val content = StringBuilder()
            var lastTerminated = true
            for (line in textList) {
                if (lastTerminated)
                    content.append(line)
                else
                    content.append(line.trim())
                val terminated = ":.\"!*-\\|".contains(line.last())
                if (terminated)
                    content.append("\n")
                else
                    content.append(" ")
                lastTerminated = terminated
            }
            return content.trimEnd('\n').toString()
        }
    }
}