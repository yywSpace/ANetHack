package com.yywspace.anethack.command

import com.yywspace.anethack.entity.NHPlayer

class NHPlayerChooseCommand(key:Char, player:NHPlayer) :NHCommand(key) {
    var player:NHPlayer
    constructor(player:NHPlayer) : this(0.toChar(), player)
    init {
        this.player = player
    }

    fun toInfoArray():Array<String> {
        return arrayOf(player.player, player.playMod)
    }
}