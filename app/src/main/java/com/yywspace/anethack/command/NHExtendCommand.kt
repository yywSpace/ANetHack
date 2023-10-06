package com.yywspace.anethack.command


class NHExtendCommand(key:Char, val idx:Int) :NHCommand(key) {
    var name:String = ""
    constructor(idx:Int) : this('#', idx)
    constructor(name:String) : this('#', -1) {
        if (name.length > 1)
            this.name = name.substring(1)
    }
    companion object {
        var last:NHExtendCommand? = null
    }
}
