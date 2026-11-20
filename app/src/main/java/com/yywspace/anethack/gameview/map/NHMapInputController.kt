package com.yywspace.anethack.gameview.map

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 地图输入层：识别触摸手势，并把语义回调给地图控制器。
 *
 * 不直接修改 Camera、World 或 Coordinates。
 */
class NHMapInputController(
    private val tapHandler: (PointF) -> Unit,
    private val longPressHandler: (PointF) -> Unit,
    private val moveHandler: (PointF, PointF) -> Unit,
    private val scaleHandler: (Float, Float, Float) -> Unit
) : OnTouchListener {

    private var currentClickTime: Long = 0
    private var isClick = false
    private var isLongPress = false
    private var isLongPressMove = false
    private var isMoving = false
    private val lastLocation = PointF()
    private val firstLocation = PointF()
    private var lastScaleSpan = -1f

    private val baseHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (isLongPress) {
            isMoving = false
            isClick = false
            isLongPressMove = true
            longPressHandler(firstLocation)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isClick = true
                isMoving = true
                isLongPress = true
                isLongPressMove = false
                firstLocation.set(event.x, event.y)
                lastLocation.set(event.x, event.y)
                currentClickTime = Calendar.getInstance().timeInMillis
                baseHandler.postDelayed(longPressRunnable, LONG_TRIGGER_TIME)
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (isMoving &&
                        (abs(firstLocation.x - event.x) > TOUCH_SLOP ||
                                abs(firstLocation.y - event.y) > TOUCH_SLOP)
                    ) {
                        isLongPress = false
                        isClick = false
                    }
                    if (isMoving && !isLongPress && !isClick) {
                        moveHandler(lastLocation, PointF(event.x, event.y))
                        lastLocation.set(event.x, event.y)
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
                    scaleHandler(scaleSpan / lastScaleSpan, (px2 + px1) / 2f, (py2 + py1) / 2f)
                    lastScaleSpan = scaleSpan
                }
            }

            MotionEvent.ACTION_UP -> {
                if (Calendar.getInstance().timeInMillis - currentClickTime <= LONG_TRIGGER_TIME) {
                    baseHandler.removeCallbacks(longPressRunnable)
                    isLongPress = false
                }
                if (isClick) {
                    tapHandler(PointF(event.x, event.y))
                }
                lastScaleSpan = -1f
            }
        }
        return true
    }

    companion object {
        private const val LONG_TRIGGER_TIME: Long = 400
        private const val TOUCH_SLOP = 50
    }
}
