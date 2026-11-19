package com.yywspace.anethack.gameview.map

import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.floor

/**
 * 地图视口：当前可见世界区域与坐标换算。
 *
 * 只保存视口矩形、缩放、tile 尺寸和安全区，并负责屏幕坐标与 tile 坐标的换算。
 * 不涉及 Canvas、触摸事件或游戏窗口；镜头跟随由 MapCamera 负责。
 */
class NHMapViewport {

    @Volatile
    var border: RectF = RectF()

    /** 手势开始时记录的地图位置，拖动使用“起点 + 累计位移”。 */
    var lastGestureBorder: RectF = RectF()

    var scaleFactor: Float = 1f

    var tileWidth: Float = 0f
    var tileHeight: Float = 0f
    var baseTileWidth: Float = 0f
    var baseTileHeight: Float = 0f

    var borderWidth: Float = 0f
    var baseBorderWidth: Float = 0.5f

    /** 顶部状态栏、消息栏、底部键盘等覆盖的像素。 */
    val safeInsets: Rect = Rect()

    fun tileBorder(x: Int, y: Int): RectF = RectF(
        floor(tileWidth * x + border.left),
        floor(tileHeight * y + border.top),
        floor(tileWidth * (x + 1) + border.left),
        floor(tileHeight * (y + 1) + border.top)
    )

    fun tileLocation(vx: Float, vy: Float): Point {
        val tx = floor((vx - border.left) / tileWidth)
        val ty = floor((vy - border.top) / tileHeight)
        return Point(tx.toInt(), ty.toInt())
    }

    /** 普通拖动：从按下时的位置应用累计位移。 */
    fun transformGesture(dx: Float, dy: Float) {
        border = RectF(
            floor(lastGestureBorder.left + dx),
            floor(lastGestureBorder.top + dy),
            floor(lastGestureBorder.right + dx),
            floor(lastGestureBorder.bottom + dy)
        )
    }

    /** 直接按当前视口增量平移。 */
    fun transformBy(dx: Float, dy: Float) {
        border = RectF(border).apply { offset(dx, dy) }
    }
}
