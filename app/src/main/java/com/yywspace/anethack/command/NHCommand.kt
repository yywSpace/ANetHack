package com.yywspace.anethack.command

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class NHCommand(val key:Char, var count:Int = 1)