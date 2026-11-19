package com.yywspace.anethack.gameview.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.yywspace.anethack.NHTileSet
import com.yywspace.anethack.NetHack
import androidx.core.graphics.createBitmap
import kotlin.math.floor

/**
 * 地图层渲染：离屏 bitmap、tile 绘制和 blit。
 *
 * 只接收世界状态和视口，不处理触摸、相机或窗口协议。
 */
class NHMapRenderer(
    private val nh: NetHack,
    val textSize: Float = 64f
) {

    val paint = Paint().apply { isFilterBitmap = false }
    val asciiPaint = TextPaint().apply {
        isAntiAlias = true
        typeface = Typeface.createFromAsset(nh.context.assets, "fonts/monobold.ttf")
        textAlign = Align.LEFT
    }

    private var mapBitmap: Bitmap? = null
    private var mapCanvas: Canvas? = null

    var contentDirty = false
        private set

    fun hasBitmap(): Boolean = mapBitmap != null

    fun markDirty() {
        contentDirty = true
    }

    /**
     * 初始化渲染资源。
     *
     * 会测量基础 tile 尺寸并写回 viewport，再分配离屏 bitmap。
     */
    fun initialize(world: NHMapWorld, viewport: NHMapProjection) {
        viewport.setBaseTileSize(measureBaseTileWidth(), measureBaseTileHeight())
        // 重建离屏缓冲
        mapBitmap = createBitmap(
            (world.width * viewport.baseTileWidth).toInt(),
            (world.height * viewport.baseTileHeight).toInt()
        ).also { mapCanvas = Canvas(it) }
        mapBitmap?.eraseColor(Color.BLACK)
        contentDirty = true
    }

    fun drawMap(
        canvas: Canvas,
        world: NHMapWorld,
        viewport: NHMapProjection,
        frame: MapFrame?,
        tileSetChanged: Boolean
    ) {
        if (frame != null)
            world.applyFrame(frame)

        if (tileSetChanged || contentDirty) {
            drawAllToOffscreen(world, viewport)
            contentDirty = false
        } else frame?.tiles?.forEach { tile ->
            if (tile.x in 0 until world.width && tile.y in 0 until world.height) {
                drawOneToOffscreen(tile.x, tile.y, world, viewport)
            }
        }

        canvas.drawColor(Color.BLACK)
        blit(canvas, viewport)
    }

    fun blit(canvas: Canvas, viewport: NHMapProjection) {
        mapBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), viewport.worldDrawRect, paint)
        }
    }

    private fun drawOneToOffscreen(x: Int, y: Int, world: NHMapWorld, viewport: NHMapProjection) {
        val canvas = mapCanvas ?: return
        val tile = world.getTile(x, y)
        val tb = RectF(
            x * viewport.baseTileWidth, y * viewport.baseTileHeight,
            (x + 1) * viewport.baseTileWidth, (y + 1) * viewport.baseTileHeight
        )
        if (tile.overlay and NHTileSet.MG_UNEXPL != 0) {
            paint.color = Color.BLACK
            paint.style = Paint.Style.FILL
            canvas.drawRect(tb, paint)
            return
        }
        val overlay = tile.overlay and NHTileSet.SUPPORTED_OVERLAY_MASK
        if (nh.tileSet.isTTY()) {
            paint.style = Paint.Style.FILL
            var bgColor = Color.BLACK
            var fgColor = tile.color.toColor()
            if (overlay != 0 && tile.glyph >= 0) {
                fgColor = Color.BLACK
                bgColor = tile.color.toColor()
            }
            paint.color = bgColor
            canvas.drawRect(tb, paint)
            if (tile.glyph >= 0) {
                asciiPaint.textSize = textSize
                asciiPaint.color = fgColor
                canvas.drawText(
                    tile.ch.toString(), 0, 1, tb.left,
                    tb.bottom - asciiPaint.descent(), asciiPaint
                )
            }
        } else {
            paint.color = Color.BLACK
            paint.style = Paint.Style.FILL
            canvas.drawRect(tb, paint)

            if (tile.glyph >= 0) {
                nh.tileSet.getTile(tile.glyph)?.apply {
                    canvas.drawBitmap(this, Rect(0, 0, this.width, this.height), tb, paint)
                }
            }
            if (overlay != 0 && tile.glyph >= 0) {
                val overlayBitmap = nh.tileSet.getTileOverlay(overlay)
                if (overlayBitmap != null) {
                    canvas.drawBitmap(overlayBitmap, nh.tileSet.getOverlayRect(overlay), tb, paint)
                }
            }
        }
    }

    private fun drawAllToOffscreen(world: NHMapWorld, viewport: NHMapProjection) {
        for (x in 0 until world.width) {
            for (y in 0 until world.height) {
                drawOneToOffscreen(x, y, world, viewport)
            }
        }
    }

    fun measureBaseTileWidth(): Float =
        if (nh.tileSet.isTTY()) {
            asciiPaint.textSize = textSize
            floor(asciiPaint.measureText("\u2550"))
        } else {
            nh.tileSet.tileWidth.toFloat()
        }

    fun measureBaseTileHeight(): Float =
        if (nh.tileSet.isTTY()) {
            asciiPaint.textSize = textSize
            val metrics = asciiPaint.fontMetrics
            floor(metrics.descent - metrics.ascent)
        } else {
            nh.tileSet.tileHeight.toFloat()
        }
}
