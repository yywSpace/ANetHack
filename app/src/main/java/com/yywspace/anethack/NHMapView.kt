package com.yywspace.anethack

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.minus
import androidx.core.view.marginStart
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHPosCommand
import com.yywspace.anethack.command.NHPosCommand.*
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.extensions.showImmersive
import com.yywspace.anethack.window.NHWMap
import com.yywspace.anethack.window.NHWMenu
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor


class NHMapView: View {
    private var textSize = 64f
    private var scaleFactor = 1f
    private var mapInit = false
    private val paint = Paint()
    private val asciiPaint = TextPaint()
    private var scaling = false
    private var scrolling = false
    private var longPress = false

    private lateinit var nh:NetHack
    private lateinit var map: NHWMap
    private lateinit var mapBorder:RectF
    private lateinit var lastMapBorder:RectF
    private var lastTouchTile:Point? = null

    private var tileWidth:Float = 0F
    private var tileHeight:Float = 0F


    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaling = true
            scaleMap(detector.scaleFactor, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    })
    
    private val gestureDetector =  GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            lastMapBorder = RectF(mapBorder)
            scaling = false
            return super.onDown(e)
        }

        override fun onScroll(e1: MotionEvent, e2: MotionEvent,
                              distanceX: Float, distanceY: Float): Boolean {
            if(scaling) return true
            scrolling = true
            mapBorder.left = lastMapBorder.left  + e2.x - e1.x
            mapBorder.right = lastMapBorder.right + e2.x - e1.x
            mapBorder.top = lastMapBorder.top + e2.y - e1.y
            mapBorder.bottom = lastMapBorder.bottom + e2.y - e1.y
            invalidate()
            return true
        }


        override fun onLongPress(e: MotionEvent) {
            if(scaling) return
            longPress = true
            lastTouchTile = getTileLocation(e.x, e.y).also { point ->
                if (abs(map.curse.x - point.x) < 1 && abs(map.curse.y - point.y) < 1) {
                    nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.TRAVEL))
                } else {
                    val menuAdapter = CMDMenuAdapter(listOf(
                        "Fire ammunition from quiver",
                        "Zap a wand"
                    ))
                    val dialogMenuView = inflate(context, R.layout.dialog_menu, null)
                        .apply {
                            findViewById<RecyclerView>(R.id.menu_item_list)?.apply {
                                adapter = menuAdapter
                                layoutManager = LinearLayoutManager(context)
                            }
                        }
                    val dialog = AlertDialog.Builder(context).run {
                        setTitle(R.string.pos_key_question)
                        setView(dialogMenuView)
                        create()
                    }
                    dialogMenuView.apply {
                        findViewById<MaterialButton>(R.id.menu_btn_1)?.apply {
                            setText(R.string.pos_key_look)
                            setOnClickListener {
                                // get tile info
                                nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.LOOK))
                                this@NHMapView.invalidate()
                                dialog.dismiss()
                            }
                        }
                        findViewById<MaterialButton>(R.id.menu_btn_2)?.apply {
                            setText(R.string.pos_key_straight)
                            setOnClickListener {
                                val tileBorder = getTileBorder(map.curse.x, map.curse.y)
                                val direction = getMoveDirection(
                                    PointF(tileBorder.centerX(), tileBorder.centerY()),
                                    PointF(e.x, e.y)
                                )
                                playerMove(direction, true)
                                this@NHMapView.invalidate()
                                dialog.dismiss()
                            }
                        }
                        findViewById<MaterialButton>(R.id.menu_btn_3)?.apply {
                            setText(R.string.pos_key_travel)
                            setOnClickListener {
                                // whether travel to tile
                                nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.TRAVEL))
                                this@NHMapView.invalidate()
                                dialog.dismiss()
                            }
                        }
                    }
                    menuAdapter.onItemClick = { _, _, item ->
                        when(item) {
                            "Fire ammunition from quiver" -> {
                                nh.command.sendCommand(NHCommand('f', 1))
                            }
                            "Zap a wand" -> {
                                nh.command.sendCommand(NHCommand('z', 1))
                            }
                        }
                        invalidate()
                        dialog.dismiss()
                    }
                    dialog.showImmersive()

                    MaterialAlertDialogBuilder(context).apply {
                        setMessage(R.string.pos_key_question)
                        setPositiveButton(R.string.pos_key_travel) { _, _ ->

                        }
                        setNegativeButton(R.string.pos_key_straight) { _, _ ->

                        }
                        setNeutralButton(R.string.pos_key_look){ _, _ ->

                        }
                        create()
                        // showImmersive()
                    }
                }
            }
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
    }

    fun initMap(nh:NetHack, map: NHWMap) {
        this.nh = nh
        this.map = map
        tileWidth = getBaseTileWidth()
        tileHeight = getBaseTileHeight()
        mapBorder = RectF(
            0F,0F,
            map.tileCols * tileWidth,
            map.tileRows * tileHeight)
        mapInit = true
    }

    fun scaleMap(scaleFactor:Float, centerX:Float, centerY:Float) {
        Log.d("scaleMap", this.scaleFactor.toString())
        if(this.scaleFactor * scaleFactor < 0.1 || this.scaleFactor * scaleFactor > 10)
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
        var cmd = when(direction) {
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
            cmd = cmd.uppercaseChar()
        }
        nh.command.sendCommand(NHCommand(cmd))
    }

    private fun getMoveDirection(base:PointF, target:PointF):Direction {
        val offset = target.minus(base)
        val angle = Math.toDegrees(atan2(offset.x, offset.y).toDouble()) + 180
        val playerBorder = getTileBorder(map.curse.x, map.curse.y)
        if (playerBorder.contains(target.x, target.y)) {
            return Direction.CENTER
        }
        var direction:Direction = Direction.UNDEFINE
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
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (mapInit) {
            drawAscii(canvas)
            drawCurse(canvas)
            drawBorder(canvas)
            drawLastTouchTile(canvas)
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
        invalidate()
    }

    private fun drawCurse(canvas: Canvas?) {
        map.apply {
            if(curse.x < 0 || curse.y < 0)
                return
            val tile = tiles[curse.y][curse.x]
            val tb = getTileBorder(curse.x, curse.y)
            nh.getNHWStatus()?.apply {
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

    private fun getTileLocation(vx:Float, vy:Float):Point {
        val tx = floor((vx - mapBorder.left) / tileWidth)
        val ty = floor((vy - mapBorder.top) / tileHeight)
        return Point(tx.toInt(), ty.toInt())
    }

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
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            if(event.action == MotionEvent.ACTION_UP) {
                // click event
                if(!scaling and !scrolling and !longPress) {
                    lastTouchTile = getTileLocation(event.x, event.y)
                    val curseBorder = getTileBorder(map.curse.x, map.curse.y)
                    val direction = getMoveDirection(
                        PointF(curseBorder.centerX(), curseBorder.centerY()),
                        PointF(event.x, event.y)
                    )
                    Log.d("direction","$direction")
                    playerMove(direction, false)
                }
                if(longPress)
                    longPress = false
                if(scrolling)
                    scrolling = false
            }
        }
        return true
    }

    enum class Direction{
        UP, DOWN, LEFT, RIGHT, LEFT_UP, LEFT_DOWN, RIGHT_UP, RIGHT_DOWN, CENTER, UNDEFINE;
    }

}

