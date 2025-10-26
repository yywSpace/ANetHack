package com.yywspace.anethack.command

class NHLineCommand(key:Char,  val line:String) :NHCommand(key) {
    constructor(line:String) : this(27.toChar(), line)
}