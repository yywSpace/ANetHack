package com.yywspace.anethack.entity

import android.graphics.Color
import android.text.SpannableString
import java.time.LocalDateTime

data class NHMessage(private val value:NHString, val time: LocalDateTime) :Cloneable{
    private val _color:NHColor = value.nhColor

    override fun toString(): String {
        return value.toString()
    }

    fun attach(lastUpdate: LocalDateTime, color: NHColor?=null):NHMessage {
        val message = clone().apply {
            if (time ==lastUpdate)
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
        return NHMessage(value.copy(), time)
    }
}