package com.yywspace.anethack.entity

import android.text.SpannableString

data class NHAttr(
    val type: NHStatus.StatusField, private val colorIdx:Int, var attr:Int, val percent:Int, val fmtVal:String, val realVal:String
) {
    private val nhAttrValue = NHString(fmtVal, attr, colorIdx)
    val color
        get() = nhAttrValue.nhColor.toColor()

    fun toSpannableString(): SpannableString {
        return nhAttrValue.toSpannableString()
    }
}
