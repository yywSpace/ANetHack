package com.yywspace.anethack.map.indicator

import android.graphics.Canvas
import android.graphics.PointF
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.map.NHMapSurfaceView
import com.yywspace.anethack.window.NHWMap
import kotlin.math.sqrt

class NHMapIndicatorController(private val mapView: NHMapSurfaceView, val nh:NetHack, val map: NHWMap) {
    private var indicatorList = mutableListOf<NHMapIndicator>()
    private var onIndicatorClickListener: OnIndicatorClickListener? = null
    fun drawIndicators(canvas:Canvas?) {
        if (!nh.prefs.showIndicator)
            return
        synchronized(indicatorList) {
            buildIndicators()
            indicatorList.forEach { it.updateLocation() }
            spreadIndicators()
            indicatorList.forEach { indicator ->
                indicator.draw(canvas)
            }
        }
    }

    /** 挤在同一处的指示符沿各自的屏幕边界推开 */
    private fun spreadIndicators() {
        val drawn = indicatorList.filter { it.canIndicatorDraw }
        if (drawn.size < 2)
            return
        // 两个圆心的最小间距：直径 + 一点空隙
        val minGap = 2 * drawn[0].radius + drawn[0].radius / 4f
        // 每轮把所有重叠的对各推开一半；推开一对可能把另一对压近，所以要迭代几轮。
        // 某一轮一对都不重叠就提前结束，8轮只是兜底，空间不够时不可能全部分开
        repeat(8) {
            var overlapped = false
            for (i in 0 until drawn.size - 1) {
                for (j in i + 1 until drawn.size) {
                    val a = drawn[i]
                    val b = drawn[j]
                    val dx = b.location.x - a.location.x
                    val dy = b.location.y - a.location.y
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance >= minGap)
                        continue
                    overlapped = true
                    // 差多少、一人一半：推完这一对刚好相距 minGap，且以原中心对称
                    val push = (minGap - distance) / 2f
                    if (distance == 0f && a.alongY == b.alongY) {
                        // 同一条边界上完全重合时，两个“远离对方”的方向都是 0，会朝同一边
                        // 一起让、永远分不开，这里直接一正一负
                        a.slide(-push)
                        b.slide(push)
                    } else {
                        // da/db 是各自沿自己那条边界的有符号距离，只用来决定往哪边让：
                        // 同一条边界上一个上一个下，跨边界（角上）的各沿自己的边滑走
                        val da = if (a.alongY) dy else dx
                        val db = if (b.alongY) -dy else -dx
                        a.slide(-push * if (da >= 0) 1f else -1f)
                        b.slide(-push * if (db >= 0) 1f else -1f)
                    }
                }
            }
            if (!overlapped)
                return
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