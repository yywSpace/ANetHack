package com.yywspace.anethack.map

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class NHMapTouchListener : OnTouchListener {
    private var currentClickTime: Long = 0
    private val touchSlop = 50
    private var isClick = false
    private var isLongPress = false
    private var isLongPressMove = false
    private var isMoving = false
    private var lastLocation = PointF()
    private var firstLocation = PointF()
    private var lastScaleSpan = -1f
    var onNHMapTouchListener: OnNHMapTouchListener? = null

    private val baseHandler: Handler =  Handler(Looper.getMainLooper())
    private val longPressRunnable: Runnable = Runnable {
        if (isLongPress) {
            isMoving = false
            isClick = false
            isLongPressMove = true
            onNHMapTouchListener?.onLongPress(firstLocation)
            Log.d("NHMapTouchListener", "LongPress")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN-> {
                isClick = true
                isMoving = true
                isLongPress = true
                isLongPressMove = false
                firstLocation.set(event.x, event.y)
                lastLocation.set(event.x, event.y)
                onNHMapTouchListener?.onDown(firstLocation)
                currentClickTime =  Calendar.getInstance().timeInMillis;
                baseHandler.postDelayed(longPressRunnable, LONG_TRIGGER_TIME)
            }
            MotionEvent.ACTION_MOVE-> {
                // 单指操作
                if (event.pointerCount == 1) {
                    if (isMoving && abs(firstLocation.x - event.x) > touchSlop || abs(firstLocation.y - event.y) > touchSlop) {
                        isLongPress = false
                        isClick = false
                    }
                    if (isLongPressMove) {
                        onNHMapTouchListener?.onLongPressMove(lastLocation, PointF(event.x, event.y))
                    }
                    if (isMoving && !isLongPress && !isClick) {
                        onNHMapTouchListener?.onMove(lastLocation, PointF(event.x, event.y))
                    }
                } else {
                    isLongPress = false
                    isLongPressMove = false
                    isClick = false
                    isMoving = false
                    val px1 = event.getX(0)
                    val py1 = event.getY(0)
                    val px2 = event.getX(1)
                    val py2 = event.getY(1)
                    val scaleSpan = sqrt((px2 - px1).pow(2) + (py2 - py1).pow(2))
                    if (lastScaleSpan <= 0)
                        lastScaleSpan = scaleSpan
                    val scale = scaleSpan / lastScaleSpan
                    onNHMapTouchListener?.onScale(scale, (px2 + px1) / 2f, (py2 + py1) / 2f)
                    lastScaleSpan = scaleSpan
                }
            }
            MotionEvent.ACTION_UP-> {
                if(Calendar.getInstance().timeInMillis - currentClickTime <= LONG_TRIGGER_TIME){
                    baseHandler.removeCallbacks(longPressRunnable)
                    isLongPress = false
                }
                if(isLongPress||isLongPressMove) {
                    onNHMapTouchListener?.onLongPressUp(PointF(event.x, event.y))
                }
                if (isClick) {
                    // click in here
                    onNHMapTouchListener?.onClick(PointF(event.x, event.y))
                }
                lastScaleSpan = -1f
            }
        }
        return true
    }
    interface OnNHMapTouchListener {
        fun onDown(e:PointF)
        fun onClick(e:PointF)
        fun onLongPress(e:PointF)
        fun onLongPressUp(e:PointF)
        fun onLongPressMove(e1:PointF, e2:PointF)
        fun onMove(e1:PointF, e2:PointF)
        fun onScale(scaleFactor:Float, cx:Float, cy:Float)

    }

    companion object {
        private const val LONG_TRIGGER_TIME: Long = 400
    }
}