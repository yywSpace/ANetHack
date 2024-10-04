package com.yywspace.anethack.window

abstract class NHWindow(val wid:Int, val type:NHWindowType) {
    abstract fun curs(x:Int, y:Int)

    abstract fun displayWindow(blocking: Boolean)
    abstract fun clearWindow(isRogueLevel: Int)
    abstract fun destroyWindow()

    abstract fun putString(attr: Int, msg: String, color: Int)

}


