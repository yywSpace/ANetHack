package com.yywspace.anethack

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
import com.yywspace.anethack.window.NHWMessage
import kotlin.math.ceil


class NHMessageSurfaceView: SurfaceView, SurfaceHolder.Callback,Runnable {
    private var textSize = 42f
    private val textPaint:TextPaint = TextPaint()
    private lateinit var nh:NetHack
    private lateinit var nhMessage: NHWMessage
    private var messageInit: Boolean = false
    private var messageSize = 3

    private var holder: SurfaceHolder? = null
    private var canvas: Canvas? = null
    private var isDrawing = false

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
        holder = getHolder()
        holder?.addCallback(this)
        holder?.setFormat(PixelFormat.TRANSLUCENT);
        isFocusable = true
        this.keepScreenOn = true
    }
    fun initMessage(nh:NetHack, message: NHWMessage) {
        this.nh = nh
        this.nhMessage = message
        messageInit = true
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        isDrawing = true
        Thread(this).start();
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isDrawing = false;
    }
    private fun drawMessageList(canvas: Canvas?) {
        canvas?.apply {
            if (messageInit) {
                var messageListHeight = 0f
                nhMessage.getRecentMessageList(messageSize)
                    .reversed().forEach{
                    val dynamicLayout = DynamicLayout.Builder.obtain(
                        it.value.toSpannableString(), textPaint,
                        width
                    ).build()
                    canvas.save()
                    canvas.translate(0f, messageListHeight)
                    dynamicLayout.draw(canvas)
                    canvas.restore()
                    messageListHeight += dynamicLayout.height
                }
                post {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, ceil(messageListHeight).toInt()
                    )
                }
            }
        }
    }

    private fun draw() {
        try {
            canvas = holder?.lockCanvas()
            canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            drawMessageList(canvas)
        } catch (_: Exception) {
        } finally {
            if (canvas != null)
                holder?.unlockCanvasAndPost(canvas)
        }
    }
    override fun run() {
        while (isDrawing) {
            draw()
            Thread.sleep(100)
        }
    }
}

