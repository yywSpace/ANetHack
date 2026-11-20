package com.yywspace.anethack.gameview.map

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.exp

/**
 * 平滑相机播放器：保存跟随目标和插值状态，驱动 [NHMapCamera] 平滑移动。
 *
 * Camera 只保存当前镜头状态；动画状态全部在这里。
 */
class NHMapCameraAnimator(
    private val camera: NHMapCamera,
    private val coordinates: NHMapCoordinates,
    private val metrics: NHMapMetrics,
    private val postDelayed: (Long, () -> Unit) -> Unit,
    private val redraw: () -> Unit
) {
    private var targetCenterX = 0f
    private var targetCenterY = 0f
    private var animating = false
    val isAnimating: Boolean
        get() = animating
    private var scheduled = false
    private var lastFrameNanos = 0L

    /**
     * 让相机跟随目标屏幕位置。
     *
     * 目标越出死区后，把目标放到安全区中心，再平滑追上。
     *
     * @return 是否启动了跟随
     */
    fun follow(
        targetScreenX: Float,
        targetScreenY: Float,
        safeBounds: RectF,
        deadZonePercent: Int
    ): Boolean {
        if (!camera.needsFollow(
                targetScreenX, targetScreenY,
                safeBounds, deadZonePercent
            )
        ) return false

        targetCenterX = camera.centerX +
                (targetScreenX - safeBounds.centerX()) / metrics.tileWidth
        targetCenterY = camera.centerY +
                (targetScreenY - safeBounds.centerY()) / metrics.tileHeight

        animating = true
        lastFrameNanos = 0L
        schedule()
        return true
    }

    /** 停止跟随；用于拖动、缩放、居中立即接管。 */
    fun cancel() {
        animating = false
        targetCenterX = camera.centerX
        targetCenterY = camera.centerY
    }

    private fun schedule() {
        if (scheduled) return
        scheduled = true
        postDelayed(16) {
            scheduled = false
            val now = System.nanoTime()
            val dtMs = if (lastFrameNanos == 0L) {
                16f
            } else {
                ((now - lastFrameNanos) / 1_000_000f).coerceIn(1f, 100f)
            }
            lastFrameNanos = now

            val active = advance(dtMs)
            coordinates.updateFromCamera(camera)
            redraw()
            if (active) schedule()
        }
    }

    /** 按经过的时间推进一帧，返回是否还需要继续。 */
    private fun advance(dtMs: Float): Boolean {
        if (!animating) return false

        val t = 1f - exp(-dtMs / SMOOTH_TIME_MS)
        camera.centerX += (targetCenterX - camera.centerX) * t
        camera.centerY += (targetCenterY - camera.centerY) * t

        if (abs(targetCenterX - camera.centerX) < SNAP_EPSILON &&
            abs(targetCenterY - camera.centerY) < SNAP_EPSILON
        ) {
            camera.centerX = targetCenterX
            camera.centerY = targetCenterY
            animating = false
        }
        return animating
    }

    companion object {
        private const val SMOOTH_TIME_MS = 80f
        private const val SNAP_EPSILON = 0.01f
    }
}
