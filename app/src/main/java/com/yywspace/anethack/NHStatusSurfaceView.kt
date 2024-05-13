package com.yywspace.anethack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.text.DynamicLayout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.entity.NHStatus.*
import com.yywspace.anethack.entity.NHString
import kotlin.math.ceil
import kotlin.math.max


class NHStatusSurfaceView: SurfaceView, SurfaceHolder.Callback,Runnable {
    private var textSize = 42f
    private val paint: Paint = Paint()
    private val textPaint:TextPaint = TextPaint()
    private lateinit var nh:NetHack
    private lateinit var nhStatus: NHStatus
    private var statusInit: Boolean = false

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
    fun initStatus(nh:NetHack, nhStatus: NHStatus) {
        this.nh = nh
        this.nhStatus = nhStatus
        statusInit = true
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
    
    private fun getStatus(field: StatusField):Pair<StatusField, Spannable> {
        return when (field) {
            StatusField.BL_HP -> {
                val hp = nhStatus.getSpannableField(StatusField.BL_HP)
                val hpMax = nhStatus.getSpannableField(StatusField.BL_HPMAX)
                Pair(field, SpannableStringBuilder(hp).append(hpMax))
            }
            StatusField.BL_ENE -> {
                val pw = nhStatus.getSpannableField(StatusField.BL_ENE)
                val pwMax = nhStatus.getSpannableField(StatusField.BL_ENEMAX)
                Pair(field, SpannableStringBuilder(pw).append(pwMax))
            }
            else -> Pair(field, nhStatus.getSpannableField(field))
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
            add(listOf(title, align, hunger, cap, condition))
            add(listOf(hp, st, dx, co, intel, wi, ch))
            add(listOf(pw, levelDesc, gold, ac, xp, time))
        }
        return statusBarList
    }

    private fun drawHpOrPw(status: Pair<StatusField, Spannable>, canvas: Canvas): RectF {
        val hpMaxValue = nhStatus.getField(StatusField.BL_HPMAX)
        val pwMaxValue = nhStatus.getField(StatusField.BL_HPMAX)
        val hpPlaceholder = "HP:${hpMaxValue}/${hpMaxValue}"
        val pwPlaceholder = "Pw:${pwMaxValue}/${pwMaxValue}"
        val hpWidth = ceil(DynamicLayout.getDesiredWidth(hpPlaceholder, textPaint))
        val pwWidth = ceil(DynamicLayout.getDesiredWidth(pwPlaceholder, textPaint))
        val statusLayout =DynamicLayout.Builder.obtain(
            status.second, textPaint, ceil(max(hpWidth, pwWidth)).toInt()
        ).build()
        val regex = Regex("(.*:)([0-9]*)\\(([0-9]*)\\)")
        textPaint.color = Color.WHITE
        regex.find(status.second)?.apply {
            val label = groupValues[1]
            val curValue = groupValues[2]
            val maxValue = groupValues[3]
            val labelLayout =DynamicLayout.Builder.obtain(
                label, textPaint,
                ceil(DynamicLayout.getDesiredWidth(label, textPaint)).toInt()
            ).build()
            val valueLayout =DynamicLayout.Builder.obtain(
                "$curValue/$maxValue", textPaint,
                ceil(DynamicLayout.getDesiredWidth("$curValue/$maxValue", textPaint)).toInt()
            ).build()
            nhStatus.getField(status.first)?.apply {
                 paint.color = nhColor.toColor()
            }
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f,
                statusLayout.width * (curValue.toFloat() / maxValue.toFloat()),
                labelLayout.height.toFloat(),
                paint)
            paint.color = Color.GRAY
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            canvas.drawRect(0f, 0f, statusLayout.width.toFloat(), labelLayout.height.toFloat(), paint)
            labelLayout.draw(canvas)
            canvas.translate(labelLayout.width + (statusLayout.width-labelLayout.width) / 2f - valueLayout.width / 2f, 0f)
            valueLayout.draw(canvas)
        }
        return RectF(0f,0f, statusLayout.width.toFloat(), statusLayout.height.toFloat())
    }

    private fun drawStatusBar(canvas: Canvas?) {
        canvas?.apply {
            if (statusInit) {
                val statusBarList = buildStatusBar()
                var statusBarHeight = 0f
                statusBarList.forEach{
                    var statusBarWidth = 0f
                    var maxHeight = 0f
                    it.forEach { s ->
                        canvas.save()
                        canvas.translate(statusBarWidth, statusBarHeight)
                        if (s.first == StatusField.BL_HP || s.first == StatusField.BL_ENE) {
                            val border = drawHpOrPw(s, canvas)
                            statusBarWidth += (border.width() + 20f)
                            maxHeight = maxHeight.coerceAtLeast(border.height())
                        }else {
                            val dynamicLayout = DynamicLayout.Builder.obtain(
                                s.second, textPaint, ceil(DynamicLayout.getDesiredWidth(s.second, textPaint)).toInt()
                            ).build()
                            dynamicLayout.draw(canvas)
                            statusBarWidth += (dynamicLayout.width + 20f)
                            maxHeight = maxHeight.coerceAtLeast(dynamicLayout.height.toFloat())
                        }
                        canvas.restore()
                    }
                    statusBarHeight += maxHeight
                }
                post {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, ceil(statusBarHeight+5).toInt()
                    )
                }
            }
        }
    }

    private fun draw() {
        try {
            canvas = holder?.lockCanvas()
            canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            drawStatusBar(canvas)
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

