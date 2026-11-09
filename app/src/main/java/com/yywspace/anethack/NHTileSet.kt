package com.yywspace.anethack

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import android.widget.Toast
import java.io.File


class NHTileSet(val nh: NetHack) {
    private val tileCache = mutableMapOf<Int, Bitmap>()
    private var tileBitmap: Bitmap? = null
    private var tileSetSignature:String? = null
    var tileWidth = 0
    var tileHeight = 0

    /**
     * 按 tile 索引从整张贴图切出单格。
     *
     * 索引越界或尺寸无效时返回占位 tile，避免 Bitmap.createBitmap 越界崩溃。
     */
    fun getTile(tile:Int):Bitmap? {
        // 贴图尚未加载
        val sheet = tileBitmap ?: return null
        if (tileWidth <= 0 || tileHeight <= 0)
            return placeholderTile()

        // 整张图按行优先排列，先算出总列数和总行数
        val columns = sheet.width / tileWidth
        val rows = sheet.height / tileHeight
        if (tile < 0 || columns <= 0 || rows <= 0)
            return placeholderTile()

        // tile -> (列, 行)
        val tx = tile % columns
        val ty = tile / columns
        if (tx !in 0 until columns || ty !in 0 until rows)
            return placeholderTile()

        // 每个 tile 只切一次，之后复用缓存
        return tileCache.getOrPut(tile) {
            Bitmap.createBitmap(sheet, tx * tileWidth, ty * tileHeight, tileWidth, tileHeight)
        }
    }

    private var placeholderBitmap: Bitmap? = null
    private var placeholderWidth = 0
    private var placeholderHeight = 0

    /** 越界 tile 的占位图，避免 createBitmap 越界崩溃。 */
    private fun placeholderTile(): Bitmap {
        val w = tileWidth.takeIf { it > 0 } ?: 16
        val h = tileHeight.takeIf { it > 0 } ?: 16
        placeholderBitmap?.let {
            if (placeholderWidth == w && placeholderHeight == h)
                return it
        }
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = minOf(w, h) * 0.7f
            textAlign = Paint.Align.CENTER
        }
        val baseline = h / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText("?", w / 2f, baseline, paint)
        placeholderBitmap = bitmap
        placeholderWidth = w
        placeholderHeight = h
        return bitmap
    }

    fun updateTileSet() {
        val am: AssetManager = nh.context.resources.assets
        var tileSprite: String? = null
        when (nh.prefs.tileSet) {
            // default
            "default" -> {
                tileSprite = "tiles/default_tiles_16.bmp"
                tileHeight = 16
                tileWidth = 16
            }
            "nevanda" -> {
                tileSprite = "tiles/nevanda_32.png"
                tileHeight = 32
                tileWidth = 32
            }
            "pixelhack" -> {
                tileSprite = "tiles/PixelHack.png"
                tileHeight = 32
                tileWidth = 32
            }
            "custom" -> {
                val file = customTileFile(nh.context)
                val width = nh.prefs.customTileWidth
                val height = nh.prefs.customTileHeight
                val bitmap = if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
                if (bitmap != null) {
                    tileBitmap = bitmap
                    tileWidth = width
                    tileHeight = height
                    tileCache.clear()
                    return
                }
                // 自定义 tileset 不可用，提示后使用默认贴图
                notifyCustomTileSetError(R.string.custom_tileset_invalid_image)
                tileSprite = "tiles/default_tiles_16.bmp"
                tileHeight = 16
                tileWidth = 16
            }
        }
        if (tileSprite != null) {
            am.open(tileSprite).use {
                tileBitmap = BitmapFactory.decodeStream(it)
            }
            tileCache.clear()
        }
    }

    private fun notifyCustomTileSetError(message: Int) {
        nh.handler.post {
            Toast.makeText(nh.context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentSignature(): String {
        val prefs = nh.prefs
        return "${prefs.tileSet}:" +
                "${prefs.customTileWidth}x${prefs.customTileHeight}:${prefs.customTileSetVersion}"
    }

    /** 只检查是否有贴图切换（不消费状态，供绘制线程变化检测） */
    fun hasTileSetChange():Boolean {
        val signature = currentSignature()
        if (tileSetSignature == null) {
            tileSetSignature = signature
            return false
        }
        return tileSetSignature != signature
    }

    /** 检查并消费贴图切换（draw 内调用，消费后执行重新加载） */
    fun isTileSetChange():Boolean {
        if (hasTileSetChange()) {
            tileSetSignature = currentSignature()
            return true
        }
        return false
    }
    fun isTTY(): Boolean {
        return nh.prefs.tileSet == "ascii"
    }

    fun getOverlayRect(overlay: Int): Rect {
        if (overlay and MG_PET != 0
            || overlay and MG_OBJPILE != 0
            || overlay and MG_DETECT != 0
            || overlay and MG_INVIS != 0)
            return Rect(0, 0, 32, 32)
        return Rect(0, 0, 32, 32)
    }

    private val overlayCache = mutableMapOf<Int, Bitmap>()

    fun getTileOverlay(overlay: Int): Bitmap? {
        overlayCache[overlay]?.let { return it }
        val overlayPath = if (overlay and MG_PET != 0)
            "tiles/overlay_pet.png"
        else if (overlay and MG_OBJPILE != 0)
            "tiles/overlay_pile.png"
        else if (overlay and MG_INVIS != 0)
            "tiles/overlay_invis.png"
        else if (overlay and MG_DETECT != 0)
            "tiles/overlay_default.png"
        else
            ""
        if(overlayPath.isNotEmpty()) {
            nh.context.resources.assets.open(overlayPath).use {  overlayIO ->
                return BitmapFactory.decodeStream(overlayIO).also { overlayCache[overlay] = it }
            }
        }
        return null
    }

    companion object {
        const val MG_INVIS = 0x00004
        const val MG_UNEXPL = 0x00800
        const val MG_DETECT = 0x00008
        const val MG_PET = 0x00010
        const val MG_OBJPILE = 0x00080

        /** 只用于绘制 overlay 的标志位；MG_UNEXPL 等变体标记不算 overlay。 */
        const val SUPPORTED_OVERLAY_MASK = MG_INVIS or MG_DETECT or MG_PET or MG_OBJPILE


        const val TILE_DIR = "tiles"
        const val CUSTOM_TILE_FILE = "custom_tileset.png"
        const val CUSTOM_TILE_TEMP_FILE = "custom_tileset.tmp"

        fun customTileFile(context: Context): File =
            File(File(context.filesDir, TILE_DIR), CUSTOM_TILE_FILE)

        fun customTileTempFile(context: Context): File =
            File(File(context.filesDir, TILE_DIR), CUSTOM_TILE_TEMP_FILE)

    }
}