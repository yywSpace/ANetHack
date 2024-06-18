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

class NHMapTouchListener : OnTouchListener {
    private var currentClickTime: Long = 0
    private var isClick = false
    private var isLongPress = false
    private var isLongPressMove = false
    private var isMoving = false
    private var lastLocation = PointF()
    private var firstLocation = PointF()
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
        when(event.action) {
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
                if (isMoving && abs(firstLocation.x - event.x) > 40 || abs(firstLocation.y - event.y) > 40) {
                    isLongPress = false
                    isClick = false
                }
                if (isLongPressMove) {
                    onNHMapTouchListener?.onLongPressMove(lastLocation, PointF(event.x, event.y))
                }
                if (isMoving && !isLongPress && !isClick) {
                    onNHMapTouchListener?.onMove(lastLocation, PointF(event.x, event.y))
                    // Log.d("NHMapTouchListener", "Move")
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
                    Log.d("NHMapTouchListener", "Click")
                }
                return true
            }
        }
        return false
    }
    interface OnNHMapTouchListener {
        fun onDown(e:PointF)
        fun onClick(e:PointF)
        fun onLongPress(e:PointF)
        fun onLongPressUp(e:PointF)
        fun onLongPressMove(e1:PointF, e2:PointF)
        fun onMove(e1:PointF, e2:PointF)
    }

    companion object {
        private const val LONG_TRIGGER_TIME: Long = 500
    }
}