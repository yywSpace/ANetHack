package com.yywspace.anethack.gameview.map

import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.floor

/**
 * 地图视口：当前可见世界区域与坐标换算。
 *
 * 只保存视口矩形、缩放、tile 尺寸和安全区，并负责屏幕坐标与 tile 坐标的换算。
 * 不涉及 Canvas、触摸事件或游戏窗口；镜头跟随由 MapCamera 负责。
 */
class NHMapProjection {

    @Volatile
    var worldDrawRect: RectF = RectF()
        private set

    var tileWidth: Float = 0f
        private set
    var tileHeight: Float = 0f
        private set
    var baseTileWidth: Float = 0f
        private set
    var baseTileHeight: Float = 0f
        private set

    var borderWidth: Float = 0f
        private set
    private var baseBorderWidth: Float = 0.5f

    /** 顶部状态栏、消息栏、底部键盘等覆盖的像素。 */
    val safeInsets: Rect = Rect()

    fun setBaseTileSize(width: Float, height: Float) {
        baseTileWidth = width
        baseTileHeight = height
    }

    fun updateFromCamera(
        camera: NHMapCamera,
        viewWidth: Int,
        viewHeight: Int,
        mapWidth: Int,
        mapHeight: Int
    ) {
        tileWidth = baseTileWidth * camera.zoom
        tileHeight = baseTileHeight * camera.zoom
        borderWidth = kotlin.math.ceil(baseBorderWidth * camera.zoom)
        val left = viewWidth / 2f - camera.centerX * tileWidth
        val top = viewHeight / 2f - camera.centerY * tileHeight
        worldDrawRect = RectF(
            left,
            top,
            left + mapWidth * tileWidth,
            top + mapHeight * tileHeight
        )
    }

    fun screenToWorld(x: Float, y: Float): PointF = PointF(
        (x - worldDrawRect.left) / tileWidth,
        (y - worldDrawRect.top) / tileHeight
    )

    fun worldToScreen(x: Float, y: Float): PointF = PointF(
        worldDrawRect.left + x * tileWidth,
        worldDrawRect.top + y * tileHeight
    )

    fun tileBorder(x: Int, y: Int): RectF = RectF(
        floor(tileWidth * x + worldDrawRect.left),
        floor(tileHeight * y + worldDrawRect.top),
        floor(tileWidth * (x + 1) + worldDrawRect.left),
        floor(tileHeight * (y + 1) + worldDrawRect.top)
    )

    fun tileLocation(vx: Float, vy: Float): Point {
        val tx = floor((vx - worldDrawRect.left) / tileWidth)
        val ty = floor((vy - worldDrawRect.top) / tileHeight)
        return Point(tx.toInt(), ty.toInt())
    }

}
