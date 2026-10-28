package com.yywspace.anethack.command

/**
 * 一条指令。[numberPrefix] 是紧挨在它前面的数字前缀（NetHack 里的数量，如 20S 的 20），
 * 0 表示没有前缀。
 *
 * 数字前缀最终仍然要按位发给游戏（游戏自己会累积数量），这一展开发生在取指令时，
 * 见 [NHBlockingCommandController.waitForCommand]；菜单这类窗口则直接读这个字段。
 */
open class NHCommand(val key:Char, var numberPrefix:Int = 0)
