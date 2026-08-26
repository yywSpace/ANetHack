package com.yywspace.anethack.entity

import android.text.SpannableString
import java.time.LocalDateTime

data class NHMessage(private val value:NHString, val time: LocalDateTime) :Cloneable{
    private val _color:NHColor = value.nhColor

    override fun toString(): String {
        return value.toString()
    }

    /** 当前显示颜色索引（attach 后：最新批绿色/旧消息原色），布局缓存 key 用 */
    val colorIndex: Int
        get() = value.nhColor.ordinal

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