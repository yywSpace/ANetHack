package com.yywspace.anethack.command

import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue

class NHBlockingCommandController {
    val cmdQueue = LinkedBlockingQueue<NHCommand>(1000)

    /**
     * 最近一次被游戏取走（正在处理）的指令。
     *
     * 游戏线程取指令 → 处理 → 期间产生的消息都归到这条指令名下，
     * 消息窗口据此给每条消息标记来源（见 NHWMessage.putString）。
     */
    @Volatile
    var lastConsumedCommand: NHCommand? = null
        private set

    /** 已经取出、等待逐个消费的指令（数字前缀展开后会临时排在这里） */
    private val pending = ArrayDeque<NHCommand>()

    /** 记录正在处理的指令（inline 函数里也要用，所以是 internal） */
    @PublishedApi
    internal fun markConsumed(cmd: NHCommand): NHCommand {
        lastConsumedCommand = cmd
        return cmd
    }

    fun sendCommand(command: NHCommand) {
        cmdQueue.put(command)
    }

    /**
     * 给游戏线程取一条指令。
     *
     * 带数字前缀的指令（如 20S）会先按位产出 '2'、'0' 两条按键，再产出指令本身：
     * 游戏自己会累积数量。菜单这类窗口不走这里，直接读 [NHCommand.numberPrefix]。
     */
    fun waitForCommand(): NHCommand {
        while (pending.isEmpty()) {
            val cmd = cmdQueue.take()
            if (cmd.numberPrefix > 0)
                cmd.numberPrefix.toString().forEach { pending.addLast(NHKeyCommand(it)) }
            pending.addLast(cmd)
        }
        return markConsumed(pending.removeFirst())
    }

    /**
     * 取出队列里第一个"选择菜单条目"用的指令：按键（可带数字前缀）或带正则的 [NHMenuCommand]。
     * 它之前的指令一并丢弃；队列为空时返回 null。
     */
    fun findMenuCommand(): NHCommand? {
        while (true) {
            val cmd = cmdQueue.poll() ?: return null
            if (cmd is NHKeyCommand || (cmd is NHMenuCommand && cmd.pattern != null))
                return markConsumed(cmd)
        }
    }

    /** 取出队列里第一个 [T] 类型的指令，它之前的指令一并丢弃；没有则返回 null */
    inline fun <reified T:NHCommand>findAnyCommand():T?{
        while (true) {
            val cmd = cmdQueue.poll() ?: return null
            if (cmd is T)
                return markConsumed(cmd) as T
        }
    }

    inline fun <reified T:NHCommand>waitForAnyCommand(noinline otherCommandDiscard:((NHCommand)->Unit)? = null):T{
        while (true) {
            val cmd = markConsumed(cmdQueue.take())
            if (cmd is T)
                return cmd
            otherCommandDiscard?.invoke(cmd)
        }
    }

    fun clear() {
        cmdQueue.clear()
        pending.clear()
    }
}
