package com.yywspace.anethack.entity

data class NHPlayer(val player:String, val playMod:String) {
    var isSelected = false

    override fun toString(): String {
        return player
    }
}