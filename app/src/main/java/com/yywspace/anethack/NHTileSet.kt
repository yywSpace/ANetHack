package com.yywspace.anethack

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log


class NHTileSet(val nh: NetHack) {
    private val tileCache = mutableMapOf<Int, Bitmap>()
    private var tileBitmap: Bitmap? = null
    private var tileSetName:String? = null
    var tileWidth = 0
    var tileHeight = 0

    fun getTile(tile:Int):Bitmap? {
        tileBitmap?.apply {
            var bitmap = tileCache[tile]
            if (bitmap == null) {
                val tx = tile % (width / tileWidth)
                val ty = tile / (width / tileWidth)
                if (tx < 0 || ty < 0)
                    return null
                bitmap = Bitmap.createBitmap(this, tx * tileWidth, ty * tileHeight, tileWidth, tileHeight)
                tileCache[tile] = bitmap
            }
            return bitmap
        }
        return null
    }

    fun updateTileSet() {
        val am: AssetManager = nh.context.resources.assets
        var tileSprite: String? = null
        when(nh.prefs.tileSet) {
            // default
            "2" -> {
                tileSprite = "tiles/default_tiles_16.bmp"
                tileHeight = 16
                tileWidth = 16
            }
            "3" -> {
                tileSprite = "tiles/nevanda_32.png"
                tileHeight = 32
                tileWidth = 32
            }
            "4" -> {
                tileSprite = "tiles/PixelHack.png"
                tileHeight = 32
                tileWidth = 32
            }
        }
        if (tileSprite != null ) {
            am.open(tileSprite).use {
                tileBitmap = BitmapFactory.decodeStream(it)
            }
            tileCache.clear()
        }
    }

    /** 只检查是否有贴图切换（不消费状态，供绘制线程变化检测） */
    fun hasTileSetChange():Boolean {
        if (tileSetName == null) {
            tileSetName = nh.prefs.tileSet
            return false
        }
        return tileSetName != nh.prefs.tileSet
    }

    /** 检查并消费贴图切换（draw 内调用，消费后执行重新加载） */
    fun isTileSetChange():Boolean {
        if (hasTileSetChange()) {
            tileSetName = nh.prefs.tileSet?:"1"
            return true
        }
        return false
    }
    fun isTTY(): Boolean {
        return nh.prefs.tileSet == "1" // ascii
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
        const val MG_DETECT = 0x00008
        const val MG_PET = 0x00010
        const val MG_OBJPILE = 0x00080

        /**
         * Tile index used for glyph-less entries (menus, empty map cells).
         * Equals NetHack's TILE_UNEXPLORED in the generated tile.c
         * (other.txt:197 "unexplored").  The native side sends this tile
         * index for items that have no real glyph, so the UI treats it as
         * "no tile to show".  Keep in sync with tile.c if the tileset is
         * regenerated.
         */
        const val TILE_UNEXPLORED = 1469
    }
}