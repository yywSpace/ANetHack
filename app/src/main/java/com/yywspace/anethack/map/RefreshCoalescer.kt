package com.yywspace.anethack.map

import android.os.Handler
import android.os.Looper
import android.view.Choreographer

/**
 * 把来自游戏线程的刷新请求合并后切到主线程执行。
 *
 * 两层保护：
 * 1. Handler 把请求从游戏线程送到主线程（Choreographer 要求所在线程有 Looper）；
 * 2. 在主线程上等到下一帧再执行，保证每帧最多刷新一次。
 *
 * 麻痹等连续回合时短时间会有大量请求，这里会把它们压缩成一帧一次。
 */
internal class RefreshCoalescer(private val onRefresh: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false

    fun request() {
        if (scheduled) return
        scheduled = true
        handler.post {
            // 已经在主线程：等到下一帧再执行，保证每帧最多刷新一次
            Choreographer.getInstance().postFrameCallback {
                scheduled = false
                onRefresh()
            }
        }
    }
}
