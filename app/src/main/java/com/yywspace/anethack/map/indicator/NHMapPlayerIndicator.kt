package com.yywspace.anethack.map.indicator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.entity.NHStatus
import kotlin.math.abs


class NHMapPlayerIndicator(val nh:NetHack): NHMapIndicator() {
    private var radius = 50f
    private var textSize = 50f
    private val paint = Paint()
    private var location = PointF()
    init {
        paint.textSize = textSize
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
    }
    override fun draw(canvas: Canvas?, width:Float, height:Float, px:Float, py:Float) {
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
        // 屏幕中心到人物与左右屏幕的交点
        val cx = x + radius * if (p1.x > p2.x) 1 else -1
        val cy = (c * d - f * a) / (a * e - d * b)
        // 人物超出屏幕时绘制
        if (abs(p1.x - p2.x) > width / 2) {
            if (nh.tileSet.isTTY())
                drawAsciiIndicator(canvas, cx, cy, radius)
            else
                drawTileIndicator(canvas, cx, cy, radius)
            location.set(cx, cy)
        }
    }

    private fun drawAsciiIndicator(canvas: Canvas?, cx:Float,cy:Float, radius:Float) {
        paint.color = getHealthColor()
        paint.style = Paint.Style.FILL
        canvas?.drawCircle(cx, cy, radius, paint)
        val textWidth = paint.measureText("@")
        val baseLineY = abs(paint.ascent() + paint.descent()) / 2
        paint.color = Color.WHITE
        canvas?.drawText("@", cx - textWidth / 2, cy + baseLineY, paint)
    }
    private fun drawTileIndicator(canvas: Canvas?, cx:Float,cy:Float, radius:Float) {
        nh.tileSet.getPlayerTile()?.apply {
            circleBitmap(this)?.apply {
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
    }

    private fun getHealthColor():Int {
        nh.getWStatus()?.apply {
            val hp = status.getField(NHStatus.StatusField.BL_HP)
            if (hp != null)
                return hp.nhColor.toColor()
        }
        return Color.WHITE
    }
    private fun circleBitmap(bitmap: Bitmap): Bitmap? {
        val r = if (bitmap.width > bitmap.height) bitmap.height else bitmap.width
        val backBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(backBitmap)
        val paint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
        val rectF = RectF(0f, 0f, r.toFloat(), r.toFloat())
        canvas.drawRoundRect(rectF, (r / 2).toFloat(), (r / 2).toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, rectF, paint)
        return backBitmap
    }
    override fun isClicked(x:Float, y:Float):Boolean {
        val cx = location.x
        val cy = location.y
        if (x > cx-radius && x < cx+radius && y > cy-radius && y < cy+radius )
            return true
        return false
    }

    override fun getBorder(): RectF {
        return RectF(
            location.x-radius,
            location.y-radius,
            location.x+radius,
            location.y+radius
        )
    }
}