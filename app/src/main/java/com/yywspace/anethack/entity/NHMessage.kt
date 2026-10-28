package com.yywspace.anethack.entity

import android.text.SpannableString
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHPosCommand

data class NHMessage(
    private val value: NHString,
    /** 产生这条消息时，游戏正在处理的那条指令（用来标记消息来源，可能为 null） */
    val source: NHCommand? = null,
    /** 载入历史存档时补的旧消息：永远不算"最新一批" */
    val history: Boolean = false
) : Cloneable {
    private val _color: NHColor = value.nhColor

    /** 这条消息是否由 look 指令产生：只有这类消息才去解析怪物名 */
    val fromLook: Boolean
        get() = (source as? NHPosCommand)?.mod == NHPosCommand.PosMod.LOOK.ordinal

    override fun toString(): String {
        return value.toString()
    }

    fun attach(lastSource: NHCommand?, color: NHColor?=null):NHMessage {
        val message = clone().apply {
            if (!history && source === lastSource)
                value.nhColor = NHColor.CLR_GREEN
            else {
                value.nhColor = _color
                if (color != null)
                    value.nhColor = color
            }
        }
        return message
    }

    fun toSpannableString(): SpannableString {
        return value.toSpannableString()
    }

    public override fun clone(): NHMessage {
        return NHMessage(value.copy(), source, history)
    }
}
