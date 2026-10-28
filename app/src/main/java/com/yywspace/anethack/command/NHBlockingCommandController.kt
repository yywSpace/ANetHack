package com.yywspace.anethack.command

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

    /** 记录正在处理的指令（inline 函数里也要用，所以是 internal） */
    @PublishedApi
    internal fun markConsumed(cmd: NHCommand): NHCommand {
        lastConsumedCommand = cmd
        return cmd
    }

    fun sendCommand(command: NHCommand) {
        // 下面这部分判断只有手动在代码中构建Command时才会用到
        // 其余情况直接使用序列命令：S20l 即可
        if(command.count > 1) {
            command.count.toString().toCharArray().forEach {
                cmdQueue.put(NHCommand(it))
            }
        }
        cmdQueue.put(command)
    }

    fun waitForCommand(): NHCommand {
        return markConsumed(cmdQueue.take())
    }

    /**
     * 取出队列里第一个"选择菜单条目"用的指令：[NHKeyCommand]或带正则的 [NHMenuCommand]。
     * 它之前的指令一并丢弃（和 findAnyCommand 的语义一致）；队列为空时返回 null。
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
    }
}
