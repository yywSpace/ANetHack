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


abstract class NHMapIndicator {
    var onIndicatorClick:((x:Float,y:Float)->Unit)? = null
    abstract fun draw(canvas: Canvas?, width:Float, height:Float, px:Float, py:Float)
    abstract fun isClicked(x:Float, y:Float):Boolean
    abstract fun getBorder():RectF
    fun onClick(x:Float, y:Float):Boolean {
        if (isClicked(x, y)) {
            onIndicatorClick?.invoke(x, y)
            return true
        }
        return false
    }
}