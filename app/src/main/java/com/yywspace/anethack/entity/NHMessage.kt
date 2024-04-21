package com.yywspace.anethack.entity

import java.time.LocalDateTime

data class NHMessage(val value:NHString, val time: LocalDateTime) :Cloneable{
    var isSelectable = false
    override fun toString(): String {
        return value.toString()
    }
    public override fun clone(): NHMessage {
        return NHMessage(value.copy(), time)
    }
}