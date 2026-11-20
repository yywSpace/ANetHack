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
        coordinates: NHMapCoordinates,
        drawCurse: Point,
        lastTouchTile: Point?,
        indicatorController: NHMapIndicatorController,
        scaleFactor: Float
    ) {
        if (nh.tileSet.isTTY()) {
            drawAsciiCurse(canvas, world, coordinates, drawCurse, scaleFactor)
        } else {
            drawTileCurse(canvas, world, coordinates, drawCurse)
        }
        drawLastTouchTile(canvas, coordinates, lastTouchTile)
        drawAnimations(canvas, coordinates)
        drawBorder(canvas, coordinates)
        indicatorController.drawIndicators(canvas)
    }

    private fun drawAsciiCurse(
        canvas: Canvas,
        world: NHMapWorld,
        coordinates: NHMapCoordinates,
        drawCurse: Point,
        scaleFactor: Float
    ) {
        if (drawCurse.x < 0 || drawCurse.y < 0) return
        val tile = world.getTile(drawCurse.x, drawCurse.y)
        val tb = coordinates.tileBorder(drawCurse.x, drawCurse.y)
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
        coordinates: NHMapCoordinates,
        drawCurse: Point
    ) {
        if (drawCurse.x < 0 || drawCurse.y < 0) return
        val tb = coordinates.tileBorder(drawCurse.x, drawCurse.y)
        renderer.paint.style = Paint.Style.FILL
        renderer.paint.color =
            (nh.status.hitPoints.color and 0x00FFFFFF) or (curseFillAlpha shl 24)
        canvas.drawRect(tb, renderer.paint)
    }

    private fun drawLastTouchTile(
        canvas: Canvas,
        coordinates: NHMapCoordinates,
        lastTouchTile: Point?
    ) {
        lastTouchTile ?: return
        val tb = coordinates.tileBorder(lastTouchTile.x, lastTouchTile.y)
        renderer.paint.color = Color.GRAY
        renderer.paint.style = Paint.Style.STROKE
        renderer.paint.strokeWidth = coordinates.borderWidth
        canvas.drawRect(tb, renderer.paint)
    }

    private fun drawAnimations(canvas: Canvas, coordinates: NHMapCoordinates) {
        if (!animations.isActive) return
        animations.draw(canvas, coordinates, renderer.paint, System.currentTimeMillis())
    }

    private fun drawBorder(canvas: Canvas, coordinates: NHMapCoordinates) {
        renderer.paint.color = Color.GRAY
        renderer.paint.style = Paint.Style.STROKE
        renderer.paint.strokeWidth = coordinates.borderWidth
        val border = RectF(coordinates.worldDrawRect).apply { left += coordinates.tileWidth }
        canvas.drawRect(border, renderer.paint)
    }
}
