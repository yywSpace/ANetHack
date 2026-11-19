package com.yywspace.anethack.gameview.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TextureView
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
import com.yywspace.anethack.gameview.NHPosKeyAdapter
import com.yywspace.anethack.gameview.map.indicator.NHMapIndicatorController
import com.yywspace.anethack.gameview.map.operation.NHMapCenter
import com.yywspace.anethack.gameview.map.operation.NHMapOperation
import com.yywspace.anethack.gameview.map.operation.NHMapScale
import com.yywspace.anethack.gameview.map.operation.NHMapTransform
import com.yywspace.anethack.window.NHWMap
import java.util.concurrent.CountDownLatch
import kotlin.math.abs
import kotlin.math.atan2


class NHMapView : TextureView, TextureView.SurfaceTextureListener {
    private val metrics = NHMapMetrics()
    private val coordinates = NHMapCoordinates(metrics)
    private val camera = NHMapCamera(metrics)
    /** 点击指示符后，下一次点地图直接 travel（不受 deadZonePercent 影响） */
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
    private lateinit var renderer: NHMapRenderer
    private lateinit var overlayRenderer: NHMapOverlayRenderer
    private val renderLoop by lazy { NHMapRenderLoop() }

    private lateinit var nh: NetHack
    private lateinit var world: NHMapWorld
    /** 地图四周被覆盖的像素（状态、消息、键盘等），由 Activity 计算后传入 */
    private val safeInsets: Rect = Rect()

    private var lastTouchTile:Point? = null

    /** 地图上的动画（箭头等），自己管理播放和结束 */
    private val animations by lazy {
        NHMapAnimationPlayer(
            scheduleFrame = renderLoop::postDelayed,
            requestRedraw = ::requestRedraw
        )
    }
    var lastTravelTile:Pair<String,Point>? = null
    private var lastCurse:Point = Point()
    /** 上次跟随的玩家位置，用于判断玩家是否移动 */
    private var lastFollowPlayer:Point = Point(-1, -1)
    /** 上次跟随的光标位置：选怪/查看时光标移动也要让视口跟上 */
    private var lastFollowCursor:Point = Point(-1, -1)
    /** 首次居中是否已做（绘制线程首帧执行，保证 view 已测量） */
    private var playerCentered = false
    /** 尺寸变化前玩家是否在屏幕内（键盘弹出/收起时据此决定要不要跟随） */
    private var playerWasOnScreen = true
    private lateinit var indicatorController:NHMapIndicatorController

    fun requestRedraw() {
        renderLoop.post { redraw() }
    }

    fun enqueueClear() {
        renderLoop.post {
            clearMap()
            redraw()
        }
    }

    fun enqueueFrame(frame: MapFrame) {
        renderLoop.post { drawFrame(frame) }
    }

    private fun enqueueOperation(op: NHMapOperation) {
        renderLoop.post {
            processOperation(op)
            redraw()
        }
    }

    private val inputController by lazy {
        NHMapInputController(
            tapHandler = ::handleMapTap,
            longPressHandler = ::handleMapLongPress,
            moveHandler = ::handleMapMove,
            scaleHandler = ::handleMapScale
        )
    }

    private fun handleMapTap(e: PointF) {
        if (indicatorController.onIndicatorClick(e))
            return
        lastTouchTile = getTileLocation(e.x, e.y).also { point ->
            val origin = getTileBorder(world.curse.x, world.curse.y)
            val direction = getMoveDirection(
                PointF(origin.centerX(), origin.centerY()),
                PointF(e.x, e.y)
            )
            showArrow(direction.dx, direction.dy)
            if (nh.status.runMode == NHStatus.RunMode.TRAVEL) {
                travelTo(point)
            } else {
                val curseBorder = getTileBorder(world.curse.x, world.curse.y)
                val moveDirection = getMoveDirection(
                    PointF(curseBorder.centerX(), curseBorder.centerY()),
                    PointF(e.x, e.y)
                )
                playerMove(moveDirection, false)
            }
        }
        requestRedraw()
    }

    private fun handleMapLongPress(e: PointF) {
        if (indicatorController.onIndicatorLongPress(e))
            return
        lastTouchTile = getTileLocation(e.x, e.y).also { point ->
            if (abs(world.curse.x - point.x) < 1 && abs(world.curse.y - point.y) < 1) {
                nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.TRAVEL))
            } else {
                val border = getTileBorder(point.x, point.y)
                showPopupWindow(border.centerX(), border.centerY())
            }
        }
        requestRedraw()
    }

    private fun handleMapMove(e1: PointF, e2: PointF) {
        enqueueOperation(NHMapTransform(e2.x - e1.x, e2.y - e1.y))
        mapTranslated = true
        updateRunMode()
    }

    private fun handleMapScale(scaleFactor: Float, cx: Float, cy: Float) {
        enqueueOperation(NHMapScale(scaleFactor, cx, cy))
    }

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    init {
        initView()
    }

    private fun initView() {
        surfaceTextureListener = this
        isOpaque = true
        isFocusable = true
        // isFocusableInTouchMode = true
        this.keepScreenOn = true
    }

    fun initMap(nh: NetHack, map: NHWMap) {
        this.nh = nh
        this.world = map.world
        renderer = NHMapRenderer(nh)
        overlayRenderer = NHMapOverlayRenderer(nh, renderer, animations)
        // View 已经布局完成时立即初始化；否则等 onSizeChanged 第一次拿到尺寸
        if (isLaidOut && measuredWidth > 0 && measuredHeight > 0)
            initMapParam()
        initIndicators()
        mapReady.countDown()
        startRenderLoopIfReady()
    }

    private fun startRenderLoopIfReady() {
        if (isAvailable && mapInit) {
            renderLoop.start()
            requestRedraw()
        }
    }

    private fun initMapParam() {
        nh.tileSet.updateTileSet()
        renderer.initialize(world, coordinates)
        camera.setZoom(
            measuredWidth.toFloat() /
                    (world.height * coordinates.baseTileHeight).coerceAtLeast(1f)
        )
        camera.centerOn(world.curse.x.toFloat(), world.curse.y.toFloat())
        metrics.setViewport(measuredWidth, measuredHeight, world.width, world.height)
        coordinates.updateFromCamera(camera)
        // 重建 bitmap 后必须全量绘制，并在首帧重新居中
        renderer.markDirty()
        playerCentered = false
    }

    private fun initIndicators() {
        indicatorController = NHMapIndicatorController(this, nh, world)
        indicatorController.setOnIndicatorClickListener(object :NHMapIndicatorController.OnIndicatorClickListener{
            override fun onIndicatorClick(e: PointF, tile: NHMapWorld.Tile) {
                // 点指示符，下一次点地图要强制TRAVEL
                travelArmed = true
                // 视口被移动到别处，这也是"地图平移过"
                mapTranslated = true
                enqueueOperation(NHMapCenter(tile.x, tile.y))
                // 清除上一次点击留下的灰色定位框，避免居中后它出现在错误位置
                lastTouchTile = null
                // 立即把状态栏切到 TRAVEL，不等绘制线程下一帧
                updateRunMode()
            }

            override fun onIndicatorLongPress(e: PointF, tile: NHMapWorld.Tile) {

            }

        })
    }

    /** 角色是否还在 deadZonePercent 死区内（跟随模式下就不该把点击当成 travel） */
    private fun playerInDeadZone(deadZonePercent: Int): Boolean =
        camera.tileInDeadZone(
            getTileBorder(world.curse.x, world.curse.y),
            safeBounds(),
            deadZonePercent
        )

    /**
     * 刷新运行模式（WALK/TRAVEL）。点指示符、拖动视图后立即调用，不等线程绘制下一帧。
     *
     * 只有"地图被平移过"（[mapTranslated]）且"角色已经不在 deadZonePercent 里"
     * （[playerInDeadZone]）时，点地图才解释成 travel；否则是 walk。
     * deadZonePercent = 0 时矩形退化成一个点，角色"不在范围内"，
     * 但只要没拖动过地图，[mapTranslated] 仍是 false，所以点击还是 walk。
     */
    private fun updateRunMode() {
        val newRunMode = if (travelArmed
            || (nh.prefs.travelAfterPanned && mapTranslated && !playerInDeadZone(nh.prefs.deadZonePercent)))
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
                                    val tileBorder = getTileBorder(world.curse.x, world.curse.y)
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
            showAtLocation(this@NHMapView, Gravity.NO_GRAVITY,
                (x-contentView.measuredWidth /2 ).toInt(), (y-contentView.measuredHeight-coordinates.tileHeight/2).toInt())
        }
    }

    private fun transformMapWithGesture(dx: Float, dy: Float) {
        camera.panByScreen(dx, dy)
        coordinates.updateFromCamera(camera)
    }

    private fun scaleMap(scaleFactor: Float, centerX: Float, centerY: Float) {
        val anchor = coordinates.screenToWorld(centerX, centerY)
        camera.zoomBy(scaleFactor)
        coordinates.updateFromCamera(camera)
        val screen = coordinates.worldToScreen(anchor.x, anchor.y)
        camera.panByScreen(screen.x - centerX, screen.y - centerY)
        coordinates.updateFromCamera(camera)
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
        val playerBorder = getTileBorder(world.curse.x, world.curse.y)
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

    /** 当前未被覆盖的安全区域（View 坐标系） */
    fun safeBounds(): RectF = RectF(
        safeInsets.left.toFloat(),
        safeInsets.top.toFloat(),
        measuredWidth.toFloat() - safeInsets.right,
        measuredHeight.toFloat() - safeInsets.bottom
    )

    /** 设置地图四周被遮挡的区域，镜头跟随会避开这些区域 */
    fun setSafeInsets(left: Int, top: Int, right: Int, bottom: Int) {
        val changed = left != safeInsets.left || top != safeInsets.top
                || right != safeInsets.right || bottom != safeInsets.bottom
        safeInsets.set(left, top, right, bottom)
        // 布局回调会重复传相同边距；只有真正变化时才重新跟随居中
        if (mapInit && changed) {
            // 键盘/面板高度变化时始终按当前光标重新适配安全区，
            transformMapWithMove(nh.prefs.deadZonePercent)
            requestRedraw()
        }
    }

    /** 只更新顶部安全区，底部保持不变。 */
    fun setTopSafeInset(top: Int) {
        setSafeInsets(safeInsets.left, top, safeInsets.right, safeInsets.bottom)
    }

    /** 只更新底部安全区，顶部保持不变。 */
    fun setBottomSafeInset(bottom: Int) {
        setSafeInsets(safeInsets.left, safeInsets.top, safeInsets.right, bottom)
    }

    fun centerPlayerInScreen() {
        if (mapInit)
            enqueueOperation(NHMapCenter(world.curse.x, world.curse.y))
    }

    /** 人物是否已经显示在屏幕里（用于 travel 前决定要不要居中） */
    private fun playerOnScreen(width:Int = measuredWidth, height:Int = measuredHeight): Boolean {
        if (!mapInit || world.curse.x < 0 || world.curse.y < 0)
            return true
        val tb = getTileBorder(world.curse.x, world.curse.y)
        return tb.right > 0 && tb.left < width && tb.bottom > 0 && tb.top < height
    }

    /** 统一的 travel 入口：人物不在屏幕里时先居中，发完指令记录目的地并退出 travel 模式 */
    private fun travelTo(point: Point) {
        if (!playerOnScreen())
            centerPlayerInScreen()
        nh.command.sendCommand(NHPosCommand(point.x, point.y, PosMod.TRAVEL))
        if (point.x in 1 until world.width && point.y in 1 until world.height)
            lastTravelTile = Pair(nh.status.dungeonLevel.realVal, point)
        travelArmed = false
        // travel 已经发出，地图平移带来的"点即 travel"意图用完就清掉，回到 walk
        mapTranslated = false
        updateRunMode()
    }

    fun centerView(x: Int, y: Int) {
        camera.centerOn(x.toFloat(), y.toFloat())
        coordinates.updateFromCamera(camera)
        requestRedraw()
    }

    /**
     * 按 deadZonePercent 死区跟随玩家/光标。
     *
     * 只负责取得目标格子的屏幕位置和安全区，实际死区计算与镜头平移由
     * [NHMapCamera.follow] 完成；移动后重新计算投影。
     */
    private fun transformMapWithMove(
        deadZonePercent: Int,
        curse: Point = world.curse
    ) {
        if (curse.x < 0 || curse.y < 0) return
        val tb = getTileBorder(curse.x, curse.y)
        val moved = camera.follow(
            tb.centerX(), tb.centerY(),
            safeBounds(), deadZonePercent
        )
        if (moved) {
            coordinates.updateFromCamera(camera)
        }
    }


    private fun showArrow(dx: Float, dy: Float) {
        if (!nh.prefs.playAnimations) return
        if (dx == 0f && dy == 0f) return
        // 每次点击只保留最新箭头
        animations.playExclusive(NHMoveArrow(
            { world.curse },
            { nh.status.hitPoints.color },
            dx, dy
        ))
    }

    fun getTileBorder(x: Int, y: Int): RectF = coordinates.tileBorder(x, y)

    // 获取界面TileLocation
    private fun getTileLocation(vx: Float, vy: Float): Point =
        coordinates.tileLocation(vx, vy)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return !mapInit || inputController.onTouch(this, event)
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
        // 首次布局：mapBitmap 为空说明 initMapParam 还没有真正初始化过
        if (mapInit && ::renderer.isInitialized && !renderer.hasBitmap() && w > 0 && h > 0) {
            initMapParam()
            requestRedraw()
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (::renderer.isInitialized) renderer.markDirty()
        startRenderLoopIfReady()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (::renderer.isInitialized) renderer.markDirty()
        if (mapInit) {
            metrics.setViewport(width, height, world.width, world.height)
            coordinates.updateFromCamera(camera)
        }
        requestRedraw()
        if (mapInit && playerWasOnScreen)
            transformMapWithMove(nh.prefs.deadZonePercent)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderLoop.stop()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    /** 是否有需要重绘的变化（格子更新/手势/光标/贴图切换/视口移动） */
    private fun processOperation(op: NHMapOperation) {
        when (op) {
            is NHMapTransform -> transformMapWithGesture(op.dx, op.dy)
            is NHMapScale -> scaleMap(op.scale, op.cx, op.cy)
            is NHMapCenter -> {
                centerView(op.x, op.y)
                lastFollowCursor = Point(world.curse)
            }
        }
    }

    private fun drawFrame(frame: MapFrame) {
        if (!isAvailable || !mapInit) return

        val canvas = lockCanvas() ?: return
        try {
            val tileSetChanged = nh.tileSet.isTileSetChange()
            if (tileSetChanged) {
                initMapParam()
            }

            val renderCurse = frame.curse
            // 玩家移动跟随玩家；只有光标离开玩家（look）时才跟随光标。
            val followPlayer = frame.player
            val followChanged = lastFollowPlayer != followPlayer ||
                    (renderCurse != followPlayer && lastFollowCursor != renderCurse)
            if (followChanged) {
                transformMapWithMove(nh.prefs.deadZonePercent, renderCurse)
                lastFollowPlayer = Point(followPlayer)
                lastFollowCursor = Point(renderCurse)
            }
            if ((tileSetChanged || !playerCentered) &&
                renderCurse.x > 0 && renderCurse.y > 0
            ) {
                centerView(renderCurse.x, renderCurse.y)
                playerCentered = true
            }
            updateRunMode()

            renderer.drawFrame(canvas, world, coordinates, frame)
            drawOverlays(canvas, renderCurse)
            lastCurse = Point(renderCurse)
        } finally {
            unlockCanvasAndPost(canvas)
        }
    }

    private fun redraw() {
        if (!isAvailable || !mapInit) return
        val canvas = lockCanvas() ?: return
        try {
            val tileSetChanged = nh.tileSet.isTileSetChange()
            if (tileSetChanged) {
                initMapParam()
                if (lastCurse.x > 0 && lastCurse.y > 0) {
                    centerView(lastCurse.x, lastCurse.y)
                    playerCentered = true
                }
            }
            renderer.redraw(canvas, world, coordinates)
            drawOverlays(canvas, lastCurse)
        } finally {
            unlockCanvasAndPost(canvas)
        }
    }

    private fun clearMap() {
        world.clearTiles()
        playerCentered = false
        renderer.markDirty()
        travelArmed = false
        mapTranslated = false
    }

    /** 只绘制屏幕覆盖层：光标、点击框、动画、边框、指示符。 */
    private fun drawOverlays(canvas: Canvas?, drawCurse: Point) {
        canvas ?: return
        overlayRenderer.draw(
            canvas, world, coordinates, drawCurse, lastTouchTile,
            indicatorController, camera.zoom
        )
    }


}
