package com.yywspace.anethack.map.indicator

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.map.NHMapSurfaceView
import com.yywspace.anethack.window.NHWMap
import kotlin.math.abs


class NHMapIndicator(private val mapView: NHMapSurfaceView,
                     private val nh:NetHack,
                     private val tile:NHWMap.Tile){
    private var canIndicatorDraw = false
    private val paint = Paint()
    private val textSize = 50f
    val location = PointF()
    val radius = 50f

    init {
        paint.textSize = textSize
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
    }

    fun updateLocation() {
        // 屏幕中心到人物与左右屏幕的交点
        val width = mapView.measuredWidth.toFloat()
        val height = mapView.measuredHeight.toFloat()
        val border = mapView.getTileBorder(tile.x, tile.y)
        val target = PointF(border.centerX(), border.centerY())
        val cross = IndicatorUtils.getBorderCrossPoint(width, height, target)
        val cx = cross.x + radius * if (cross.x > width / 2) -1 else 1
        val cy = cross.y
        // 人物超出屏幕时绘制, 目标点到中心点的x轴距离大于width / 2
        if (abs(target.x -width / 2) > width / 2) {
            canIndicatorDraw = true
            location.set(cx, cy)
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
        paint.color = getHealthColor()
        paint.style = Paint.Style.FILL
        canvas?.drawCircle(cx, cy, radius, paint)
        val textWidth = paint.measureText(tile.ch.toString())
        val baseLineY = abs(paint.ascent() + paint.descent()) / 2
        paint.color = Color.WHITE
        canvas?.drawText(tile.ch.toString(), cx - textWidth / 2, cy + baseLineY, paint)
    }
    private fun drawTileIndicator(canvas: Canvas?, cx:Float,cy:Float) {
        val tileBitmap = nh.tileSet.getTile(tile.glyph) ?: return
        IndicatorUtils.circleBitmap(tileBitmap)?.apply {
            canvas?.drawBitmap(
                this,
                Rect(0,0, this.width, this.height),
                RectF(cx-radius,cy-radius, cx+radius,cy+radius),
                paint
            )
            paint.color = getHealthColor()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas?.drawCircle(cx, cy, radius, paint)
        }
    }

    fun isClicked(x:Float, y:Float):Boolean {
        val cx = location.x
        val cy = location.y
        if (x > cx-radius && x < cx+radius && y > cy-radius && y < cy+radius )
            return true
        return false
    }

    private fun getHealthColor() :Int {
        nh.getWStatus()?.apply {
            val hp = status.getField(NHStatus.StatusField.BL_HP)
            if (hp != null)
                return hp.nhColor.toColor()
        }
        return Color.GRAY
    }
    fun getTile(): NHWMap.Tile {
        return tile
    }
}