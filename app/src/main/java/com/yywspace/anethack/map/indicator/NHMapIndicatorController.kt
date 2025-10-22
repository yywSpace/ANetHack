package com.yywspace.anethack.map.indicator

import android.graphics.Canvas
import android.graphics.PointF
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.map.NHMapSurfaceView
import com.yywspace.anethack.window.NHWMap

class NHMapIndicatorController(private val mapView: NHMapSurfaceView, val nh:NetHack, val map: NHWMap) {
    private var indicatorList = mutableListOf<NHMapIndicator>()
    private var onIndicatorClickListener: OnIndicatorClickListener? = null
    fun drawIndicators(canvas:Canvas?) {
        if (!nh.prefs.showIndicator)
            return
        synchronized(indicatorList) {
            buildIndicators()
            indicatorList.forEach { indicator ->
                indicator.updateLocation()
                indicator.draw(canvas)
            }
        }
    }

    private fun buildIndicators() {
        val tmpIndicators = mutableListOf<NHMapIndicator>()
        nh.prefs.indicatorSymbols?.toCharArray()?.toSet()?.forEach { symbol ->
            map.getTileList(symbol).forEach {
                tmpIndicators.add(NHMapIndicator(mapView, nh, it))
            }
        }
        if (nh.prefs.showLastTravelIndicator) {
            mapView.lastTravelTile?.apply {
                // 只显示对应楼层LastTravelIndicator
                if (nh.status.dungeonLevel.realVal == first) {
                    val tile = map.getTile(second.x, second.y)
                    tmpIndicators.add(NHMapIndicator(mapView, nh, tile))
                }
            }
        }
        if (map.player.x > 0 && map.player.y > 0) {
            val tile = map.getTile(map.player.x, map.player.y)
            tmpIndicators.add(NHMapIndicator(mapView, nh, tile))
        }
        indicatorList.clear()
        indicatorList.addAll(tmpIndicators.distinctBy { it.getTile() })
    }
    fun onIndicatorClick(e: PointF):Boolean {
         synchronized(indicatorList) {
            indicatorList.reversed().forEach { indicator ->
                if (indicator.isClicked(e.x, e.y)) {
                    onIndicatorClickListener?.onIndicatorClick(e, indicator.getTile())
                    return true
                }
            }
        }
        return false
    }

    fun onIndicatorLongPress(e: PointF):Boolean {
        synchronized(indicatorList) {
            indicatorList.forEach { indicator ->
                if (indicator.isClicked(e.x, e.y)) {
                    onIndicatorClickListener?.onIndicatorLongPress(e, indicator.getTile())
                    return true
                }
            }
        }
        return false
    }

    fun setOnIndicatorClickListener(listener: OnIndicatorClickListener) {
        onIndicatorClickListener = listener
    }

    interface OnIndicatorClickListener {
        fun onIndicatorClick(e: PointF, tile: NHWMap.Tile)
        fun onIndicatorLongPress(e: PointF, tile: NHWMap.Tile)

    }
}