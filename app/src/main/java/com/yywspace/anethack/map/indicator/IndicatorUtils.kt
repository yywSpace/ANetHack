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

    // 获取屏幕中心点到物体的直线与左右边界的交点
    fun getHorizontalBorderCrossPoint(screenWidth:Float, screenHeight:Float, target:PointF):PointF {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val x = if (target.x < cx) 0f else screenWidth
        val y = cy + (target.y - cy) * (x - cx) / (target.x - cx)
        return PointF(x, y)
    }

    // 获取屏幕中心点到物体的直线与上下边界的交点
    fun getVerticalBorderCrossPoint(screenWidth:Float, screenHeight:Float, target:PointF):PointF {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val y = if (target.y < cy) 0f else screenHeight
        val x = cx + (target.x - cx) * (y - cy) / (target.y - cy)
        return PointF(x, y)
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