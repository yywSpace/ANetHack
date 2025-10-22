package com.yywspace.anethack.command


class NHPosCommand(key:Char, val x:Int, val y:Int, mod:PosMod) :NHCommand(key) {
    var mod:Int = mod.ordinal

    constructor(x:Int, y:Int, mod:PosMod) : this(0.toChar(), x, y, mod)

    enum class PosMod {
        NONE, TRAVEL, LOOK
    }
}