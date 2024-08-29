package com.yywspace.anethack.window

import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHAnswerCommand
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.extensions.showImmersive

class NHWText(wid: Int, private val nh: NetHack) : NHWindow(wid) {
    private val textList = mutableListOf<NHString>()
    override fun curs(x: Int, y: Int) {

    }

    override fun displayWindow(blocking: Boolean) {
        Log.d("NHWText","NHWText: displayWindow")
        nh.handler.post {
            val dialogTextView = View.inflate( nh.context, R.layout.dialog_text, null)
                .apply {
                    findViewById<TextView>(R.id.text_view).apply {
                        movementMethod = ScrollingMovementMethod.getInstance()
                        text = textList.joinToString("\n")
                    }
                }
            val dialog = AlertDialog.Builder(nh.context).apply {
                setView(dialogTextView)
                setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    if (blocking) {
                        nh.command.sendCommand(NHCommand(27.toChar()))
                    }
                }
            }.create()
            dialog.showImmersive()
        }
        nh.command.waitForAnyCommand<NHCommand> {  }
    }

    override fun clearWindow(isRogueLevel: Int) {

    }

    override fun destroyWindow() {

    }

    override fun putString(attr: Int, msg: String, color: Int) {
        textList.add(NHString(msg, attr))
    }
}