package com.yywspace.anethack.map.indicator

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.min

object IndicatorUtils {

    /**
     * 从安全区域中心向目标点作射线，返回射线与安全区域边界的交点。
     * 目标点在安全区域内时调用方不应使用该结果。
     */
    fun getBorderCrossPoint(bounds: RectF, target: PointF): PointF {
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val dx = target.x - cx
        val dy = target.y - cy

        var t = Float.MAX_VALUE
        if (dx > 0f)
            t = min(t, (bounds.right - cx) / dx)
        else if (dx < 0f)
            t = min(t, (bounds.left - cx) / dx)

        if (dy > 0f)
            t = min(t, (bounds.bottom - cy) / dy)
        else if (dy < 0f)
            t = min(t, (bounds.top - cy) / dy)

        if (t == Float.MAX_VALUE || t <= 0f)
            return PointF(cx, cy)

        return PointF(cx + dx * t, cy + dy * t)
    }

    fun circleBitmap(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val r = if (bitmap.width > bitmap.height) bitmap.height else bitmap.width
        val backBitmap = android.graphics.Bitmap.createBitmap(
            bitmap.width, bitmap.height,
            bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(backBitmap)
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
        val rectF = RectF(0f, 0f, r.toFloat(), r.toFloat())
        canvas.drawRoundRect(rectF, (r / 2).toFloat(), (r / 2).toFloat(), paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, rectF, paint)
        return backBitmap
    }
}
