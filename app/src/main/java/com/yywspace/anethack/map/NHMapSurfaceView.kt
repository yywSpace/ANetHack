package com.yywspace.anethack.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.graphics.minus
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NHPosKeyAdapter
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHPosCommand
import com.yywspace.anethack.command.NHPosCommand.*
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.window.NHWMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor


class NHMapSurfaceView: SurfaceView, SurfaceHolder.Callback,Runnable {
    private var textSize = 64f
    private var scaleFactor = 1f
    private var mapInit = false
    private val paint = Paint()
    private val asciiPaint = TextPaint()
    private var scaling = false

    private lateinit var nh: NetHack
    private lateinit var map: NHWMap
    private lateinit var mapBorder:RectF
    private lateinit var lastMapBorder:RectF
    private var lastTouchTile:Point? = null

    private var tileWidth:Float = 0F
    private var tileHeight:Float = 0F
    private var holder: SurfaceHolder? = null
    private var canvas: Canvas? = null
    private var isDrawing = false

    private lateinit var playerIndicator: NHMapPlayerIndicator


    private var mapTouchListener: NHMapTouchListener = NHMapTouchListener().apply {
        onNHMapTouchListener = object : NHMapTouchListener.OnNHMapTouchListener {
            override fun onDown(e: PointF) {
                scaling = false
                lastMapBorder = RectF(mapBorder)
            }

            override fun onClick(e: PointF) {
                if (scaling) return
                if (playerIndicator.isClicked(e.x, e.y)) {
                    centerPlayerInScreen()
                    return
                }
                lastTouchTile = getTileLocation(e.x, e.y)
                val curseBorder = getTileBorder(map.curse.x, map.curse.y)
                val direction = getMoveDirection(
                    PointF(curseBorder.centerX(), curseBorder.centerY()),
                    PointF(e.x, e.y)
                )
                Log.d("direction", "$direction")
                playerMove(direction, false)
            }

            override fun onLongPressUp(e: PointF) {

            }

            override fun onLongPressMove(e1: PointF, e2: PointF) {

            }
            override fun onLongPress(e: PointF) {
                if (scaling) return
                lastTouchTile = getTileLocation(e.x, e.y).also { point ->
                    if (abs(map.curse.x - point.x) < 1 && abs(map.curse.y - point.y) < 1) {
                        nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.TRAVEL))
                    } else {
                        val border = getTileBorder(point.x, point.y)
                        showPopupWindow(border.centerX(), border.centerY())
                    }
                }
            }
            override fun onMove(e1: PointF, e2: PointF) {
                if (scaling) return
                mapBorder.left = lastMapBorder.left  + e2.x - e1.x
                mapBorder.right = lastMapBorder.right + e2.x - e1.x
                mapBorder.top = lastMapBorder.top + e2.y - e1.y
                mapBorder.bottom = lastMapBorder.bottom + e2.y - e1.y
            }
        }
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaling = true
            scaleMap(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    init {
        asciiPaint.textSize = textSize
        asciiPaint.isAntiAlias = true
        asciiPaint.typeface = Typeface.createFromAsset(context.assets, "fonts/monobold.ttf")
        asciiPaint.textAlign = Align.LEFT
        paint.isAntiAlias = true
        scaleDetector.isQuickScaleEnabled = false

        initView()
    }

    private fun initView() {
        holder = getHolder()
        holder?.addCallback(this)
        isFocusable = true
        // isFocusableInTouchMode = true
        this.keepScreenOn = true
    }

    fun initMap(nh: NetHack, map: NHWMap) {
        this.nh = nh
        this.map = map
        this.playerIndicator = NHMapPlayerIndicator(nh)
        tileWidth = getBaseTileWidth()
        tileHeight = getBaseTileHeight()
        mapBorder = RectF(
            0F,0F,
            map.tileCols * tileWidth,
            map.tileRows * tileHeight)
        mapInit = true
    }

    @SuppressLint("InflateParams")
    fun showPopupWindow(x:Float, y:Float) {
        val posKeyList = listOf("Look", "Run", "Travel")
        val popupWindow = PopupWindow(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val recyclerView = LayoutInflater.from(context).inflate(R.layout.popup_window_pos_key, null).apply {
            setBackgroundColor(Color.WHITE)
            findViewById<RecyclerView>(R.id.pos_key_list).apply {
                adapter = NHPosKeyAdapter(posKeyList).apply {
                    onItemClickListener = object : NHPosKeyAdapter.OnItemClickListener {
                        override fun onItemClick(view: View, position: Int) {
                            val point = getTileLocation(x, y)
                            when(posKeyList[position]) {
                                "Look" -> {
                                    // get tile info
                                    nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.LOOK))
                                }
                                "Run" -> {
                                    val tileBorder = getTileBorder(map.curse.x, map.curse.y)
                                    val direction = getMoveDirection(
                                        PointF(tileBorder.centerX(), tileBorder.centerY()),
                                        PointF(x, y)
                                    )
                                    playerMove(direction, true)
                                }
                                "Travel" -> {
                                    // whether travel to tile
                                    nh.command.sendCommand(
                                        NHPosCommand(point.x, point.y, PosMod.TRAVEL)
                                    )
                                }
                            }
                            popupWindow.dismiss()
                        }
                    }
                }
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            }
            measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        }
        popupWindow.apply {
            contentView = recyclerView
            isOutsideTouchable = true
            showAtLocation(this@NHMapSurfaceView, Gravity.NO_GRAVITY,
                (x-contentView.measuredWidth /2 ).toInt(), (y-contentView.measuredHeight-tileHeight).toInt())
        }
    }

    fun scaleMap(scaleFactor:Float, centerX:Float, centerY:Float) {
        // Log.d("scaleMap", this.scaleFactor.toString())
        if(this.scaleFactor * scaleFactor < 0.3 || this.scaleFactor * scaleFactor > 10)
            return
        this.scaleFactor *= scaleFactor
        paint.textSize = paint.textSize * scaleFactor
        asciiPaint.textSize = asciiPaint.textSize * scaleFactor
        tileWidth *= scaleFactor
        tileHeight *= scaleFactor
        mapBorder.left += (mapBorder.left - centerX) * (scaleFactor - 1)
        mapBorder.right += (mapBorder.right - centerX) * (scaleFactor - 1)
        mapBorder.top += (mapBorder.top - centerY) * (scaleFactor - 1)
        mapBorder.bottom += (mapBorder.bottom - centerY) * (scaleFactor - 1)
    }

    private fun playerMove(direction: Direction, straight:Boolean) {
        //  y k u
        //  \ | /
        // h- . -l
        //  / | \
        //  b j n
        val cmd = when(direction) {
            Direction.UP -> 'k'
            Direction.DOWN -> 'j'
            Direction.LEFT -> 'h'
            Direction.RIGHT -> 'l'
            Direction.LEFT_UP -> 'y'
            Direction.LEFT_DOWN -> 'b'
            Direction.RIGHT_UP -> 'u'
            Direction.RIGHT_DOWN -> 'n'
            Direction.CENTER -> '.'
            else -> 27.toChar()
        }
        if(straight) {
            // cmd = cmd.uppercaseChar()
            nh.command.sendCommand(NHCommand('G'))
        }
        nh.command.sendCommand(NHCommand(cmd))
    }

    private fun getMoveDirection(base:PointF, target:PointF): Direction {
        val offset = target.minus(base)
        val angle = Math.toDegrees(atan2(offset.x, offset.y).toDouble()) + 180
        val playerBorder = getTileBorder(map.curse.x, map.curse.y)
        if (playerBorder.contains(target.x, target.y)) {
            return Direction.CENTER
        }
        var direction: Direction = Direction.UNDEFINE
        if ((angle > 337.5) or (angle < 22.5))
            direction = Direction.UP
        if ((angle > 22.5) and (angle < 67.5))
            direction = Direction.LEFT_UP
        if ((angle > 67.5) and (angle < 112.5))
            direction = Direction.LEFT
        if ((angle > 112.5) and (angle < 157.5))
            direction = Direction.LEFT_DOWN
        if ((angle > 157.5) and (angle < 202.5))
            direction = Direction.DOWN
        if ((angle > 202.5) and (angle < 247.5))
            direction = Direction.RIGHT_DOWN
        if ((angle > 247.5) and (angle < 292.5))
            direction = Direction.RIGHT
        if ((angle > 292.5) and (angle < 337.5))
            direction = Direction.RIGHT_UP
        Log.d("getMoveDirection", angle.toString())
        return direction
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (mapInit) {
            centerView(map.player.x, map.curse.y)
        }
    }
    private fun drawAscii(canvas: Canvas?) {
        map.apply {
            for (x in 0 until tileCols) {
                for (y in 0 until tileRows) {
                    val tile = tiles[y][x]
                    val tb = getTileBorder(x,y)
                    paint.style = Paint.Style.FILL
                    var bgColor = Color.BLACK
                    var fgColor = tile.color.toColor()
                    // reverse special objet color
                    if(tile.overlay.toInt() != 0 && tile.glyph >= 0) {
                        fgColor = Color.BLACK
                        bgColor = tile.color.toColor()
                    }
                    paint.color = bgColor
                    canvas?.drawRect(tb, paint)

                    if(tile.glyph >= 0) {
                        val ch = String(tile.ch.toString().toByteArray())
                        asciiPaint.color = fgColor
                        canvas?.drawText(ch,0, 1,
                            tb.left, tb.bottom - asciiPaint.descent() , asciiPaint);
                    }
                }
            }
        }
    }

    fun centerPlayerInScreen() {
        if (mapInit)
            centerView(map.player.x, map.player.y)
    }
    fun centerView(x:Int, y:Int) {
        val tb = getTileBorder(x,y)
        mapBorder.offset(-(tb.centerX() - measuredWidth / 2F), -(tb.centerY() - measuredHeight / 2F))
    }

    private fun drawCurse(canvas: Canvas?) {
        map.apply {
            if(curse.x < 0 || curse.y < 0)
                return
            val tile = tiles[curse.y][curse.x]
            val tb = getTileBorder(curse.x, curse.y)
            nh.getWStatus()?.apply {
                val hp = status.getField(NHStatus.StatusField.BL_HP)
                if (hp != null) {
                    paint.color = hp.nhColor.toColor()
                    paint.style = Paint.Style.FILL
                    canvas?.drawRect(tb, paint)
                    if(tile.glyph >= 0) {
                        asciiPaint.color = tile.color.toColor()
                        canvas?.drawText(tile.ch.toString(),0, 1,
                            tb.left, tb.bottom - asciiPaint.descent() , asciiPaint);
                    }
                }
            }

        }
    }

    private fun drawLastTouchTile(canvas: Canvas?) {
        lastTouchTile?.apply {
            val tb = getTileBorder(x, y)
            paint.color = Color.GRAY
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2F
            canvas?.drawRect(tb, paint)
        }
    }

    private fun drawPlayerIndicator(canvas: Canvas?) {
        val playerBorder = getTileBorder(map.curse.x, map.curse.y)
        playerIndicator.draw(canvas,
            measuredWidth.toFloat(), measuredHeight.toFloat(),
            playerBorder.centerX(), playerBorder.centerY()
        )
    }

    private fun drawBorder(canvas: Canvas?) {
        map.apply {
            paint.color = Color.GRAY
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2F
            canvas?.drawRect(mapBorder, paint);
        }
    }
    private fun getBaseTileWidth(): Float {
        val w = asciiPaint.measureText("\u2550")
        return floor(w)
    }

    private fun getBaseTileHeight(): Float {
        val metrics = asciiPaint.fontMetrics
        return floor(metrics.descent - metrics.ascent)
    }

    private fun getTileBorder(x:Int, y:Int):RectF {
        return RectF(
            ceil(tileWidth * x  + mapBorder.left),
            ceil(tileHeight * y  + mapBorder.top),
            ceil(tileWidth * (x + 1)  + mapBorder.left),
            ceil(tileHeight * (y +1 )  + mapBorder.top),
        )
    }

    // 获取界面TileLocation
    private fun getTileLocation(vx:Float, vy:Float):Point {
        val tx = floor((vx - mapBorder.left) / tileWidth)
        val ty = floor((vy - mapBorder.top) / tileHeight)
        return Point(tx.toInt(), ty.toInt())
    }

    // 获取地图内TileLocation
    private fun getInnerTileLocation(vx:Float, vy:Float):Point? {
        val tx = floor((vx - mapBorder.left) / tileWidth)
        val ty = floor((vy - mapBorder.top) / tileHeight)
        for (x in 0 until map.tileCols) {
            for (y in 0 until map.tileRows) {
                val tb = getTileBorder(x,y)
                if (tb.contains(vx, vy)) {
                    Log.d("getTileLocation", "inner: $x, $y")
                    Log.d("getTileLocation", "outer: $tx, $ty")
                    return Point(x, y)
                }
            }
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(mapInit) {
            // 防止缩放和滑动冲突
            if (event.pointerCount == 2)
                scaleDetector.onTouchEvent(event)
            else if (event.pointerCount == 1)
                mapTouchListener.onTouch(this, event)
        }
        return true
    }

    enum class Direction{
        UP, DOWN, LEFT, RIGHT, LEFT_UP, LEFT_DOWN, RIGHT_UP, RIGHT_DOWN, CENTER, UNDEFINE;
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

    private fun draw() {
        holder?.apply {
            try {
                canvas = lockCanvas()
                if (mapInit) {
                    canvas?.drawColor(Color.BLACK)
                    drawAscii(canvas)
                    drawCurse(canvas)
                    drawBorder(canvas)
                    drawLastTouchTile(canvas)
                    drawPlayerIndicator(canvas)
                }
            } catch (_: Exception) {
            } finally {
                if (canvas != null)
                    unlockCanvasAndPost(canvas)
            }
        }
    }
    override fun run() {
        while (isDrawing) {
            draw()
        }
    }
}

