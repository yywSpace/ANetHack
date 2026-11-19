package com.yywspace.anethack.gameview.map

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View

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
) {
    private val touchListener = NHMapTouchListener().apply {
        onNHMapTouchListener = object : NHMapTouchListener.OnNHMapTouchListener {
            override fun onDown(e: PointF) = Unit
            override fun onClick(e: PointF) {
                tapHandler(e)
            }
            override fun onLongPress(e: PointF) {
                longPressHandler(e)
            }
            override fun onLongPressUp(e: PointF) = Unit
            override fun onLongPressMove(e1: PointF, e2: PointF) = Unit
            override fun onMove(e1: PointF, e2: PointF) {
                moveHandler(e1, e2)
            }
            override fun onScale(scaleFactor: Float, cx: Float, cy: Float) {
                scaleHandler(scaleFactor, cx, cy)
            }
        }
    }

    fun onTouch(view: View, event: MotionEvent): Boolean =
        touchListener.onTouch(view, event)
}
