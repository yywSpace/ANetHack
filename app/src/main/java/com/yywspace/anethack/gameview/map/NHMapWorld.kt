package com.yywspace.anethack.gameview.map

import android.graphics.Point
import com.yywspace.anethack.entity.NHColor

/**
 * 地图世界状态。
 *
 * 持有格子、玩家、光标和待提交的帧；不依赖 Canvas、Camera 或 View。
 * 窗口协议（NHWMap）只负责把游戏事件转成世界更新。
 */
data class MapFrame(
    val tiles: List<NHMapWorld.Tile>,
    val curse: Point,
    val player: Point
)

class NHMapWorld(
    val width: Int = 80,
    val height: Int = 21
) {
    val curse = Point(-1, -1)
    val player = Point(-1, -1)

    /** 已提交的格子数据：绘制线程读，applyFrame 写。 */
    private val tiles = Array(height) { Array(width) { Tile() } }

    /** printTile 收集中的格子，displayWindow 时封成 MapFrame。 */
    private val pendingTiles = mutableListOf<Tile>()
    private val pendingCurse = Point(-1, -1)
    private val stateLock = Any()

    fun getTile(x: Int, y: Int): Tile = tiles[y][x]

    fun getTileList(c: Char): List<Tile> {
        val result = mutableListOf<Tile>()
        tiles.forEach { row ->
            row.forEach { tile ->
                if (tile.ch == c) result.add(tile)
            }
        }
        return result
    }

    fun applyFrame(frame: MapFrame) {
        frame.tiles.forEach { tile ->
            val dst = tiles[tile.y][tile.x]
            dst.x = tile.x
            dst.y = tile.y
            dst.glyph = tile.glyph
            dst.ch = tile.ch
            dst.color = tile.color
            dst.overlay = tile.overlay
        }
    }

    fun setPlayer(x: Int, y: Int) {
        player.x = x
        player.y = y
    }

    fun setPendingCursor(x: Int, y: Int) {
        synchronized(stateLock) {
            pendingCurse.x = x
            pendingCurse.y = y
        }
    }

    fun addPendingTile(tile: Tile) {
        synchronized(stateLock) {
            pendingTiles.add(tile)
        }
    }

    /** 把待提交的格子和光标封成一帧；没有变化时返回 null。 */
    fun buildFrame(): MapFrame? = synchronized(stateLock) {
        val cursorChanged = pendingCurse != curse
        if (cursorChanged) {
            curse.x = pendingCurse.x
            curse.y = pendingCurse.y
        }
        if (pendingTiles.isEmpty() && !cursorChanged)
            return null
        val frame = MapFrame(
            tiles = pendingTiles.toList(),
            curse = Point(curse.x, curse.y),
            player = Point(player.x, player.y)
        )
        pendingTiles.clear()
        frame
    }

    fun clear() {
        synchronized(stateLock) {
            pendingTiles.clear()
        }
    }

    /** 由绘制线程调用：清空已提交的格子数据。 */
    fun clearTiles() {
        synchronized(stateLock) {
            tiles.forEach { row ->
                row.forEach { tile ->
                    tile.glyph = -1
                    tile.overlay = 0
                    tile.ch = '0'
                }
            }
        }
    }

    /** 一个世界格子的绝对数据；坐标是固定的 tile 世界坐标。 */
    class Tile {
        var x = -1
        var y = -1
        var glyph = -1
        var overlay: Int = 0
        var ch: Char = '0'
        var color: NHColor = NHColor.NO_COLOR
    }
}
