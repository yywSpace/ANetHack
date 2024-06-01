package com.yywspace.anethack.command

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class NHAnswerCommand(key:Char, count:Int = 1) :NHCommand(key, count) {
}