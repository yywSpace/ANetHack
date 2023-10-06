package com.yywspace.anethack.window

import android.graphics.Point
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.NHMapView
import com.yywspace.anethack.NetHack

class NHWMap (wid: Int, val nh: NetHack) : NHWindow(wid) {
    val tileCols = 80
    val tileRows = 21
    private var mapView: NHMapView = nh.binding.mapView
    private var firstCenter = true

    // 光标位置
    val curse = Point(-1,-1)
    // 玩家位置
    val player = Point(-1,-1)

    val tiles = Array(tileRows){Array(tileCols) { Tile() } }

    init {
        mapView.initMap(nh,this)
    }

    fun clipAround(cx: Int, cy: Int,ux: Int, uy: Int) {
        player.x = ux
        player.y = uy
        if(firstCenter) {
            mapView.centerView(cx,cy)
            firstCenter = false
        }
    }
    
    override fun curs(x: Int, y: Int) {
        curse.x = x
        curse.y = y
    }

    override fun displayWindow(blocking: Boolean) {
        mapView.invalidate()
    }

    override fun clearWindow(isRogueLevel: Int) {
        firstCenter = true
        for (row in tiles)
            for (col in row) {
                col.glyph = -1
            }
    }

    override fun destroyWindow() {
        curse.set(0, 0)
        player.set(0, 0)
        tiles.forEach {
            it.forEach { tile ->
                tile.glyph = -1
            }
        }
    }

    override fun putString(attr: Int, msg: String, color: Int) {

    }

    fun printTile(x: Int, y: Int, tile: Int, ch: Int, col: Int, special: Int) {
        tiles[y][x].glyph = tile
        tiles[y][x].ch =ch.toChar()
        tiles[y][x].color = NHColor.fromInt(col)
        tiles[y][x].overlay = special.toShort()
    }

    class Tile {
        var glyph = -1
        var overlay: Short = 0
        var ch:Char = '0'
        var color: NHColor = NHColor.NO_COLOR
    }
}