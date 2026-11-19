package com.yywspace.anethack.gameview.map

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

    /** 屏幕上的增量位移换算成世界中心的移动。 */
    fun panByScreen(dx: Float, dy: Float, tileWidth: Float, tileHeight: Float) {
        if (tileWidth <= 0f || tileHeight <= 0f) return
        centerX -= dx / tileWidth
        centerY -= dy / tileHeight
    }
}
