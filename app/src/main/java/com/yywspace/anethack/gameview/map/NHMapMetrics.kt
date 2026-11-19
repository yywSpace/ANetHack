package com.yywspace.anethack.gameview.map

/**
 * 地图当前屏幕 tile 尺寸。
 *
 * 由 Coordinates 在投影刷新时更新，Camera 直接读取。
 */
class NHMapMetrics {
    var mapWidth = 0
    var mapHeight = 0

    var viewWidth = 0
    var viewHeight = 0

    var tileWidth = 0f
    var tileHeight = 0f

    fun setViewport(viewWidth: Int, viewHeight: Int, mapWidth: Int, mapHeight: Int) {
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        this.mapWidth = mapWidth
        this.mapHeight = mapHeight
    }
}
