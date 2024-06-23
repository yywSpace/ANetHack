package com.yywspace.anethack

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect





class NHTileSet(val nh: NetHack) {
    private val tileCache = mutableMapOf<Int, Bitmap>()
    private var tileBitmap: Bitmap? = null
    private var tileSetName:String? = null
    var tileWidth = 0
    var tileHeight = 0

    fun getPlayerTile():Bitmap? {
        nh.getWMap()?.apply {
            val tile = tiles[player.y][player.x]
            if (tile.glyph < 0)
                return null
            return getTile(tile.glyph)
        }
        return null
    }
    fun getTile(tile:Int):Bitmap? {
        tileBitmap?.apply {
            var bitmap = tileCache[tile]
            if (bitmap == null) {
                val tx = tile % (width / tileWidth)
                val ty = tile / (width / tileWidth)
                bitmap = Bitmap.createBitmap(this, tx * tileWidth, ty * tileHeight, tileWidth, tileHeight)
                tileCache[tile] = bitmap
            }
            return bitmap
        }
        return null
    }

    fun updateTileSet() {
        when(nh.prefs.tileSet) {
            // default
            "2" -> {
                val am: AssetManager = nh.context.resources.assets
                am.open("tiles/default_tiles_16.bmp").use {
                    tileBitmap = BitmapFactory.decodeStream(it)
                }
                tileHeight = 16
                tileWidth = 16
            }
        }
    }

    fun isTileSetChange():Boolean {
        if (tileSetName == null)
            tileSetName = nh.prefs.tileSet
        if (tileSetName != nh.prefs.tileSet) {
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

    fun getTileOverlay(overlay: Int): Bitmap? {
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
            nh.context.resources.assets.open(overlayPath).use {
                return BitmapFactory.decodeStream(it)
            }
        }
        return null
    }

    companion object {
        const val MG_INVIS = 0x00004
        const val MG_DETECT = 0x00008
        const val MG_PET = 0x00010
        const val MG_OBJPILE = 0x00080

    }
}