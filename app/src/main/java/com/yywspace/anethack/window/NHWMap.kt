package com.yywspace.anethack.window

import android.graphics.Point
import android.util.Log
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.map.NHMapSurfaceView

class NHWMap (wid: Int, val nh: NetHack) : NHWindow(wid) {
    val tileCols = 80
    val tileRows = 21
    private var mapView: NHMapSurfaceView = nh.binding.mapView
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
        if(firstCenter || nh.prefs.lockView) {
            mapView.centerView(cx,cy)
            firstCenter = false
        }
    }
    
    override fun curs(x: Int, y: Int) {
        curse.x = x
        curse.y = y
    }

    override fun displayWindow(blocking: Boolean) {
        Log.d("NHWMap", "displayWindow")
    }

    override fun clearWindow(isRogueLevel: Int) {
        Log.d("NHWMap", "clearWindow")
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
        Log.d("NHWMap", "printTile(wid: $wid, x: $x, y: $y, tile: $tile, ch: ${CP437_UNICODE[ch and 0xff]}, col: $col, special: ${Integer.toBinaryString(special)})")
        tiles[y][x].glyph = tile
        tiles[y][x].ch = CP437_UNICODE[ch and 0xff]
        tiles[y][x].color = NHColor.fromInt(col)
        tiles[y][x].overlay = special
    }

    class Tile {
        var glyph = -1
        var overlay: Int = 0
        var ch:Char = '0'
        var color: NHColor = NHColor.NO_COLOR
    }

    companion object {
        private val CP437_UNICODE = charArrayOf(
            0x00A0.toChar(),
            0x263A.toChar(),
            0x263B.toChar(),
            0x2665.toChar(),
            0x2666.toChar(),
            0x2663.toChar(),
            0x2660.toChar(),
            0x2022.toChar(),
            0x25D8.toChar(),
            0x25CB.toChar(),
            0x25D9.toChar(),
            0x2660.toChar(),
            0x2661.toChar(),
            0x266A.toChar(),
            0x266B.toChar(),
            0x2609.toChar(),
            0x25BA.toChar(),
            0x25C4.toChar(),
            0x2195.toChar(),
            0x203C.toChar(),
            0x00B6.toChar(),
            0x00A7.toChar(),
            0x25AC.toChar(),
            0x2607.toChar(),
            0x2191.toChar(),
            0x2193.toChar(),
            0x2192.toChar(),
            0x2190.toChar(),
            0x221F.toChar(),
            0x2194.toChar(),
            0x25B2.toChar(),
            0x25BC.toChar(),
            0x0020.toChar(),
            0x0021.toChar(),
            0x0022.toChar(),
            0x0023.toChar(),
            0x0024.toChar(),
            0x0025.toChar(),
            0x0026.toChar(),
            0x0027.toChar(),
            0x0028.toChar(),
            0x0029.toChar(),
            0x002A.toChar(),
            0x002B.toChar(),
            0x002C.toChar(),
            0x002D.toChar(),
            0x002E.toChar(),
            0x002F.toChar(),
            0x0030.toChar(),
            0x0031.toChar(),
            0x0032.toChar(),
            0x0033.toChar(),
            0x0034.toChar(),
            0x0035.toChar(),
            0x0036.toChar(),
            0x0037.toChar(),
            0x0038.toChar(),
            0x0039.toChar(),
            0x003A.toChar(),
            0x003B.toChar(),
            0x003C.toChar(),
            0x003D.toChar(),
            0x003E.toChar(),
            0x003F.toChar(),
            0x0040.toChar(),
            0x0041.toChar(),
            0x0042.toChar(),
            0x0043.toChar(),
            0x0044.toChar(),
            0x0045.toChar(),
            0x0046.toChar(),
            0x0047.toChar(),
            0x0048.toChar(),
            0x0049.toChar(),
            0x004A.toChar(),
            0x004B.toChar(),
            0x004C.toChar(),
            0x004D.toChar(),
            0x004E.toChar(),
            0x004F.toChar(),
            0x0050.toChar(),
            0x0051.toChar(),
            0x0052.toChar(),
            0x0053.toChar(),
            0x0054.toChar(),
            0x0055.toChar(),
            0x0056.toChar(),
            0x0057.toChar(),
            0x0058.toChar(),
            0x0059.toChar(),
            0x005A.toChar(),
            0x005B.toChar(),
            0x005C.toChar(),
            0x005D.toChar(),
            0x005E.toChar(),
            0x005F.toChar(),
            0x0060.toChar(),
            0x0061.toChar(),
            0x0062.toChar(),
            0x0063.toChar(),
            0x0064.toChar(),
            0x0065.toChar(),
            0x0066.toChar(),
            0x0067.toChar(),
            0x0068.toChar(),
            0x0069.toChar(),
            0x006A.toChar(),
            0x006B.toChar(),
            0x006C.toChar(),
            0x006D.toChar(),
            0x006E.toChar(),
            0x006F.toChar(),
            0x0070.toChar(),
            0x0071.toChar(),
            0x0072.toChar(),
            0x0073.toChar(),
            0x0074.toChar(),
            0x0075.toChar(),
            0x0076.toChar(),
            0x0077.toChar(),
            0x0078.toChar(),
            0x0079.toChar(),
            0x007A.toChar(),
            0x007B.toChar(),
            0x007C.toChar(),
            0x007D.toChar(),
            0x007E.toChar(),
            0x2206.toChar(),
            0x00C7.toChar(),
            0x00FC.toChar(),
            0x00E9.toChar(),
            0x00E2.toChar(),
            0x00E4.toChar(),
            0x00E0.toChar(),
            0x00E5.toChar(),
            0x00E7.toChar(),
            0x00EA.toChar(),
            0x00EB.toChar(),
            0x00E8.toChar(),
            0x00EF.toChar(),
            0x00EE.toChar(),
            0x00EC.toChar(),
            0x00C4.toChar(),
            0x00C5.toChar(),
            0x00C9.toChar(),
            0x00E6.toChar(),
            0x00C6.toChar(),
            0x00F4.toChar(),
            0x00F6.toChar(),
            0x00F2.toChar(),
            0x00FB.toChar(),
            0x00F9.toChar(),
            0x00FF.toChar(),
            0x00D6.toChar(),
            0x00DC.toChar(),
            0x00A2.toChar(),
            0x00A3.toChar(),
            0x00A5.toChar(),
            0x20A3.toChar(),
            0x0192.toChar(),
            0x00E1.toChar(),
            0x00ED.toChar(),
            0x00F3.toChar(),
            0x00FA.toChar(),
            0x00F1.toChar(),
            0x00D1.toChar(),
            0x00AA.toChar(),
            0x00BA.toChar(),
            0x00BF.toChar(),
            0x2310.toChar(),
            0x00AC.toChar(),
            0x00BD.toChar(),
            0x00BC.toChar(),
            0x00A1.toChar(),
            0x00AB.toChar(),
            0x00BB.toChar(),
            0x2591.toChar(),
            0x2592.toChar(),
            0x2591.toChar(),
            0x2502.toChar(),
            0x2524.toChar(),
            0x2561.toChar(),
            0x2562.toChar(),
            0x2556.toChar(),
            0x2555.toChar(),
            0x2563.toChar(),
            0x2551.toChar(),
            0x2557.toChar(),
            0x255D.toChar(),
            0x255C.toChar(),
            0x255B.toChar(),
            0x2510.toChar(),
            0x2514.toChar(),
            0x2534.toChar(),
            0x252C.toChar(),
            0x251C.toChar(),
            0x2500.toChar(),
            0x253C.toChar(),
            0x255E.toChar(),
            0x255F.toChar(),
            0x255A.toChar(),
            0x2554.toChar(),
            0x2569.toChar(),
            0x2566.toChar(),
            0x2560.toChar(),
            0x2550.toChar(),
            0x256C.toChar(),
            0x2567.toChar(),
            0x2568.toChar(),
            0x2564.toChar(),
            0x2565.toChar(),
            0x2559.toChar(),
            0x2558.toChar(),
            0x2552.toChar(),
            0x2553.toChar(),
            0x256B.toChar(),
            0x256A.toChar(),
            0x2518.toChar(),
            0x250C.toChar(),
            0x2588.toChar(),
            0x2584.toChar(),
            0x258C.toChar(),
            0x2590.toChar(),
            0x2580.toChar(),
            0x03B1.toChar(),
            0x00DF.toChar(),
            0x0393.toChar(),
            0x03C0.toChar(),
            0x03A3.toChar(),
            0x03C3.toChar(),
            0x00B5.toChar(),
            0x03C4.toChar(),
            0x03A6.toChar(),
            0x0398.toChar(),
            0x03A9.toChar(),
            0x03B4.toChar(),
            0x221E.toChar(),
            0x03C6.toChar(),
            0x25A0.toChar(),
            0x002B.toChar(),
            0x2261.toChar(),
            0x00B1.toChar(),
            0x2265.toChar(),
            0x2264.toChar(),
            0x2320.toChar(),
            0x2321.toChar(),
            0x00F7.toChar(),
            0x2248.toChar(),
            0x00B0.toChar(),
            0x2219.toChar(),
            0x00B7.toChar(),
            0x221A.toChar(),
            0x207F.toChar(),
            0x00B2.toChar(),
            0x25A0.toChar(),
            0x00A0.toChar()
        )
    }
}