package com.yywspace.anethack.gameview.map

import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 地图任务调度：后台线程按延迟执行任务。
 *
 * 不关心地图、帧、相机或绘制内容；立即任务延迟为 0，动画帧使用 16ms。
 */
class NHMapRenderLoop : Runnable {

    private class Task(
        val sequence: Long,
        val runAt: Long,
        val action: () -> Unit
    ) : Delayed {
        override fun getDelay(unit: TimeUnit): Long =
            unit.convert(runAt - System.nanoTime(), TimeUnit.NANOSECONDS)

        override fun compareTo(other: Delayed): Int {
            val task = other as Task
            val byTime = runAt.compareTo(task.runAt)
            return if (byTime != 0) byTime else sequence.compareTo(task.sequence)
        }

        fun run() = action()
    }

    private val queue = DelayQueue<Task>()
    private val nextSequence = AtomicLong()

    @Volatile
    private var running = false

    private var thread: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread(this, "NHMapRenderLoop").apply { start() }
    }

    @Synchronized
    fun stop() {
        running = false
        // 唤醒阻塞在 take() 的线程
        queue.offer(Task(nextSequence.incrementAndGet(), System.nanoTime(), {}))
    }

    fun post(task: () -> Unit) = postDelayed(0, task)

    fun postDelayed(delayMs: Long, task: () -> Unit) {
        queue.offer(Task(
            nextSequence.incrementAndGet(),
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs),
            task
        ))
    }

    override fun run() {
        while (running) {
            try {
                queue.take().run()
            } catch (_: InterruptedException) {
                break
            }
        }
    }
}
