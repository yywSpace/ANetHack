package com.yywspace.anethack.command

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class NHLineCommand(key:Char,  val line:String) :NHCommand(key) {
    constructor(line:String) : this(27.toChar(), line)
}