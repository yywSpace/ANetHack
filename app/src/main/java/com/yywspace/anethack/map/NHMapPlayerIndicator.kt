package com.yywspace.anethack.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.entity.NHStatus
import kotlin.math.abs

class NHMapPlayerIndicator(val nh:NetHack) {
    private var indicatorRadius = 50f
    private var indicatorTextSize = 50f
    private val indicatorPaint = Paint()
    private var indicatorLocation = PointF()

    init {
        indicatorPaint.textSize = indicatorTextSize
        indicatorPaint.isAntiAlias = true
    }

    fun draw(canvas: Canvas?, width:Float, height:Float, px:Float, py:Float) {
        // 四个点求两直线交点
        val p1 = PointF(width / 2f, width /2f)
        val p2 = PointF(px, py)
        val p3 = PointF(if (p1.x > p2.x) 0f else width, 0f)
        val p4 = PointF(if (p1.x > p2.x) 0f else width, height)
        val a = p2.y - p1.y
        val b = p1.x - p2.x
        val c = p2.x * p1.y - p1.x * p2.y
        val d = p4.y - p3.y
        val e = p3.x - p4.x
        val f = p4.x * p3.y - p3.x * p4.y
        val x = (f * b - c * e) / (a * e - d * b)
        val y = (c * d - f * a) / (a * e - d * b)
        // 绘制
        if (abs(p1.x - p2.x) > width / 2) {
            nh.getWStatus()?.apply {
                val hp = status.getField(NHStatus.StatusField.BL_HP)
                if (hp != null) {
                    indicatorPaint.color = hp.nhColor.toColor()
                    indicatorPaint.style = Paint.Style.FILL
                    val cx = x + indicatorRadius * if (p1.x > p2.x) 1 else -1
                    canvas?.drawCircle(cx, y, indicatorRadius, indicatorPaint)
                    val textWidth = indicatorPaint.measureText("@")
                    val baseLineY = abs(indicatorPaint.ascent() + indicatorPaint.descent()) / 2
                    indicatorPaint.color = Color.WHITE
                    canvas?.drawText("@", cx - textWidth / 2, y + baseLineY, indicatorPaint)
                    indicatorLocation.set(cx, y)
                }
            }
        }
    }

    fun isClicked(x:Float, y:Float):Boolean {
        val cx = indicatorLocation.x
        val cy = indicatorLocation.y
        if (x > cx-indicatorRadius && x < cx+indicatorRadius && y > cy-indicatorRadius && y < cy+indicatorRadius )
            return true
        return false
    }
}