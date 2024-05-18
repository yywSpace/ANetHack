package com.yywspace.anethack.command

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class NHCommandController {
    val cmdQueue = ArrayDeque<NHCommand>()
    val lock = ReentrantLock()
    val condition: Condition = lock.newCondition()

    var lastCmdTime: LocalDateTime = LocalDateTime.now()

    fun sendCommand(command: NHCommand) {
        lastCmdTime = LocalDateTime.now()
        if(command.count > 1) {
            command.count.toString().toCharArray().forEach {
                cmdQueue.add(NHCommand(it))
            }
        }
        cmdQueue.add(command)
        lock.withLock {
            condition.signal()
        }
    }

    fun sendExtendCommand(command: NHExtendCommand) {
        lastCmdTime = LocalDateTime.now()
        cmdQueue.add(NHCommand(command.key))
        cmdQueue.add(command)
        lock.withLock {
            condition.signal()
        }
    }

    public fun waitForCommand(): NHCommand {
        val cmd = if(cmdQueue.isNotEmpty()) {
            cmdQueue.removeFirst()
        }else {
            lock.withLock {
                condition.await()
            }
            cmdQueue.removeFirst()
        }
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
                    if(cmd == cmdQueue.removeFirst())
                        return cmd
                }
                return null
            }
        }
        return null
    }
    inline fun <reified T:NHCommand>waitForAnyCommand():T{
        if(cmdQueue.isEmpty()) {
            lock.withLock {
                condition.await()
            }
        }
        while (cmdQueue.isNotEmpty()) {
            val cmd = cmdQueue.removeFirst()
            if (cmd is T)
                return cmd
            if(cmdQueue.isEmpty()) {
                lock.withLock {
                    condition.await()
                }
            }
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
            }
        }
        throw RuntimeException("command not found")
    }

    fun clear() {
        cmdQueue.clear()
        lastCmdTime = LocalDateTime.now()
    }
}