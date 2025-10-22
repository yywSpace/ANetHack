package com.yywspace.anethack.command

import com.yywspace.anethack.entity.NHPlayer

class NHPlayerChooseCommand(key:Char, var player: NHPlayer) :NHCommand(key) {
    constructor(player:NHPlayer) : this(0.toChar(), player)

    fun toInfoArray():Array<String> {
        return arrayOf(player.player, player.playMod)
    }
}