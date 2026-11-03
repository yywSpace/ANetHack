package com.yywspace.anethack.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import kotlin.math.sin

/**
 * 地图上播放的一次性动画（箭头、滑步等）。
 *
 * [draw] 返回 false 表示动画已结束，由 [NHMapAnimationPlayer] 移除。
 */
interface NHMapAnimation {
    fun draw(canvas: Canvas, frame: NHMapFrame, paint: Paint, now: Long): Boolean
}

/** 一帧里动画需要的几何信息 */
class NHMapFrame(
    /** 一格多少像素 */
    val cellWidth: Float,
    val cellHeight: Float,
    /** 地图左上角在画布上的位置 */
    val originX: Float,
    val originY: Float
) {
    /** 第 [x] 列格子的中心 */
    fun cellCenterX(x: Int): Float = originX + cellWidth * (x + 0.5f)

    /** 第 [y] 行格子的中心 */
    fun cellCenterY(y: Int): Float = originY + cellHeight * (y + 0.5f)

    /** 以“格”为单位的距离换算成像素 */
    fun cellsToWidth(cells: Float): Float = cells * cellWidth
    fun cellsToHeight(cells: Float): Float = cells * cellHeight
}

/** 管理当前正在播放的动画 */
class NHMapAnimationPlayer {
    private val animations = mutableListOf<NHMapAnimation>()

    val isActive: Boolean
        get() = synchronized(animations) { animations.isNotEmpty() }

    fun add(animation: NHMapAnimation) {
        synchronized(animations) { animations.add(animation) }
    }

    fun clear() {
        synchronized(animations) { animations.clear() }
    }

    fun draw(canvas: Canvas, frame: NHMapFrame, paint: Paint, now: Long) {
        synchronized(animations) {
            animations.removeAll { !it.draw(canvas, frame, paint, now) }
        }
    }
}

/**
 * 带时长的动画基类：子类只实现 [drawProgress]，进度和结束判断由这里处理。
 */
abstract class NHProgressAnimation(
    protected val start: Long = System.currentTimeMillis(),
    private val duration: Long = 350L
) : NHMapAnimation {

    final override fun draw(canvas: Canvas, frame: NHMapFrame, paint: Paint, now: Long): Boolean {
        val progress = (now - start) / duration.toFloat()
        if (progress >= 1f) return false
        drawProgress(canvas, frame, paint, progress)
        return true
    }

    /** [progress] 从 0 到 1，等于 1 时动画结束 */
    abstract fun drawProgress(canvas: Canvas, frame: NHMapFrame, paint: Paint, progress: Float)
}

/** 朝移动方向跳一下的小箭头，位置每帧跟着角色 */
class NHMoveArrow(
    private val player: () -> Point,
    private val color: () -> Int,
    private val dx: Float,
    private val dy: Float
) : NHProgressAnimation() {

    private val path = Path()

    override fun drawProgress(canvas: Canvas, frame: NHMapFrame, paint: Paint, progress: Float) {
        val eased = sin(Math.PI * progress).toFloat()
        val distance = 0.55f + 0.25f * eased
        val tile = player()
        val cx = frame.cellCenterX(tile.x) + frame.cellsToWidth(dx * distance)
        val cy = frame.cellCenterY(tile.y) + frame.cellsToHeight(dy * distance)
        val half = frame.cellWidth * 0.16f

        path.reset()
        // 指向右的箭头，再按方向旋转
        path.moveTo(half * 1.5f, 0f)
        path.lineTo(0f, -half)
        path.lineTo(0f, half)
        path.close()
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat())
        paint.style = Paint.Style.FILL
        val alpha = (255 * (1f - progress)).toInt()
        paint.color = (color() and 0x00FFFFFF) or (alpha shl 24)
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
