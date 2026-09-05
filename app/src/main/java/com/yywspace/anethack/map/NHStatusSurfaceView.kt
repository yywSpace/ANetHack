package com.yywspace.anethack.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.text.DynamicLayout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.entity.NHStatus.StatusField
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.ceil
import androidx.core.graphics.withTranslation


class NHStatusSurfaceView: SurfaceView, SurfaceHolder.Callback,Runnable {
    private var textSize = 42f
    private val textPaint:TextPaint = TextPaint()
    private lateinit var nh: NetHack
    private lateinit var status: NHStatus
    private var statusInit: Boolean = false

    private var canvas: Canvas? = null
    private var isDrawing = false

    /** 变化通知锁：displayWindow 唤醒，绘制线程挂起（事件驱动） */
    private val redrawLock = ReentrantLock()
    private val redrawCondition = redrawLock.newCondition()
    private var pendingDirty = false
    /** 上次绘制的高度（变化才 post layoutParams） */
    private var lastHeight = 0
    /** 字段值签名缓存：值没变的字段复用上次布局（C 侧每回合全量发字段，这里做对比增量） */
    private val valueCache = HashMap<StatusField, String>()
    /** 布局缓存：field → DynamicLayout（值没变复用） */
    private val layoutCache = HashMap<StatusField, DynamicLayout>()

    /** 状态渲染完成时唤醒绘制线程（NHWStatus.displayWindow 调用） */
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
    fun initStatus(nh: NetHack, nhStatus: NHStatus) {
        this.nh = nh
        this.status = nhStatus
        statusInit = true
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        isDrawing = true
        pendingDirty = true // surface 重建后强制重绘一帧
        Thread(this).start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // surface 尺寸变化（高度动态调整后）→ 强制重绘，否则内容被旧尺寸裁剪（只显示前几行）
        requestRedraw()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isDrawing = false
    }
    
    private fun getStatus(field: StatusField):Pair<StatusField, Spannable> {
        return when (field) {
            StatusField.BL_HP -> {
                val hp = status.hitPoints.toSpannableString()
                val hpMax = status.maxHitPoints.toSpannableString()
                Pair(field, SpannableStringBuilder(hp).append(hpMax))
            }
            StatusField.BL_ENE -> {
                val pw = status.power.toSpannableString()
                val pwMax = status.maxPower.toSpannableString()
                Pair(field, SpannableStringBuilder(pw).append(pwMax))
            }
            StatusField.BL_XP -> {
                val xp = status.expLevel.toSpannableString()
                val exp = status.expPoints.toSpannableString()
                val hd = status.hitDice.toSpannableString()
                if (xp.isEmpty())
                    Pair(field, hd)
                else
                    Pair(field, SpannableStringBuilder(xp).append(exp))
            }
            StatusField.BL_CONDITION -> {
                Pair(field, status.getConditionSpannable())
            }
            else -> Pair(field, status.getField(field).toSpannableString())
        }
    }
    private fun buildStatusBar():List<List<Pair<StatusField, Spannable>>> {
        val align = getStatus(StatusField.BL_ALIGN)
        val title = getStatus(StatusField.BL_TITLE)
        val st = getStatus(StatusField.BL_STR)
        val dx = getStatus(StatusField.BL_DX)
        val co = getStatus(StatusField.BL_CO)
        val intel = getStatus(StatusField.BL_IN)
        val wi = getStatus(StatusField.BL_WI)
        val ch = getStatus(StatusField.BL_CH)
        val hp = getStatus(StatusField.BL_HP)
        val pw = getStatus(StatusField.BL_ENE)
        val gold = getStatus(StatusField.BL_GOLD)
        val ac = getStatus(StatusField.BL_AC)
        val xp = getStatus(StatusField.BL_XP)
        val time = getStatus(StatusField.BL_TIME)
        val levelDesc = getStatus(StatusField.BL_LEVELDESC)
        val hunger = getStatus(StatusField.BL_HUNGER)
        val cap = getStatus(StatusField.BL_CAP)
        val condition = getStatus(StatusField.BL_CONDITION)
        val statusBarList = mutableListOf<List<Pair<StatusField, Spannable>>>().apply {
            add(listOf(title, align))
            add(listOf(hp, st, dx, co, intel, wi, ch))
            add(listOf(pw, gold, ac, xp, time))
            add(listOf(levelDesc, hunger, cap, condition))
        }
        return statusBarList
    }

    /** 字段值签名：显示文本 + 颜色（title 额外含 HP 百分比和 HP 条颜色）。
     *  文本相同但颜色变化时缓存也需失效，否则布局复用旧颜色不更新 */
    private fun valueSignature(field: StatusField, spannable: Spannable): String {
        val colorSig = {
            val fg = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
                .joinToString(",") { it.foregroundColor.toString() }
            val bg = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)
                .joinToString(",") { it.backgroundColor.toString() }
            "|$fg|$bg"
        }
        return if (field == StatusField.BL_TITLE) {
            // HP 条背景色来自 hitPoints 颜色，独立于标题文本的 span
            spannable.toString() + "|" + status.hitPoints.percent + colorSig + "|" + status.hitPoints.color
        } else {
            spannable.toString() + colorSig
        }
    }

    /** 构建标题布局（HP 条背景按百分比） */
    private fun buildTitleLayout(titleSpannable: Spannable): DynamicLayout {
        val hp = status.hitPoints
        val title = SpannableStringBuilder(titleSpannable)
        val percent = hp.percent
        val remainSpan = BackgroundColorSpan(Color.argb(200, 220, 220, 220))
        val colorSpan = BackgroundColorSpan(hp.color)
        title.setSpan(colorSpan, 0, title.length * percent / 100, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        title.setSpan(remainSpan, title.length * percent / 100, title.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        return DynamicLayout.Builder.obtain(
            title, textPaint,
            ceil(DynamicLayout.getDesiredWidth(title, textPaint)).toInt()
        ).build()
    }

    private fun drawStatusBar(canvas: Canvas?) {
        canvas?.apply {
            if (statusInit) {
                val statusBarList = buildStatusBar()
                var statusBarHeight = 0f
                statusBarList.forEach { row ->
                    var statusBarWidth = 0f
                    var maxHeight = 0f
                    row.forEach { s ->
                        val field = s.first
                        if (s.second.isNotEmpty()) {
                            canvas.withTranslation(statusBarWidth, statusBarHeight) {
                                // 值对比：签名没变 → 复用布局；变了 → 重建（只重建变化的字段）
                                val sig = valueSignature(field, s.second)
                                val layout = if (valueCache[field] == sig) {
                                    layoutCache[field]
                                } else {
                                    val newLayout = if (field == StatusField.BL_TITLE) {
                                        buildTitleLayout(s.second)
                                    } else {
                                        DynamicLayout.Builder.obtain(
                                            s.second, textPaint,
                                            ceil(DynamicLayout.getDesiredWidth(s.second, textPaint)).toInt()
                                        ).build()
                                    }
                                    valueCache[field] = sig
                                    layoutCache[field] = newLayout
                                    newLayout
                                }
                                layout?.let { l ->
                                    l.draw(this)
                                    statusBarWidth += (l.width + 20f)
                                    maxHeight = maxHeight.coerceAtLeast(l.height.toFloat())
                                }
                            }
                        }
                    }
                    statusBarHeight += maxHeight
                }
                // 高度变化才回主线程调整布局（SurfaceView 尺寸变化开销大）
                val h = ceil(statusBarHeight + 5).toInt()
                if (h != lastHeight) {
                    lastHeight = h
                    // 高度变化 → surface 尺寸变化 → surfaceChanged 触发重绘
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
        // 完成后会立即再绘制一帧，避免"绘制中信号被吞 → 更新永远丢失"
        pendingDirty = false
        try {
            canvas = holder?.lockCanvas()
            canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawStatusBar(canvas)
        } finally {
            if (canvas != null)
                holder?.unlockCanvasAndPost(canvas)
        }
    }

    private fun hasChange(): Boolean = pendingDirty

    override fun run() {
        while (isDrawing) {
            // 无变化挂起（500ms 兜底）；变化来源：displayWindow signalAll
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

