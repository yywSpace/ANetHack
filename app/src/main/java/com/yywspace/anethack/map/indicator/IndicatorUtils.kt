package com.yywspace.anethack.map.indicator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap

object IndicatorUtils {

    // 四个点求两直线交点
    private fun getCrossPoint(p1: PointF, p2:PointF, p3: PointF, p4:PointF):PointF {
        val a = p2.y - p1.y
        val b = p1.x - p2.x
        val c = p2.x * p1.y - p1.x * p2.y
        val d = p4.y - p3.y
        val e = p3.x - p4.x
        val f = p4.x * p3.y - p3.x * p4.y
        val x = (f * b - c * e) / (a * e - d * b)
        val y = (c * d - f * a) / (a * e - d * b)
        return PointF(x, y)
    }

    // 获取屏幕中心点到物体的直线与边界交点
    fun getBorderCrossPoint(screenWidth:Float, screenHeight:Float, target:PointF):PointF {
        val p1 = PointF(screenWidth / 2f, screenHeight /2f)
        val p2 = PointF(target.x, target.y)
        val p3 = PointF(if (p1.x > p2.x) 0f else screenWidth, 0f)
        val p4 = PointF(if (p1.x > p2.x) 0f else screenWidth, screenHeight)
        return getCrossPoint(p1, p2, p3, p4)
    }

    fun distance(p1:PointF, p2:PointF):Float {
        return sqrt((p1.x-p2.x)*(p1.x-p2.x)+(p1.y-p2.y)*(p1.y-p2.y))
    }

    fun isCircleOverlay(p1:PointF, p2:PointF, r1:Float, r2:Float):Boolean {
        return distance(p1,p2) < r1 + r2
    }
    fun circleBitmap(bitmap: Bitmap): Bitmap {
        val r = if (bitmap.width > bitmap.height) bitmap.height else bitmap.width
        val backBitmap = createBitmap(bitmap.width, bitmap.height)
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

}