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
import com.yywspace.anethack.map.operation.NHMapCenter
import com.yywspace.anethack.map.operation.NHMapOperation
import com.yywspace.anethack.map.operation.NHMapScale
import com.yywspace.anethack.map.operation.NHMapTransform
import com.yywspace.anethack.window.MapFrame
import com.yywspace.anethack.window.NHWMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import androidx.core.graphics.createBitmap


class NHMapSurfaceView: SurfaceView, SurfaceHolder.Callback,Runnable {
    private var textSize = 64f
    private var scaleFactor = 1f
    /** 点击指示符后，下一次点地图直接 travel（不受 walkRange 影响） */
    private var travelArmed = false
    /**
     * 地图是否被平移过（拖动视口、点指示符跳转）。
     *
     * 设置 travelAfterPanned 打开时，只有它为 true 才把点地图解释成 travel，
     * 否则一直是 walk；发过一次 travel 后复位，回到"正常走"的状态。
     */
    private var mapTranslated = false
    private val mapReady = CountDownLatch(1)
    private val mapInit: Boolean
        get() = mapReady.count == 0L
    private val paint = Paint()
    private val asciiPaint = TextPaint()

    private lateinit var nh: NetHack
    private lateinit var map: NHWMap
    @Volatile
    private var mapBorder:RectF = RectF()
    private var lastMapBorder:RectF = RectF()
    /** 地图四周被覆盖的像素（状态、消息、键盘等），由 Activity 计算后传入 */
    private val safeInsets = Rect()

    private var lastTouchTile:Point? = null

    /** 地图上的动画（箭头等），自己管理播放和结束 */
    private val animations = NHMapAnimationPlayer()
    var lastTravelTile:Pair<String,Point>? = null
    private var lastCurse:Point = Point()
    /** 上次跟随的玩家位置，用于判断玩家是否移动 */
    private var lastFollowPlayer:Point = Point(-1, -1)
    /** 上次跟随的光标位置：选怪/查看时光标移动也要让视口跟上 */
    private var lastFollowCursor:Point = Point(-1, -1)
    private var baseBorderWidth:Float = .5F
    private var borderWidth:Float = 0F
    /** 光标半透明填充的 alpha（0-255），贴图模式下光标为半透明方块 */
    private val curseFillAlpha = 0x26
    private var tileWidth:Float = 0F
    private var tileHeight:Float = 0F
    /** 离屏地图缓冲：基础尺寸（不随缩放变），脏格子只更新这里，屏幕整图 blit 杜绝残影 */
    private var baseTileWidth = 0f
    private var baseTileHeight = 0f
    private var mapBitmap: Bitmap? = null
    private var mapCanvas: Canvas? = null
    /** 需要全量重绘离屏缓冲（surface 重建/首帧） */
    private var mapContentDirty = false
    /** 首次居中是否已做（绘制线程首帧执行，保证 view 已测量） */
    private var playerCentered = false
    /** 尺寸变化前玩家是否在屏幕内（键盘弹出/收起时据此决定要不要跟随） */
    private var playerWasOnScreen = true
    private var isDrawing = false
    private lateinit var indicatorController:NHMapIndicatorController

    /** 绘制线程的唯一工作来源：帧就绪、手势操作、失效重绘。 */
    private sealed interface MapWork {
        data class Operation(val op: NHMapOperation) : MapWork
        data class Frame(val frame: MapFrame) : MapWork
        object Invalidate : MapWork
    }

    private val workQueue = LinkedBlockingDeque<MapWork>()

    fun requestRedraw() {
        workQueue.offer(MapWork.Invalidate)
    }

    fun enqueueFrame(frame: MapFrame) {
        synchronized(workQueue) {
            val frames = workQueue.filterIsInstance<MapWork.Frame>()
            if (frames.size >= 4) {
                // 队列过长：合并最旧帧的 tile，保留最新镜头位置
                val old = frames.first()
                workQueue.remove(old)
                workQueue.offer(MapWork.Frame(
                    MapFrame(old.frame.tiles + frame.tiles, frame.curse)
                ))
            } else {
                workQueue.offer(MapWork.Frame(frame))
            }
        }
    }

    private var mapTouchListener: NHMapTouchListener = NHMapTouchListener().apply {
        onNHMapTouchListener = object : NHMapTouchListener.OnNHMapTouchListener {
            override fun onDown(e: PointF) {
                lastMapBorder = RectF(mapBorder)
            }

            override fun onClick(e: PointF) {
                // 居中操作还没被渲染线程处理时，坐标还是旧的，先忽略这次点击
                if (workQueue.any { it is MapWork.Operation && it.op is NHMapCenter }) return
                if(indicatorController.onIndicatorClick(e))
                    return
                lastTouchTile = getTileLocation(e.x, e.y).also { point ->
                    val origin = getTileBorder(map.curse.x, map.curse.y)
                    val direction = getMoveDirection(
                        PointF(origin.centerX(), origin.centerY()),
                        PointF(e.x, e.y)
                    )
                    showArrow(direction.dx, direction.dy)
                    if (nh.status.runMode == NHStatus.RunMode.TRAVEL) {
                        travelTo(point)
                    }else {
                        val curseBorder = getTileBorder(map.curse.x, map.curse.y)
                        val direction = getMoveDirection(
                            PointF(curseBorder.centerX(), curseBorder.centerY()),
                            PointF(e.x, e.y)
                        )
                        playerMove(direction, false)
                    }
                }
                // 点击框（lastTouchTile）变化：触发一次绘制刷新覆盖层
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
                    // long click yourself: this is a left click on the hero (CLICK_1),
                    // NetHack answers with its "What do you want to do?" self menu
                    if (abs(map.curse.x - point.x) < 1 && abs(map.curse.y - point.y) < 1) {
                        nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.TRAVEL))
                    } else {
                        // long click other
                        val border = getTileBorder(point.x, point.y)
                        showPopupWindow(border.centerX(), border.centerY())
                    }
                }
                requestRedraw()
            }
            override fun onMove(e1: PointF, e2: PointF) {
                workQueue.offer(MapWork.Operation(NHMapTransform(e2.x - e1.x, e2.y - e1.y)))
                // 用户平移了地图
                mapTranslated = true
                updateRunMode()
            }

            override fun onScale(scaleFactor: Float, cx: Float, cy: Float) {
                workQueue.offer(MapWork.Operation(NHMapScale(scaleFactor, cx, cy)))
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
        mapReady.countDown()
        // 首帧强制全量重绘离屏 + 唤醒
        mapContentDirty = true
        playerCentered = false
    }

    /** 地图清空（换楼层等）：下一帧以光标为中心重新居中 + 全量重绘离屏。 */
    fun onMapCleared() {
        playerCentered = false
        mapContentDirty = true
        // 换楼层后旧地图上置的 travel 意图作废
        travelArmed = false
        mapTranslated = false
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
                // 点指示符，下一次点地图要强制TRAVEL
                travelArmed = true
                // 视口被移动到别处，这也是"地图平移过"
                mapTranslated = true
                workQueue.offer(MapWork.Operation(NHMapCenter(tile.x, tile.y)))
                // 清除上一次点击留下的灰色定位框，避免居中后它出现在错误位置
                lastTouchTile = null
                // 立即把状态栏切到 TRAVEL，不等绘制线程下一帧
                updateRunMode()
            }

            override fun onIndicatorLongPress(e: PointF, tile: NHWMap.Tile) {

            }

        })
    }

    /** 角色是否还在 walkRange 矩形里（跟随模式下就不该把点击当成 travel） */
    private fun playerInWalkRange(walkRange: Int):Boolean {
        val left = safeInsets.left.toFloat()
        val top = safeInsets.top.toFloat()
        val right = measuredWidth.toFloat() - safeInsets.right
        val bottom = measuredHeight.toFloat() - safeInsets.bottom
        if (right <= left || bottom <= top)
            return true
        val width = right - left
        val height = bottom - top
        val offsetX = (1 - walkRange / 100f) * width / 2f
        val offsetY = (1 - walkRange / 100f) * height / 2f
        val playerBorder = getTileBorder(map.curse.x, map.curse.y)
        val cx = playerBorder.centerX()
        val cy = playerBorder.centerY()
        val px = if (cx > left + width/2) playerBorder.left else playerBorder.right
        val py = if (cy > top + height/2) playerBorder.top else playerBorder.bottom
        return px > left + offsetX && py > top + offsetY &&
                px < right - offsetX && py < bottom - offsetY
    }

    /**
     * 刷新运行模式（WALK/TRAVEL）。点指示符、拖动视图后立即调用，不等线程绘制下一帧。
     *
     * 只有"地图被平移过"（[mapTranslated]）且"角色已经不在 walkRange 里"
     * （[playerInWalkRange]）时，点地图才解释成 travel；否则是 walk。
     * walkRange = 0 时矩形退化成一个点，角色"不在范围内"，
     * 但只要没拖动过地图，[mapTranslated] 仍是 false，所以点击还是 walk。
     */
    private fun updateRunMode() {
        val newRunMode = if (travelArmed
            || (nh.prefs.travelAfterPanned && mapTranslated && !playerInWalkRange(nh.prefs.walkRange)))
            NHStatus.RunMode.TRAVEL
        else
            NHStatus.RunMode.WALK
        if (nh.status.runMode != newRunMode) {
            nh.status.runMode = newRunMode
            // runMode 显示在状态栏条件里，变化时刷新状态栏，
            nh.binding.statusView.requestRedraw()
        }
    }

    @SuppressLint("InflateParams")
    fun showPopupWindow(x:Float, y:Float) {
        val posKeyList = listOf("Look", "Run", "Travel")
        val popupWindow = PopupWindow(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val recyclerView = LayoutInflater.from(context).inflate(R.layout.popup_window_pos_key, null).apply {
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
                                    travelTo(point)
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
        mapBorder = RectF(
            floor(lastMapBorder.left + dx),
            floor(lastMapBorder.top + dy),
            floor(lastMapBorder.right + dx),
            floor(lastMapBorder.bottom + dy)
        )
    }

    private fun transformMap(dx:Float, dy:Float) {
        mapBorder = RectF(mapBorder).apply { offset(dx, dy) }
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
        val newLeft = mapBorder.left + (mapBorder.left - centerX) * (scaleFactor - 1)
        val newTop = mapBorder.top + (mapBorder.top - centerY) * (scaleFactor - 1)
        mapBorder = RectF(
            newLeft,
            newTop,
            newLeft + map.width * tileWidth,
            newTop + map.height * tileHeight
        )
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
    /** 设置地图四周被遮挡的区域，镜头跟随会避开这些区域 */
    fun setSafeInsets(left: Int, top: Int, right: Int, bottom: Int) {
        val changed = left != safeInsets.left || top != safeInsets.top
                || right != safeInsets.right || bottom != safeInsets.bottom
        safeInsets.set(left, top, right, bottom)
        // 布局回调会重复传相同边距；只有真正变化时才重新跟随居中
        if (mapInit && changed) {
            if (!mapTranslated)
                transformMapWithMove(nh.prefs.walkRange)
            requestRedraw()
        }
    }

    fun centerPlayerInScreen() {
        if (mapInit)
            workQueue.offer(MapWork.Operation(NHMapCenter(map.curse.x, map.curse.y)))
    }

    /** 人物是否已经显示在屏幕里（用于 travel 前决定要不要居中） */
    private fun playerOnScreen(width:Int = measuredWidth, height:Int = measuredHeight): Boolean {
        if (!mapInit || map.curse.x < 0 || map.curse.y < 0)
            return true
        val tb = getTileBorder(map.curse.x, map.curse.y)
        return tb.right > 0 && tb.left < width && tb.bottom > 0 && tb.top < height
    }

    /** 统一的 travel 入口：人物不在屏幕里时先居中，发完指令记录目的地并退出 travel 模式 */
    private fun travelTo(point: Point) {
        if (!playerOnScreen())
            centerPlayerInScreen()
        nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.TRAVEL))
        if (point.x in 1 until map.width && point.y in 1 until map.height)
            lastTravelTile = Pair(nh.status.dungeonLevel.realVal, point)
        travelArmed = false
        // travel 已经发出，地图平移带来的"点即 travel"意图用完就清掉，回到 walk
        mapTranslated = false
        updateRunMode()
    }

    fun centerView(x:Int, y:Int) {
        val tb = getTileBorder(x,y)
        // 只按地图 View 自身尺寸居中；根布局还包含键盘/面板，不能拿来算中心
        val viewHeight = measuredHeight.takeIf { it > 0 }
            ?: nh.binding.root.height
        transformMap(-(tb.centerX() - measuredWidth / 2F), -(tb.centerY() - viewHeight / 2F))
    }

    private fun transformMapWithMove(
        walkRange: Int,
        curse: Point = map.curse
    ) {
        val tb = getTileBorder(curse.x, curse.y)
        var dx = 0f
        var dy = 0f
        val px = tb.centerX()
        val py = tb.centerY()
        val left = safeInsets.left.toFloat()
        val top = safeInsets.top.toFloat()
        val right = measuredWidth.toFloat() - safeInsets.right
        val bottom = measuredHeight.toFloat() - safeInsets.bottom
        if (right <= left || bottom <= top)
            return
        val width = right - left
        val height = bottom - top
        val offsetX = (1 - walkRange / 100f) * width / 2f
        val offsetY = (1 - walkRange / 100f) * height / 2f
        if (px < left + offsetX)
            dx = left + offsetX - px
        if (px > right - offsetX)
            dx = right - offsetX - px
        if (py < top + offsetY)
            dy = top + offsetY - py
        if (py > bottom - offsetY)
            dy = bottom - offsetY - py
        // 视口没动（光标在安全区内）时不触发 transformMap，保持增量绘制
        if (dx != 0f || dy != 0f) {
            transformMap(dx, dy)
        }
    }

    private fun drawAsciiCurse(canvas: Canvas?, drawCurse: Point) {
        map.apply {
            if(drawCurse.x < 0 || drawCurse.y < 0)
                return
            val tile = getTile(drawCurse.x, drawCurse.y)
            val tb = getTileBorder(drawCurse.x, drawCurse.y)
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

    private fun drawTileCurse(canvas: Canvas?, drawCurse: Point) {
        map.apply {
            if(drawCurse.x < 0 || drawCurse.y < 0)
                return
            val tb = getTileBorder(drawCurse.x, drawCurse.y)
            // 半透明填充
            paint.style = Paint.Style.FILL
            paint.color = (nh.status.hitPoints.color and 0x00FFFFFF) or (curseFillAlpha shl 24)
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

    /** 播放当前还没结束的动画（箭头等） */
    private fun drawAnimations(canvas: Canvas?) {
        if (!nh.prefs.playAnimations) {
            animations.clear()
            return
        }
        if (!animations.isActive) return
        val frame = NHMapAnimationFrame(
            tileWidth, tileHeight,
            mapBorder.left, mapBorder.top
        )
        animations.draw(canvas ?: return, frame, paint, System.currentTimeMillis())
    }

    private fun showArrow(dx: Float, dy: Float) {
        if (!nh.prefs.playAnimations) return
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length <= 0f) return
        animations.clear()   // 每次点击只保留最新箭头
        animations.add(NHMoveArrow(
            { map.curse },
            { nh.status.hitPoints.color },
            dx / length, dy / length
        ))
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(mapInit)
            return mapTouchListener.onTouch(this, event)
        return true
    }

    enum class Direction(val dx: Float, val dy: Float) {
        UP(0f, -1f),
        DOWN(0f, 1f),
        LEFT(-1f, 0f),
        RIGHT(1f, 0f),
        LEFT_UP(-1f, -1f),
        LEFT_DOWN(-1f, 1f),
        RIGHT_UP(1f, -1f),
        RIGHT_DOWN(1f, 1f),
        CENTER(0f, 0f),
        UNDEFINE(0f, 0f);
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 尺寸变化前的玩家可见性，键盘顶起/收起时据此决定要不要跟随
        if (mapInit && oldw > 0 && oldh > 0)
            playerWasOnScreen = playerOnScreen(oldw, oldh)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isDrawing = true
        // surface 重建（切后台返回/设置返回）后画布清空，必须无条件重绘一帧，
        // 否则事件驱动下没有变化源通知绘制线程 → 黑屏
        mapContentDirty = true
        Thread(this).start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // 键盘顶起/收起、旋转都会改变地图尺寸：先重绘一帧，
        // 尺寸变化前玩家在屏幕里才跟随一次，否则会把玩家手动拖走的视口拽回来
        requestRedraw()
        if (mapInit && playerWasOnScreen)
            transformMapWithMove(nh.prefs.walkRange)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isDrawing = false
    }

    /** 是否有需要重绘的变化（格子更新/手势/光标/贴图切换/视口移动） */
    private fun processOperation(op: NHMapOperation) {
        when (op) {
            is NHMapTransform -> transformMapWithGesture(op.dx, op.dy)
            is NHMapScale -> scaleMap(op.scale, op.cx, op.cy)
            is NHMapCenter -> {
                centerView(op.x, op.y)
                lastFollowCursor = Point(map.curse)
            }
        }
    }

    private fun draw(frame: MapFrame?): Boolean {
        holder?.apply {
            if (mapInit) {
                val canvas = lockCanvas()
                try {
                    val tileSetChanged = nh.tileSet.isTileSetChange()
                    val renderCurse = frame?.curse ?: map.curse

                    if (tileSetChanged) {
                        initMapParam() // 重建离屏并全量重绘
                        // 切换贴图后视野重新居中到角色（贴图尺寸变化会让原视口偏移）
                        if (renderCurse.x >= 0 && renderCurse.y >= 0)
                            centerView(renderCurse.x, renderCurse.y)
                    }
                    // 玩家移动跟随玩家；只有光标离开玩家（look）时才跟随光标。
                    // look 退出时光标回到玩家，不触发镜头移动，视口保持在查看位置。
                    val followPlayer = map.player
                    val followChanged = lastFollowPlayer != followPlayer ||
                            (renderCurse != followPlayer && lastFollowCursor != renderCurse)
                    if (followChanged) {
                        transformMapWithMove(nh.prefs.walkRange, renderCurse)
                        lastFollowPlayer = Point(followPlayer)
                        lastFollowCursor = Point(renderCurse)
                    }
                    // 首次以玩家位置为中心
                    if (!playerCentered && renderCurse.x >= 0 && renderCurse.y >= 0) {
                        centerView(renderCurse.x, renderCurse.y)
                        playerCentered = true
                    }
                    updateRunMode()
                    // 地图层和覆盖层分开绘制
                    drawMapLayer(canvas, frame, tileSetChanged)
                    drawOverlays(canvas, renderCurse)
                    lastCurse = Point(renderCurse)
                } finally {
                    if (canvas != null)
                        unlockCanvasAndPost(canvas)
                }
                return true
            }
        }
        return false
    }

    /** 绘制地图层：有 MapFrame 时应用它，再更新并贴上离屏地图。 */
    private fun drawMapLayer(canvas: Canvas?, frame: MapFrame?, tileSetChanged: Boolean) {
        if (frame != null)
            map.applyFrame(frame)

        if (tileSetChanged || mapContentDirty) {
            drawAllToOffscreen()
            mapContentDirty = false
        } else frame?.tiles?.map { Pair(it.x, it.y) }?.filter { it.first in 0 until map.width && it.second in 0 until map.height }
            ?.distinct()?.forEach { (x, y) -> drawOneToOffscreen(x, y) }

        canvas?.drawColor(Color.BLACK)
        blitMap(canvas)
    }

    /** 只绘制屏幕覆盖层：光标、点击框、动画、边框、指示符。 */
    private fun drawOverlays(canvas: Canvas?, drawCurse: Point) {
        if (canvas == null) return
        if (nh.tileSet.isTTY()) {
            drawAsciiCurse(canvas, drawCurse)
        } else {
            drawTileCurse(canvas, drawCurse)
        }
        drawLastTouchTile(canvas)
        drawAnimations(canvas)
        drawBorder(canvas)
        drawIndicator(canvas)
    }

    /** 只重画当前离屏地图和覆盖层 */
    private fun drawAnimationFrame() {
        holder?.apply {
            if (mapContentDirty)
                return
            val canvas = lockCanvas() ?: return
            try {
                canvas.drawColor(Color.BLACK)
                blitMap(canvas)
                drawOverlays(canvas, lastCurse)
            } finally {
                unlockCanvasAndPost(canvas)
            }
        }
    }

    override fun run() {
        while (isDrawing) {
            if (!mapInit) {
                // 地图尚未初始化：不要消费 workQueue，等待 initMap 完成
                mapReady.await(500, TimeUnit.MILLISECONDS)
                continue
            }

            // 动画期间用短超时，空闲时等队列事件
            val timeout = if (animations.isActive) 16L else 500L
            val first = workQueue.poll(timeout, TimeUnit.MILLISECONDS)
            // 队列空闲时活跃动画补一帧；下一轮 poll 的 16ms 超时负责限速
            if (first == null) {
                if (animations.isActive)
                    drawAnimationFrame()
                continue
            }

            var work: MapWork? = first
            while (work != null) {
                when (work) {
                    is MapWork.Operation -> processOperation(work.op)
                    is MapWork.Invalidate -> Unit
                    is MapWork.Frame -> break // 一帧只消费一个 MapFrame
                }
                work = workQueue.poll()
            }

            // break 时 work 仍指向刚取到的 Frame；没有 Frame 则循环正常结束为 null
            draw((work as? MapWork.Frame)?.frame)
        }
    }
}
