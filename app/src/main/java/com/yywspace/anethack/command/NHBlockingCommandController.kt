package com.yywspace.anethack.command

import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue

class NHBlockingCommandController {
    val cmdQueue = ArrayBlockingQueue<NHCommand>(1000)
    var lastCmdTime: LocalDateTime = LocalDateTime.now()

    fun sendCommand(command: NHCommand) {
        lastCmdTime = LocalDateTime.now()
        if(command.count > 1) {
            command.count.toString().toCharArray().forEach {
                cmdQueue.put(NHCommand(it))
            }
        }
        cmdQueue.put(command)
    }

    fun sendExtendCommand(command: NHExtendCommand) {
        lastCmdTime = LocalDateTime.now()
        cmdQueue.put(NHCommand(command.key))
        cmdQueue.put(command)
    }

    fun waitForCommand(): NHCommand {
        val cmd = cmdQueue.take()
        try {
            Thread.sleep(50)
        } catch (_: InterruptedException) {
        }
        return cmd
    }

    inline fun <reified T:NHCommand>findAnyCommand():T?{
        for(cmd in cmdQueue) {
            if (cmd is T) {
                while (cmdQueue.isNotEmpty()) {
                    if(cmd == cmdQueue.take())
                        return cmd
                }
                return null
            }
        }
        return null
    }

    inline fun <reified T:NHCommand>waitForAnyCommand(noinline otherCommandDiscard:((NHCommand)->Unit)? = null):T{
        while (true) {
            val cmd = cmdQueue.take()
            if (cmd is T)
                return cmd
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
            }
            otherCommandDiscard?.invoke(cmd)
        }
    }

    fun clear() {
        cmdQueue.clear()
        lastCmdTime = LocalDateTime.now()
    }
}