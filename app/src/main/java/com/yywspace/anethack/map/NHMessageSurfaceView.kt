package com.yywspace.anethack.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.text.DynamicLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.window.NHWMessage
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.ceil
import androidx.core.graphics.withTranslation


class NHMessageSurfaceView: SurfaceView, SurfaceHolder.Callback,Runnable {
    private var textSize = 42f
    private val textPaint:TextPaint = TextPaint()
    private lateinit var nh: NetHack
    private lateinit var nhMessage: NHWMessage
    private var messageInit: Boolean = false
    private var messageSize = 3

    private var canvas: Canvas? = null
    private var isDrawing = false

    /** 变化通知锁：putString 唤醒，绘制线程挂起（事件驱动） */
    private val redrawLock = ReentrantLock()
    private val redrawCondition = redrawLock.newCondition()
    private var pendingDirty = false
    /** 上次绘制的高度（变化才 post layoutParams） */
    private var lastHeight = 0
    /** 布局缓存：key = 消息文本，同文本复用 DynamicLayout。
     *  窗口内旧消息跨帧内容不变（即使位置后移），每次绘制只有新消息构建布局 */
    private val layoutCache = HashMap<String, DynamicLayout>()

    /** 新消息到达时唤醒绘制线程（NHWMessage.putString 调用） */
    fun requestRedraw() {
        redrawLock.lock()
        try {
            pendingDirty = true
            redrawCondition.signalAll()
        } finally {
            redrawLock.unlock()
        }
    }

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    init {
        initView()
        textPaint.textSize = textSize
        textPaint.isAntiAlias = true
    }

    private fun initView() {
        holder?.addCallback(this)
        holder?.setFormat(PixelFormat.TRANSLUCENT)
        isFocusable = true
        this.keepScreenOn = true
    }
    fun initMessage(nh: NetHack, message: NHWMessage) {
        this.nh = nh
        this.nhMessage = message
        messageInit = true
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        isDrawing = true
        pendingDirty = true // surface 重建后强制重绘一帧
        Thread(this).start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // surface 尺寸变化（高度动态调整后）→ 强制重绘
        requestRedraw()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isDrawing = false
    }
    private fun drawMessageList(canvas: Canvas?) {
        canvas?.apply {
            if (messageInit) {
                var messageListHeight = 0f
                val messages = nhMessage.getRecentMessageList(messageSize).reversed()
                // 内容缓存：key = 文本 + 颜色（attach 后最新批绿色/旧消息原色，
                // 只按文本缓存会导致旧消息复用"绿色时"的布局，永远显示绿色）
                val layouts = messages.map { msg ->
                    val key = msg.toString() + "|" + msg.colorIndex
                    layoutCache.getOrPut(key) {
                        DynamicLayout.Builder.obtain(
                            msg.toSpannableString(), textPaint,
                            width
                        ).build()
                    }
                }
                // 缓存上限，防止无限增长（游戏消息文本有限）
                if (layoutCache.size > 50)
                    layoutCache.clear()
                layouts.forEach { dynamicLayout ->
                    canvas.withTranslation(0f, messageListHeight) {
                        dynamicLayout.draw(this)
                    }
                    messageListHeight += dynamicLayout.height
                }
                // 高度变化才回主线程调整布局（SurfaceView 尺寸变化开销大）
                val h = ceil(messageListHeight).toInt()
                if (h != lastHeight) {
                    lastHeight = h
                    post {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, h
                        )
                    }
                }
            }
        }
    }

    private fun draw() {
        // 无变化（兜底唤醒）不绘制，避免空转清屏
        if (!hasChange()) return
        // 绘制前消费信号：绘制期间到达的新请求保持 pendingDirty=true，
        // 完成后会立即再绘制一帧，避免"绘制中 putString 被吞 → 最新消息永远不显示"
        pendingDirty = false
        try {
            canvas = holder?.lockCanvas()
            canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawMessageList(canvas)
        } finally {
            if (canvas != null)
                holder?.unlockCanvasAndPost(canvas)
        }
    }

    private fun hasChange(): Boolean = pendingDirty

    override fun run() {
        while (isDrawing) {
            // 无变化挂起（500ms 兜底）；变化来源：putString signalAll
            redrawLock.lock()
            try {
                while (!hasChange() && isDrawing) {
                    redrawCondition.await(500, TimeUnit.MILLISECONDS)
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } finally {
                redrawLock.unlock()
            }
            draw()
        }
    }
}

