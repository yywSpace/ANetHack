package com.yywspace.anethack.map.indicator

import android.graphics.Canvas
import android.graphics.PointF
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.map.NHMapSurfaceView
import com.yywspace.anethack.window.NHWMap
import kotlinx.coroutines.delay

class NHMapIndicatorController(private val mapView: NHMapSurfaceView, val nh:NetHack, val map: NHWMap) {
    private var indicatorList = mutableListOf<NHMapIndicator>()
    private var onIndicatorClickListener: OnIndicatorClickListener? = null

    fun drawIndicators(canvas:Canvas?) {
        if (!nh.prefs.showIndicator)
            return
        buildIndicators()
        indicatorList.forEach { indicator ->
            indicator.updateLocation()
            indicator.draw(canvas)
        }

    }

    private fun buildIndicators() {
        indicatorList.clear()
        nh.prefs.indicatorSymbols?.toCharArray()?.toSet()?.forEach {
            map.getTileList(it).forEach {
                indicatorList.add(NHMapIndicator(mapView, nh, it))
            }
        }
        if (map.player.x > 0 && map.player.y > 0) {
            val tile = map.getTile(map.player.x, map.player.y)
            indicatorList.add(NHMapIndicator(mapView, nh, tile))
        }
    }
    fun onIndicatorClick(e: PointF):Boolean {
        indicatorList.reversed().forEach { indicator ->
            if (indicator.isClicked(e.x, e.y)) {
                onIndicatorClickListener?.onIndicatorClick(e, indicator.getTile())
                return true
            }
        }
        return false
    }

    fun onIndicatorLongPress(e: PointF):Boolean {
        indicatorList.forEach { indicator ->
            if (indicator.isClicked(e.x, e.y)) {
                onIndicatorClickListener?.onIndicatorLongPress(e, indicator.getTile())
                return true
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