package com.yywspace.anethack.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.graphics.minus
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.command.NHPosCommand
import com.yywspace.anethack.command.NHPosCommand.PosMod
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.map.indicator.NHMapIndicatorController
import com.yywspace.anethack.map.operation.NHMapOperation
import com.yywspace.anethack.map.operation.NHMapScale
import com.yywspace.anethack.map.operation.NHMapTransform
import com.yywspace.anethack.window.NHWMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import androidx.core.graphics.createBitmap


class NHMapSurfaceView: SurfaceView, SurfaceHolder.Callback,Runnable {
    private var textSize = 64f
    private var scaleFactor = 1f
    private var mapTranslated = false
    private var mapInit = false
    /** 视口被外部改动（指示符点击/居中/缩放）后置位，hasChange 据此触发重绘 */
    private var mapTransformDirty = false
    private val paint = Paint()
    private val asciiPaint = TextPaint()
    private var operationQueue = LinkedBlockingDeque<NHMapOperation>()

    private lateinit var nh: NetHack
    private lateinit var map: NHWMap
    private var mapBorder:RectF = RectF()
    private var lastMapBorder:RectF = RectF()
    private var lastTouchTile:Point? = null
    var lastTravelTile:Pair<String,Point>? = null
    private var lastCurse:Point = Point()
    /** 上次跟随的玩家位置（clipAround 每回合更新；curs 仅在 cursor_on_u 时更新，不可靠） */
    private var lastFollowPlayer:Point = Point(-1, -1)
    private var baseBorderWidth:Float = .5F
    private var borderWidth:Float = 0F
    private var tileWidth:Float = 0F
    private var tileHeight:Float = 0F
    /** 离屏地图缓冲：基础尺寸（不随缩放变），脏格子只更新这里，屏幕整图 blit 杜绝残影 */
    private var baseTileWidth = 0f
    private var baseTileHeight = 0f
    private var mapBitmap: Bitmap? = null
    private var mapCanvas: Canvas? = null
    /** 需要全量重绘离屏缓冲（surface 重建/首帧） */
    private var mapContentDirty = false
    /** 首次以玩家位置居中是否已做（绘制线程首帧执行，保证 view 已测量） */
    private var playerCentered = false
    private var isDrawing = false
    private lateinit var indicatorController:NHMapIndicatorController

    /** 变化通知锁：绘制线程挂起，内容/手势变化时 signalAll 唤醒 */
    private val redrawLock = ReentrantLock()
    private val redrawCondition = redrawLock.newCondition()

    /** 有内容/手势/光标变化时唤醒绘制线程（NHWMap 与手势回调调用） */
    fun requestRedraw() {
        redrawLock.lock()
        try {
            redrawCondition.signalAll()
        } finally {
            redrawLock.unlock()
        }
    }

    private var mapTouchListener: NHMapTouchListener = NHMapTouchListener().apply {
        onNHMapTouchListener = object : NHMapTouchListener.OnNHMapTouchListener {
            override fun onDown(e: PointF) {
                lastMapBorder = RectF(mapBorder)
            }

            override fun onClick(e: PointF) {
                if(indicatorController.onIndicatorClick(e))
                    return
                lastTouchTile = getTileLocation(e.x, e.y).also { point ->
                    if (nh.status.runMode == NHStatus.RunMode.TRAVEL) {
                        nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.TRAVEL))
                        mapTranslated = false
                    }else {
                        val curseBorder = getTileBorder(map.curse.x, map.curse.y)
                        val direction = getMoveDirection(
                            PointF(curseBorder.centerX(), curseBorder.centerY()),
                            PointF(e.x, e.y)
                        )
                        Log.d("direction", "$direction")
                        playerMove(direction, false)
                    }
                }
                // 点击框（lastTouchTile）变化：触发一次绘制刷新覆盖层
                mapTransformDirty = true
                requestRedraw()
            }

            override fun onLongPressUp(e: PointF) {

            }

            override fun onLongPressMove(e1: PointF, e2: PointF) {

            }
            override fun onLongPress(e: PointF) {
                if (indicatorController.onIndicatorLongPress(e))
                    return
                lastTouchTile = getTileLocation(e.x, e.y).also { point ->
                    // long click yourself
                    if (abs(map.curse.x - point.x) < 1 && abs(map.curse.y - point.y) < 1) {
                        nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.TRAVEL))
                    } else {
                        // long click other
                        val border = getTileBorder(point.x, point.y)
                        showPopupWindow(border.centerX(), border.centerY())
                    }
                }
                mapTransformDirty = true
                requestRedraw()
            }
            override fun onMove(e1: PointF, e2: PointF) {
                operationQueue.push(NHMapTransform(e2.x - e1.x, e2.y - e1.y))
                requestRedraw()
            }

            override fun onScale(scaleFactor: Float, cx: Float, cy: Float) {
                operationQueue.push(NHMapScale(scaleFactor, cx, cy))
                requestRedraw()
            }
        }
    }

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    init {
        asciiPaint.textSize = textSize
        asciiPaint.isAntiAlias = true
        asciiPaint.typeface = Typeface.createFromAsset(context.assets, "fonts/monobold.ttf")
        asciiPaint.textAlign = Align.LEFT
        paint.isFilterBitmap = false
        initView()
    }

    private fun initView() {
        holder?.addCallback(this)
        isFocusable = true
        // isFocusableInTouchMode = true
        this.keepScreenOn = true
    }

    fun initMap(nh: NetHack, map: NHWMap) {
        this.nh = nh
        this.map = map
        initMapParam()
        initIndicators()
        this.mapInit = true
        // 首帧强制全量重绘离屏 + 唤醒
        mapContentDirty = true
        mapTransformDirty = true
        playerCentered = false
    }

    private fun initMapParam() {
        nh.tileSet.updateTileSet()
        scaleFactor = 1f
        tileWidth = getBaseTileWidth()
        tileHeight = getBaseTileHeight()
        baseTileWidth = tileWidth
        baseTileHeight = tileHeight
        // 重建离屏缓冲（基础尺寸；缩放/平移只改 blit 目标，不重建缓冲）
        mapBitmap =
            createBitmap((map.width * baseTileWidth).toInt(), (map.height * baseTileHeight).toInt()).also { mapCanvas = Canvas(it) }
        mapBitmap?.eraseColor(Color.BLACK)
        drawAllToOffscreen()
        mapBorder = RectF(
            mapBorder.left, mapBorder.top,
            mapBorder.left + map.width * tileWidth,
            mapBorder.top + map.height * tileHeight
        )
        val scale = measuredWidth.toFloat() / mapBorder.height()
        val tb = getTileBorder(map.curse.x, map.curse.y)
        scaleMap(scale, tb.centerX(), tb.centerY())
    }

    private fun initIndicators() {
        indicatorController = NHMapIndicatorController(this, nh, map)
        indicatorController.setOnIndicatorClickListener(object :NHMapIndicatorController.OnIndicatorClickListener{
            override fun onIndicatorClick(e: PointF, tile: NHWMap.Tile) {
                mapTranslated = true
                centerView(tile.x, tile.y)
            }

            override fun onIndicatorLongPress(e: PointF, tile: NHWMap.Tile) {

            }

        })
    }

    private fun playerInWalkRange(walkRange: Int):Boolean {
        val offsetX = (1 - walkRange / 100f) * width / 2f
        val offsetY = (1 - walkRange / 100f) * height / 2f
        val playerBorder = getTileBorder(map.curse.x, map.curse.y)
        val cx = playerBorder.centerX()
        val cy = playerBorder.centerY()
        val width = measuredWidth.toFloat()
        val height = measuredHeight.toFloat()
        val px = if (cx > width/2) playerBorder.left else playerBorder.right
        val py = if (cy > height/2) playerBorder.top else playerBorder.bottom
        if (px > offsetX && py > offsetY && px < width-offsetX && py < height-offsetY)
            return true
        return false
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
                                    if (!(point.x >= map.width || point.y >= map.height || point.x <= 0 || point.y <= 0))
                                        lastTravelTile = Pair(nh.status.dungeonLevel.realVal, point)
                                    mapTranslated = false
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
                (x-contentView.measuredWidth /2 ).toInt(), (y-contentView.measuredHeight-tileHeight/2).toInt())
        }
    }

    private fun transformMapWithGesture(dx:Float, dy:Float) {
        mapBorder.left = floor(lastMapBorder.left  + dx)
        mapBorder.right = floor(lastMapBorder.right + dx)
        mapBorder.top = floor(lastMapBorder.top + dy)
        mapBorder.bottom = floor(lastMapBorder.bottom + dy)
    }

    private fun transformMap(dx:Float, dy:Float) {
        mapBorder.offset(dx, dy)
        mapTransformDirty = true
        requestRedraw()
    }

    private fun scaleMap(scaleFactor:Float, centerX:Float, centerY:Float) {
        // if(this.scaleFactor * scaleFactor < 0.3 || this.scaleFactor * scaleFactor > 10)
        //      return
        this.scaleFactor *= scaleFactor
        paint.textSize = textSize * this.scaleFactor
        asciiPaint.textSize = textSize * this.scaleFactor
        tileWidth = getBaseTileWidth() * this.scaleFactor
        tileHeight = getBaseTileHeight() * this.scaleFactor
        borderWidth = ceil(baseBorderWidth * this.scaleFactor)
        mapBorder.left += (mapBorder.left - centerX) * (scaleFactor - 1)
        mapBorder.top += (mapBorder.top - centerY) * (scaleFactor - 1)
        mapBorder.right = mapBorder.left + map.width * tileWidth
        mapBorder.bottom = mapBorder.top + map.height * tileHeight
        mapTransformDirty = true
        requestRedraw()
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
            nh.command.sendCommand(NHKeyCommand('G'))
        }
        nh.command.sendCommand(NHKeyCommand(cmd))
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

    /** 绘制单个格子到离屏缓冲（基础坐标，不随缩放变） */
    private fun drawOneToOffscreen(x: Int, y: Int) {
        val canvas = mapCanvas ?: return
        val tile = map.getTile(x, y)
        val tb = RectF(
            x * baseTileWidth, y * baseTileHeight,
            (x + 1) * baseTileWidth, (y + 1) * baseTileHeight
        )
        if (nh.tileSet.isTTY()) {
            paint.style = Paint.Style.FILL
            var bgColor = Color.BLACK
            var fgColor = tile.color.toColor()
            // reverse special object color
            if (tile.overlay != 0 && tile.glyph >= 0) {
                fgColor = Color.BLACK
                bgColor = tile.color.toColor()
            }
            paint.color = bgColor
            canvas.drawRect(tb, paint)
            if (tile.glyph >= 0) {
                asciiPaint.textSize = textSize // 离屏用基础字号，屏幕层 blit 时缩放
                asciiPaint.color = fgColor
                canvas.drawText(tile.ch.toString(), 0, 1, tb.left, tb.bottom - asciiPaint.descent(), asciiPaint)
            }
        } else {
            if (tile.glyph >= 0) {
                nh.tileSet.getTile(tile.glyph)?.apply {
                    canvas.drawBitmap(this, Rect(0, 0, this.width, this.height), tb, paint)
                }
            } else {
                // 无 tile：画背景覆盖旧内容（离屏也是覆盖式，杜绝残影）
                paint.color = Color.BLACK
                paint.style = Paint.Style.FILL
                canvas.drawRect(tb, paint)
            }
            if (tile.overlay != 0 && tile.glyph >= 0) {
                val overlay = nh.tileSet.getTileOverlay(tile.overlay)
                if (overlay != null) {
                    canvas.drawBitmap(overlay, nh.tileSet.getOverlayRect(tile.overlay), tb, paint)
                }
            }
        }
    }

    /** 全量重绘离屏缓冲 */
    private fun drawAllToOffscreen() {
        for (x in 0 until map.width) {
            for (y in 0 until map.height) {
                drawOneToOffscreen(x, y)
            }
        }
    }

    /** 将离屏缓冲整体绘制到屏幕（mapBorder 定位，缩放通过目标矩形实现） */
    private fun blitMap(canvas: Canvas?) {
        mapBitmap?.let { bmp ->
            canvas?.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), mapBorder, paint)
        }
    }
    fun centerPlayerInScreen() {
        if (mapInit)
            centerView(map.curse.x, map.curse.y)
    }
    fun centerView(x:Int, y:Int) {
        val tb = getTileBorder(x,y)
        transformMap(-(tb.centerX() - measuredWidth / 2F), -(tb.centerY() - measuredHeight / 2F))
    }

    private fun transformMapWithMove(walkRange:Int) {
        // 玩家位置：clipAround 的 player 优先（curs 不一定更新）
        val followX = if (map.player.x >= 0) map.player.x else map.curse.x
        val followY = if (map.player.y >= 0) map.player.y else map.curse.y
        val tb = getTileBorder(followX, followY)
        var dx = 0f
        var dy = 0f
        val px = tb.centerX()
        val py = tb.centerY()
        val width = measuredWidth.toFloat()
        val height = measuredHeight.toFloat()
        val offsetX = (1 - walkRange / 100f) * width / 2f
        val offsetY = (1 - walkRange / 100f) * height / 2f
        if (px < offsetX)
            dx = offsetX - px
        if (px > width - offsetX)
            dx = width - offsetX - px
        if (py < offsetY)
            dy = offsetY - py
        if (py > height - offsetY)
            dy = height - offsetY - py
        // 视口没动（玩家在安全区内）时不触发 transformMap，保持增量绘制
        if (dx != 0f || dy != 0f)
            transformMap(dx, dy)
    }

    private fun drawAsciiCurse(canvas: Canvas?) {
        map.apply {
            if(curse.x < 0 || curse.y < 0)
                return
            val tile = getTile(curse.x, curse.y)
            val tb = getTileBorder(curse.x, curse.y)
            paint.color = nh.status.hitPoints.color
            paint.style = Paint.Style.FILL
            canvas?.drawRect(tb, paint)
            if(tile.glyph >= 0) {
                asciiPaint.color = tile.color.toColor()
                // 覆盖层用屏幕层字号（缩放后）；离屏绘制会重置为基础字号，这里必须显式设置
                asciiPaint.textSize = textSize * scaleFactor
                canvas?.drawText(tile.ch.toString(),0, 1,
                    tb.left, tb.bottom - asciiPaint.descent() , asciiPaint)
            }

        }
    }

    private fun drawTileCurse(canvas: Canvas?) {
        map.apply {
            if(curse.x < 0 || curse.y < 0)
                return
            val tb = getTileBorder(curse.x, curse.y)
            paint.color = nh.status.hitPoints.color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderWidth
            canvas?.drawRect(tb, paint)
        }
    }
    private fun drawLastTouchTile(canvas: Canvas?) {
        lastTouchTile?.apply {
            val tb = getTileBorder(x, y)
            paint.color = Color.GRAY
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderWidth
            canvas?.drawRect(tb, paint)
        }
    }

    private fun drawIndicator(canvas: Canvas?) {
        indicatorController.drawIndicators(canvas)
    }

    private fun drawBorder(canvas: Canvas?) {
        map.apply {
            paint.color = Color.GRAY
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderWidth
            val border = RectF(mapBorder).apply { left+=tileWidth }
            canvas?.drawRect(border, paint)
        }
    }

    private fun drawWalkRange(canvas: Canvas?) {
        val walkRangeP = nh.prefs.walkRange / 100f
        val width = measuredWidth.toFloat()
        val height = measuredHeight.toFloat()
        val rangeX = (width - walkRangeP * width) / 2
        val rangeY = (height - walkRangeP * height) / 2
        val border = RectF(rangeX, rangeY,rangeX + walkRangeP * width,rangeY  + walkRangeP * height)
        paint.color = Color.GRAY
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderWidth
        canvas?.drawRect(border, paint)
    }

    private fun getBaseTileWidth(): Float {
        return if (nh.tileSet.isTTY()) {
            asciiPaint.textSize = textSize
            val w = asciiPaint.measureText("\u2550")
            asciiPaint.textSize = textSize * scaleFactor
            floor(w)
        } else
            nh.tileSet.tileWidth.toFloat()
    }

    private fun getBaseTileHeight(): Float {
        return if (nh.tileSet.isTTY()) {
            asciiPaint.textSize = textSize
            val metrics = asciiPaint.fontMetrics
            asciiPaint.textSize = textSize * scaleFactor
            floor(metrics.descent - metrics.ascent)
        } else
            nh.tileSet.tileHeight.toFloat()
    }

    fun getTileBorder(x:Int, y:Int):RectF {
        return RectF(
            floor(tileWidth * x  + mapBorder.left),
            floor(tileHeight * y  + mapBorder.top),
            floor(tileWidth * (x + 1)  + mapBorder.left),
            floor(tileHeight * (y +1 )  + mapBorder.top),
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
        for (x in 0 until map.width) {
            for (y in 0 until map.height) {
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
        if(mapInit)
            return mapTouchListener.onTouch(this, event)
        return true
    }

    enum class Direction{
        UP, DOWN, LEFT, RIGHT, LEFT_UP, LEFT_DOWN, RIGHT_UP, RIGHT_DOWN, CENTER, UNDEFINE;
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isDrawing = true
        // surface 重建（切后台返回/设置返回）后画布清空，必须无条件重绘一帧，
        // 否则事件驱动下没有变化源通知绘制线程 → 黑屏
        mapContentDirty = true
        mapTransformDirty = true
        Thread(this).start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isDrawing = false
    }

    /** 是否有需要重绘的变化（格子更新/手势/光标/贴图切换/视口移动） */
    private fun hasChange(): Boolean {
        if (!mapInit) return false // 绘制线程可能先于 initMap 启动
        return map.hasPendingTiles()
            || operationQueue.isNotEmpty()
            || lastCurse != map.curse
            || mapTransformDirty
            || nh.tileSet.hasTileSetChange()
    }

    private fun draw(): Boolean {
        holder?.apply {
            if (mapInit) {
                // 变化检测：没有更新内容/手势/光标/贴图变化 → 跳过本帧（不 lockCanvas）
                if (!hasChange()) {
                    return false
                }
                val canvas = lockCanvas()
                try {
                    val tileSetChanged = nh.tileSet.isTileSetChange()

                    if (tileSetChanged)
                        initMapParam() // 重建离屏并全量重绘
                    // 处理手势操作（拖动/缩放 → 只改视口，离屏不变）
                    while (operationQueue.isNotEmpty()) {
                        val op = operationQueue.pop()
                        if (op is NHMapTransform) {
                            mapTranslated = true
                            transformMapWithGesture(op.dx, op.dy)
                        }
                        if (op is NHMapScale)
                            scaleMap(op.scale, op.cx, op.cy)
                    }
                    // 玩家移动 → 视口跟随（用 clipAround 的 player 位置，curs 不一定更新）
                    if (lastFollowPlayer != map.player) {
                        transformMapWithMove(nh.prefs.walkRange)
                        lastFollowPlayer = Point(map.player)
                    }
                    val newRunMode = if (mapTranslated && nh.prefs.travelAfterPanned && !playerInWalkRange(nh.prefs.walkRange))
                        NHStatus.RunMode.TRAVEL
                    else
                        NHStatus.RunMode.WALK
                    if (nh.status.runMode != newRunMode) {
                        nh.status.runMode = newRunMode
                        // runMode 显示在状态栏条件里：变化时刷新状态栏，
                        // 否则要等下一回合 displayWindow 才更新（脱屏是纯 UI 操作，无 native 回合）
                        nh.binding.statusView.requestRedraw()
                    }

                    // 离屏更新：贴图切换/强制全量 → 重绘全部；否则只重绘变化的格子+光标新旧格
                    if (tileSetChanged || mapContentDirty) {
                        drawAllToOffscreen()
                        mapContentDirty = false
                    } else {
                        val changed = map.applyPendingTiles()
                        (changed + listOf(
                            Pair(lastCurse.x, lastCurse.y),
                            Pair(map.curse.x, map.curse.y)
                        ))
                            .filter { it.first in 0 until map.width && it.second in 0 until map.height }
                            .distinct()
                            .forEach { (x, y) -> drawOneToOffscreen(x, y) }
                    }
                    // 首次以玩家位置为中心（绘制时 view 已测量，measuredWidth 有效；
                    // 且 clipAround 已把玩家位置写入 map.player）
                    if (!playerCentered && map.player.x >= 0 && map.player.y >= 0) {
                        centerView(map.player.x, map.player.y)
                        playerCentered = true
                    }
                    // 屏幕输出：清背景 + 整图 blit（永远完整画面，无残影）+ 覆盖层
                    canvas?.drawColor(Color.BLACK)
                    blitMap(canvas)
                    if (nh.tileSet.isTTY()) {
                        drawAsciiCurse(canvas)
                    } else {
                        drawTileCurse(canvas)
                    }
                    drawLastTouchTile(canvas)
                    drawBorder(canvas)
                    // drawWalkRange(canvas)
                    drawIndicator(canvas)
                    lastCurse = Point(map.curse)
                    mapTransformDirty = false
                } finally {
                    if (canvas != null)
                        unlockCanvasAndPost(canvas)
                }
                return true
            }
        }
        return false
    }
    override fun run() {
        while (isDrawing) {
            // 无变化时挂起（最多 500ms 兜底，防通知丢失）；变化来源：displayWindow/curs/手势 signalAll
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

