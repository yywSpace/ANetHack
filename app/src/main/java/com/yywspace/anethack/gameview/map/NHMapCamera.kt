package com.yywspace.anethack.gameview.map

import android.graphics.RectF

/**
 * 地图镜头：决定“看哪里”。
 *
 * 世界坐标固定，镜头只保存观察中心与缩放。
 * 视口换算和绘制由 [NHMapCoordinates] 与渲染器负责。
 */
class NHMapCamera {
    var centerX: Float = 0f
    var centerY: Float = 0f
    var zoom: Float = 1f

    fun centerOn(x: Float, y: Float) {
        centerX = x
        centerY = y
    }

    /**
     * 按屏幕像素位移平移镜头。
     *
     * 方向与拖动内容相反：手指向右拖时 dx > 0，镜头中心左移，
     * 屏幕上的地图看起来跟着手指向右移动。
     *
     * @param dx        屏幕 X 位移（像素）
     * @param dy        屏幕 Y 位移（像素）
     * @param tileWidth 当前屏幕上一个 tile 的宽度
     * @param tileHeight 当前屏幕上一个 tile 的高度
     */
    fun panByScreen(dx: Float, dy: Float, tileWidth: Float, tileHeight: Float) {
        if (tileWidth <= 0f || tileHeight <= 0f) return
        centerX -= dx / tileWidth
        centerY -= dy / tileHeight
    }

    /**
     * 计算当前 walkRange 对应的跟随死区（屏幕坐标）。
     *
     * walkRange = 100 时死区等于整个安全区；0 时退化为安全区中心点。
     */
    fun deadZone(safeBounds: RectF, walkRange: Int): RectF {
        val range = walkRange.coerceIn(0, 100)
        val offsetX = (1 - range / 100f) * safeBounds.width() / 2f
        val offsetY = (1 - range / 100f) * safeBounds.height() / 2f
        return RectF(
            safeBounds.left + offsetX,
            safeBounds.top + offsetY,
            safeBounds.right - offsetX,
            safeBounds.bottom - offsetY
        )
    }

    /**
     * 让目标屏幕位置回到安全区死区内。
     *
     * 死区由 [walkRange] 决定：
     * - 100：死区 = 整个 [safeBounds]
     * - 0：死区退化成安全区中心点
     *
     * 目标在死区内时不移动，越界时移动到最近的安全边界
     * *
     * @param targetScreenX 目标（玩家或光标）格子的屏幕中心 X
     * @param targetScreenY 目标（玩家或光标）格子的屏幕中心 Y
     * @param safeBounds    未被键盘/状态栏/消息栏遮挡的屏幕区域
     * @param walkRange     跟随死区比例，范围 0..100
     * @param tileWidth     当前屏幕 tile 宽度
     * @param tileHeight    当前屏幕 tile 高度
     * @return 是否移动了镜头
     */
    fun follow(
        targetScreenX: Float,
        targetScreenY: Float,
        safeBounds: RectF,
        walkRange: Int,
        tileWidth: Float,
        tileHeight: Float
    ): Boolean {
        if (safeBounds.width() <= 0f || safeBounds.height() <= 0f)
            return false

        val zone = deadZone(safeBounds, walkRange)
        var dx = 0f
        var dy = 0f
        if (targetScreenX < zone.left)
            dx = zone.left - targetScreenX
        if (targetScreenX > zone.right)
            dx = zone.right - targetScreenX
        if (targetScreenY < zone.top)
            dy = zone.top - targetScreenY
        if (targetScreenY > zone.bottom)
            dy = zone.bottom - targetScreenY

        if (dx == 0f && dy == 0f) return false
        panByScreen(dx, dy, tileWidth, tileHeight)
        return true
    }
}
