package com.yywspace.anethack.map.indicator

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.map.NHMapSurfaceView
import com.yywspace.anethack.window.NHWMap
import kotlin.math.abs


class NHMapIndicator(private val mapView: NHMapSurfaceView,
                     private val nh:NetHack,
                     private val tile:NHWMap.Tile){
    var canIndicatorDraw = false
        private set
    private val paint = Paint()
    private val textSize = 50f
    val location = PointF()
    val radius = 50f
    private val margin = 10f // 指示符与屏幕边界之间的间隙
    private val inset = radius + margin
    /** 贴左右边界时沿 y 排开，贴下边界时沿 x */
    var alongY = true
        private set
    private var tangentMin = 0f
    private var tangentMax = 0f

    init {
        paint.textSize = textSize
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
    }

    // 角上时交点的另一个坐标会跑到屏幕外，把指示符整体限制在屏幕内
    private fun clampTangent(value: Float): Float {
        return value.coerceAtLeast(tangentMin).coerceAtMost(tangentMax)
    }

    /** 去重叠时沿边界平移，并夹回可用范围 */
    fun slide(delta: Float) {
        if (alongY)
            location.y = clampTangent(location.y + delta)
        else
            location.x = clampTangent(location.x + delta)
    }

    fun updateLocation() {
        // 屏幕中心到人物与屏幕边界的交点
        val width = mapView.measuredWidth.toFloat()
        val height = mapView.measuredHeight.toFloat()
        val border = mapView.getTileBorder(tile.x, tile.y)
        val target = PointF(border.centerX(), border.centerY())
        canIndicatorDraw = false
        if (target.x !in 0.0f..width) {
            // 目标超出左右屏幕: 指示符贴在左右边界内侧
            val cross = IndicatorUtils.getHorizontalBorderCrossPoint(width, height, target)
            val cx = cross.x + inset * if (cross.x > width / 2) -1 else 1
            alongY = true
            tangentMin = inset
            tangentMax = height - inset
            location.set(cx, clampTangent(cross.y))
            canIndicatorDraw = true
        } else if (target.y > height) {
            // 目标超出屏幕下方(例如被键盘顶出可见区域): 指示符贴在下边界内侧
            val cross = IndicatorUtils.getVerticalBorderCrossPoint(width, height, target)
            val cy = cross.y + inset * if (cross.y > height / 2) -1 else 1
            alongY = false
            tangentMin = inset
            tangentMax = width - inset
            location.set(clampTangent(cross.x), cy)
            canIndicatorDraw = true
        }
    }

    fun draw(canvas: Canvas?, offsetX:Float = 0f, offsetY:Float = 0f) {
        val cx = location.x + offsetX
        val cy = location.y + offsetY
        if (canIndicatorDraw) {
            if (nh.tileSet.isTTY())
                drawAsciiIndicator(canvas, cx, cy)
            else
                drawTileIndicator(canvas, cx, cy)
        }
    }
    private fun drawAsciiIndicator(canvas:Canvas?, cx:Float, cy:Float) {
        paint.color = nh.status.hitPoints.color
        paint.style = Paint.Style.FILL
        canvas?.drawCircle(cx, cy, radius, paint)
        val textWidth = paint.measureText(tile.ch.toString())
        val baseLineY = abs(paint.ascent() + paint.descent()) / 2
        paint.color = Color.WHITE
        canvas?.drawText(tile.ch.toString(), cx - textWidth / 2, cy + baseLineY, paint)
    }
    private fun drawTileIndicator(canvas: Canvas?, cx:Float,cy:Float) {
        val tileBitmap = nh.tileSet.getTile(tile.glyph) ?: return
        IndicatorUtils.circleBitmap(tileBitmap).apply {
            canvas?.drawBitmap(
                this,
                Rect(0,0, this.width, this.height),
                RectF(cx-radius,cy-radius, cx+radius,cy+radius),
                paint
            )
            paint.color =  nh.status.hitPoints.color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas?.drawCircle(cx, cy, radius, paint)
        }
    }

    fun isClicked(x:Float, y:Float):Boolean {
        val cx = location.x
        val cy = location.y
        return x > cx-radius && x < cx+radius && y > cy-radius && y < cy+radius
    }

    fun getTile(): NHWMap.Tile {
        return tile
    }
}