package com.yywspace.anethack

import android.app.Activity
import android.os.Handler
import android.util.Log
import com.yywspace.anethack.command.NHBlockingCommandController
import com.yywspace.anethack.command.NHPosCommand
import com.yywspace.anethack.databinding.ActivityNethackBinding
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.entity.NHMessage
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.window.NHExtendChoose
import com.yywspace.anethack.window.NHPlayerChoose
import com.yywspace.anethack.window.NHQuestion
import com.yywspace.anethack.window.NHWMap
import com.yywspace.anethack.window.NHWMenu
import com.yywspace.anethack.window.NHWMessage
import com.yywspace.anethack.window.NHWStatus
import com.yywspace.anethack.window.NHWText
import com.yywspace.anethack.window.NHWindow
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList


class NetHack(
    val handler: Handler, val context: Activity,
    val binding: ActivityNethackBinding, private val netHackDir:String) {
    private val TAG = "NetHack"
    private val windows = CopyOnWriteArrayList<NHWindow>()
    private var question: NHQuestion = NHQuestion(this)
    private var playerChoose: NHPlayerChoose = NHPlayerChoose(this)
    private var extCmdChoose: NHExtendChoose = NHExtendChoose(this)
    private var nextWinId = 0

    val tileSet = NHTileSet(this)
    val command = NHBlockingCommandController()
    var isRunning = false
    val prefs by lazy { SharedPreferencesUtils(context) }
    val status:NHStatus
        get() = getWStatus().status

    private val runNHThread:Thread = Thread {
        Log.d(TAG, "start native process")
        try {
            isRunning = true
            System.loadLibrary("NetHack")
            runNetHack(netHackDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isRunning = false
        Log.d(TAG, "native process finished")
    }
    fun run() {
        runNHThread.start()
    }

    fun stop() {
        stopNetHack()
    }

    private external fun stopNetHack()
    private external fun runNetHack(path: String)

    private fun createWindow(type: Int): Int {
        Log.d(TAG, "createWindow(type:$type)")
        val winType = NHWindow.NHWindowType.fromInt(type)
        val wid: Int = nextWinId++

        when (winType) {
            NHWindow.NHWindowType.NHW_MESSAGE -> {
                windows.add(NHWMessage(wid, this))
            }
            NHWindow.NHWindowType.NHW_STATUS -> {
                windows.add(NHWStatus(wid, this))
            }
            NHWindow.NHWindowType.NHW_MAP -> {
                windows.add(NHWMap(wid, this))
            }
            NHWindow.NHWindowType.NHW_MENU -> {
                windows.add(NHWMenu(wid, this))
            }
            NHWindow.NHWindowType.NHW_TEXT -> {
                windows.add(NHWText(wid,this))
            }
            NHWindow.NHWindowType.NHW_PERMINVENT -> {
                // windows.add(NHWPermInvent(wid, NHWindowType.NHW_PERMINVENT))
            }
        }
        return wid
    }
    private fun displayWindow(wid: Int, blocking: Boolean) {
        getWindow(wid).displayWindow(blocking)
        Log.d(TAG, "displayWindow(wid:$wid,blocking:$blocking)")
    }

    private fun clearWindow(wid: Int, isRogueLevel: Int) {
        getWindow(wid).clearWindow(isRogueLevel)
        Log.d(TAG, "clearWindow(wid:$wid,isRogueLevel:$isRogueLevel)")
    }

    private fun destroyWindow(wid: Int) {
        val window = getWindow(wid)
        window.destroyWindow()
        windows.remove(window)
        Log.d(TAG, "destroyWindow(wid:$wid)")
    }
    fun curs(wid:Int, x:Int, y:Int) {
        getWindow(wid).curs(x, y)
        Log.d(TAG, "curs(wid:$wid, x:$x, y:$y)")
    }

    fun clipAround(cx:Int, cy:Int, ux:Int, uy:Int) {
        getWMap().clipAround(cx, cy, ux, uy)
    }
    fun putString(wid: Int, attr: Int, msg: String, color: Int) {
        getWindow(wid).putString(attr, msg, color)
        Log.d(TAG, "putString(wid:$wid, attr:$attr, msg:$msg, color:$color)")
    }

    private fun renderStatus(fldIdx:Int, fldName:String, fmtVal:String, realVal:String, attr:Int, color:Int, percent:Int) {
        getWStatus().renderField(fldIdx, fldName, fmtVal, realVal, attr, color, percent)
    }

    private fun printTile(wid: Int, x: Int, y: Int, tile: Int, ch: Int, col: Int, special: Int) {
        (getWindow(wid) as NHWMap).printTile(x, y, tile, ch, col, special)
        // Log.d(TAG, "printTile(wid: $wid, x: $x, y: $y, tile: $tile, ch: $ch, col: $col, special: $special)")
    }
    private fun rawPrint(attr: Int, msg: String) {
        getWMessage().putString(attr, msg, NHColor.NO_COLOR.ordinal)
        Log.d(TAG, "rawPrint(attr:$attr,msg:$msg)")
    }

    fun getMessageHistory(idx:Int):String {
        getWMessage().apply {
            if (idx >= messageList.size)
                return "message_end"
            return messageList[idx].toString()
        }
    }

    fun putMessageHistory(msg:String, restoring:Boolean) {
        if (restoring) {
            getWMessage().apply {
                // set historical messages to the earliest
                messageList.add(
                    NHMessage(NHString(msg.trim()),
                        LocalDateTime.of(LocalDateTime.now().toLocalDate(), LocalTime.MIN))
                )
            }
        }
    }

    fun askName(nameSize: Int, saves: Array<String>):Array<String> {
        Log.d(TAG, "askName(playerNSize:$nameSize,saves:${saves})")
        playerChoose.askName(nameSize, saves)
        return playerChoose.waitForPlayerChoose()
    }
    fun showExtCmdMenu(extCmdList: Array<String>):Int {
        Log.d(TAG, "showExtCmdMenu(extCmdList:${extCmdList.toList()})")
        return extCmdChoose.waitForExtCmdChoose(extCmdList)
    }

    fun requireKeyCommand(): Int {
        val cmd = command.waitForCommand()
        return cmd.key.code
    }
    fun requirePosKeyCommand(event:IntArray): Char {
        val cmd = command.waitForCommand()
         if (cmd is NHPosCommand) {
             event[0] = cmd.x
             event[1] = cmd.y
             event[2] = cmd.mod
        }
        return cmd.key
    }

    private fun startMenu(wid: Int, behavior:Long) {
        getWMenu(wid).startMenu(behavior)
        Log.d(TAG, "startMenu(wid:$wid,behavior:$behavior)")
    }

    private fun addMenu(
        window: Int,
        glyph: Int,
        identifier: Long,
        accelerator: Char,
        groupAcc: Char,
        attr: Int,
        clr:Int,
        text: String,
        preselected: Boolean
    ) {
        getWMenu(window).addMenu(glyph, identifier, accelerator, groupAcc, attr, clr, text, preselected)
        // winid window,  const glyph_info * glyph, const union any * identifier, char accelerator, char groupacc, int attr, int clr, const char *str, unsigned int itemflags
        Log.d(TAG, "addMenu(window:$window, glyph:$glyph, identifier: $identifier, accelerator: ${accelerator}, groupAcc: $groupAcc, attr: $attr, clr: $clr, text: $text, preselected: $preselected)")
    }

    private fun endMenu(wid: Int, prompt: String) {
        getWMenu(wid).endMenu(prompt)
        Log.d(TAG, "endMenu(wid: $wid, prompt: $prompt)")
    }

    private fun selectMenu(wid: Int, how: Int): LongArray {
        val nhMenu = getWMenu(wid)
        Log.d(TAG, "selectMenu(wid: $wid, how: $how)")
        Log.d(TAG, "itemList:${nhMenu.nhMenuItems}")
        return nhMenu.selectMenu(how)
    }

    fun ynFunction(question: String, choices: String, ynNumber:LongArray, def: Char):Char {
        if (question.startsWith("In what direction")) {
            return command.waitForCommand().key
        }
        this.question.showSelectQuestion(question, choices, ynNumber, def)
        return this.question.waitForAnswer()
    }

    fun getLine(question: String, input: String, bufSize: Int):String {
        this.question.showInputQuestion(question, input, bufSize)
        return this.question.waitForLine()
    }
    private fun delayOutput() {
        try {
            Thread.sleep(50)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
    private fun getWindow(wid:Int): NHWindow {
        for (window in windows)
            if(window.wid == wid)
                return window
        throw RuntimeException("no window found for wid: $wid")
    }

    private fun getWMenu(wid:Int): NHWMenu {
        val window = getWindow(wid)
        if(window is NHWMenu)
            return getWindow(wid) as NHWMenu
        throw RuntimeException("no menu found for wid: $wid")
    }

    fun getWStatus(): NHWStatus {
        for (window in windows) {
            if(window is NHWStatus)
                return window
        }
        throw RuntimeException("no status window found")
    }

     private fun getWMap(): NHWMap {
        for (window in windows) {
            if(window is NHWMap)
                return window
        }
         throw RuntimeException("no map window found")
    }
     private fun getWMessage(): NHWMessage {
        for (window in windows) {
            if(window is NHWMessage)
                return window
        }
         throw RuntimeException("no message window found")
    }
    fun runOnUi(runUi: ((binding:ActivityNethackBinding, context:Activity) -> Unit)) {
        handler.post {
            runUi.invoke(binding, context)
        }
    }
}