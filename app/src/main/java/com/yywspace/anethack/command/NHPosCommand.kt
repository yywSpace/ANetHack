package com.yywspace.anethack.command

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class NHPosCommand(key:Char, val x:Int, val y:Int, mod:PosMod) :NHCommand(key) {
    var mod:Int
    constructor(x:Int, y:Int, mod:PosMod) : this(0.toChar(), x, y, mod)
    init {
        this.mod = mod.ordinal
    }

    enum class PosMod {
        NONE, TRAVEL, LOOK
    }
}