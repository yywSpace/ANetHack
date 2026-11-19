package com.yywspace.anethack.gameview.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.gameview.map.indicator.NHMapIndicatorController

/**
 * 地图覆盖层：光标、点击框、边框、动画、指示器。
 *
 * 只读 World 和 Viewport，不修改相机或世界状态。
 */
class NHMapOverlayRenderer(
    private val nh: NetHack,
    private val renderer: NHMapRenderer,
    private val animations: NHMapAnimationPlayer
) {
    private val curseFillAlpha = 0x26

    fun draw(
        canvas: Canvas,
        world: NHMapWorld,
        viewport: NHMapProjection,
        drawCurse: Point,
        lastTouchTile: Point?,
        indicatorController: NHMapIndicatorController,
        scaleFactor: Float
    ) {
        if (nh.tileSet.isTTY()) {
            drawAsciiCurse(canvas, world, viewport, drawCurse, scaleFactor)
        } else {
            drawTileCurse(canvas, world, viewport, drawCurse)
        }
        drawLastTouchTile(canvas, viewport, lastTouchTile)
        drawAnimations(canvas, viewport)
        drawBorder(canvas, viewport)
        indicatorController.drawIndicators(canvas)
    }

    private fun drawAsciiCurse(
        canvas: Canvas,
        world: NHMapWorld,
        viewport: NHMapProjection,
        drawCurse: Point,
        scaleFactor: Float
    ) {
        if (drawCurse.x < 0 || drawCurse.y < 0) return
        val tile = world.getTile(drawCurse.x, drawCurse.y)
        val tb = viewport.tileBorder(drawCurse.x, drawCurse.y)
        renderer.paint.color = nh.status.hitPoints.color
        renderer.paint.style = Paint.Style.FILL
        canvas.drawRect(tb, renderer.paint)
        if (tile.glyph >= 0) {
            renderer.asciiPaint.color = tile.color.toColor()
            renderer.asciiPaint.textSize = renderer.textSize * scaleFactor
            canvas.drawText(
                tile.ch.toString(), 0, 1,
                tb.left, tb.bottom - renderer.asciiPaint.descent(), renderer.asciiPaint
            )
        }
    }

    private fun drawTileCurse(
        canvas: Canvas,
        world: NHMapWorld,
        viewport: NHMapProjection,
        drawCurse: Point
    ) {
        if (drawCurse.x < 0 || drawCurse.y < 0) return
        val tb = viewport.tileBorder(drawCurse.x, drawCurse.y)
        renderer.paint.style = Paint.Style.FILL
        renderer.paint.color =
            (nh.status.hitPoints.color and 0x00FFFFFF) or (curseFillAlpha shl 24)
        canvas.drawRect(tb, renderer.paint)
    }

    private fun drawLastTouchTile(
        canvas: Canvas,
        viewport: NHMapProjection,
        lastTouchTile: Point?
    ) {
        lastTouchTile ?: return
        val tb = viewport.tileBorder(lastTouchTile.x, lastTouchTile.y)
        renderer.paint.color = Color.GRAY
        renderer.paint.style = Paint.Style.STROKE
        renderer.paint.strokeWidth = viewport.borderWidth
        canvas.drawRect(tb, renderer.paint)
    }

    private fun drawAnimations(canvas: Canvas, viewport: NHMapProjection) {
        if (!nh.prefs.playAnimations) {
            animations.clear()
            return
        }
        if (!animations.isActive) return
        val frame = NHMapAnimationFrame(
            viewport.tileWidth,
            viewport.tileHeight,
            viewport.worldDrawRect.left,
            viewport.worldDrawRect.top
        )
        animations.draw(canvas, frame, renderer.paint, System.currentTimeMillis())
    }

    private fun drawBorder(canvas: Canvas, viewport: NHMapProjection) {
        renderer.paint.color = Color.GRAY
        renderer.paint.style = Paint.Style.STROKE
        renderer.paint.strokeWidth = viewport.borderWidth
        val border = RectF(viewport.worldDrawRect).apply { left += viewport.tileWidth }
        canvas.drawRect(border, renderer.paint)
    }
}
