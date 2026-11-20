package com.yywspace.anethack.gameview.map

import android.graphics.PointF
import android.graphics.RectF

/**
 * 地图镜头：决定“看哪里”。
 *
 * 世界坐标固定，镜头只保存观察中心与缩放。
 * 视口换算和绘制由 [NHMapCoordinates] 与渲染器负责。
 */
class NHMapCamera(
    private val metrics: NHMapMetrics
) {
    var centerX: Float = 0f
    var centerY: Float = 0f
    var zoom: Float = 1f
        private set

    fun setZoom(value: Float) {
        zoom = value
    }

    /** 按双指比例缩放；0.8 是阻尼。 */
    fun zoomBy(scaleFactor: Float) {
        zoom *= 1f + (scaleFactor - 1f) * 0.8f
    }

    fun centerOn(x: Float, y: Float) {
        centerX = x
        centerY = y
    }

    /**
     * 按屏幕位移平移镜头，内部换算成世界坐标。
     *
     * @param dx 屏幕 X 位移
     * @param dy 屏幕 Y 位移
     */
    fun panByScreen(dx: Float, dy: Float) {
        if (metrics.tileWidth <= 0f || metrics.tileHeight <= 0f) return
        centerX -= dx / metrics.tileWidth
        centerY -= dy / metrics.tileHeight
    }

    /**
     * 计算当前 deadZonePercent 对应的跟随死区（屏幕坐标）。
     *
     * deadZonePercent = 100 时死区等于整个安全区；0 时退化为安全区中心点。
     */
    fun deadZone(safeBounds: RectF, deadZonePercent: Int): RectF {
        val range = deadZonePercent.coerceIn(0, 100)
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
     * 判断一个格子的屏幕矩形是否还在死区内。
     *
     * 用格子的外侧边缘判断，而不是格子中心，避免玩家半个身子
     * 已经越界时仍被当成在死区内。
     */
    fun tileInDeadZone(
        tileBounds: RectF,
        safeBounds: RectF,
        deadZonePercent: Int
    ): Boolean {
        if (safeBounds.width() <= 0f || safeBounds.height() <= 0f)
            return true
        val zone = deadZone(safeBounds, deadZonePercent)
        val cx = tileBounds.centerX()
        val cy = tileBounds.centerY()
        val px = if (cx > zone.centerX()) tileBounds.left else tileBounds.right
        val py = if (cy > zone.centerY()) tileBounds.top else tileBounds.bottom
        return px > zone.left && py > zone.top &&
                px < zone.right && py < zone.bottom
    }

    /**
     * 计算目标越出死区后需要的屏幕位移。
     *
     * @return 需要移动的屏幕位移；目标在死区内时返回 null
     */
    fun followOffset(
        targetScreenX: Float,
        targetScreenY: Float,
        safeBounds: RectF,
        deadZonePercent: Int
    ): PointF? {
        if (safeBounds.width() <= 0f || safeBounds.height() <= 0f)
            return null

        val zone = deadZone(safeBounds, deadZonePercent)
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

        return if (dx == 0f && dy == 0f) null else PointF(dx, dy)
    }

    /** 目标是否越出了死区，需要开始跟随。 */
    fun needsFollow(
        targetScreenX: Float,
        targetScreenY: Float,
        safeBounds: RectF,
        deadZonePercent: Int
    ): Boolean = followOffset(
        targetScreenX, targetScreenY,
        safeBounds, deadZonePercent
    ) != null

    /**
     * 立即跟随目标屏幕位置。
     *
     * @return 是否移动了镜头
     */
    fun follow(
        targetScreenX: Float,
        targetScreenY: Float,
        safeBounds: RectF,
        deadZonePercent: Int
    ): Boolean {
        val offset = followOffset(
            targetScreenX, targetScreenY,
            safeBounds, deadZonePercent
        ) ?: return false
        panByScreen(offset.x, offset.y)
        return true
    }
}
