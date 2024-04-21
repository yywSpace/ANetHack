package com.yywspace.anethack.entity

import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MaskFilterSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan


data class NHString(var value:String = "", var attr:Int = 0, val colorIdx:Int = NHColor.NO_COLOR.ordinal) {
    var attrs:List<TextAttr>
    var nhColor: NHColor = NHColor.NO_COLOR

    init {
        attrs = TextAttr.fromAttr(attr)
        nhColor = NHColor.fromInt(colorIdx)
    }
    fun set(value:String, attr: Int, colorIdx: Int) {
        this.attr = attr
        this.value = value
        this.nhColor = NHColor.fromInt(colorIdx)
        this.attrs = TextAttr.fromAttr(attr)
    }

    override fun toString(): String {
        return value
    }

    fun toSpannableString(): SpannableString{
        val span = SpannableString(value)
        if(!attrs.contains(TextAttr.ATR_INVERSE))
            span.setSpan(ForegroundColorSpan(nhColor.toColor()), 0, value.length, SPAN_INCLUSIVE_EXCLUSIVE)
        else {
            span.setSpan(BackgroundColorSpan(nhColor.toColor()), 0, value.length, SPAN_INCLUSIVE_EXCLUSIVE)
            span.setSpan(ForegroundColorSpan(Color.BLACK), 0, value.length, SPAN_INCLUSIVE_EXCLUSIVE)
        }
        attrs.forEach {
            if(it == TextAttr.ATR_BOLD)
                span.setSpan(StyleSpan(Typeface.BOLD), 0, value.length, SPAN_INCLUSIVE_EXCLUSIVE)
            if(it == TextAttr.ATR_ULINE)
                span.setSpan(UnderlineSpan(), 0, value.length, SPAN_INCLUSIVE_EXCLUSIVE)
            if(it == TextAttr.ATR_ULINE)
                span.setSpan(StyleSpan(Typeface.ITALIC), 0, value.length, SPAN_INCLUSIVE_EXCLUSIVE)
            if(it == TextAttr.ATR_DIM) {
                val blurMaskFilterSpan = MaskFilterSpan(BlurMaskFilter(5f, Blur.SOLID))
                span.setSpan(blurMaskFilterSpan, 0, value.length, SPAN_INCLUSIVE_EXCLUSIVE)
            // ATR_BLINK not support
            }

        }
        return span
    }

    enum class TextAttr {
        ATR_NONE,       // #define ATR_NONE       0
        ATR_BOLD,       // #define ATR_BOLD       1
        ATR_DIM,        // #define ATR_DIM        2
        ATR_ITALIC,     // #define ATR_ITALIC     3
        ATR_ULINE,      // #define ATR_ULINE      4
        ATR_BLINK,      // #define ATR_BLINK      5
        ATR_PLACEHOLDER1,
        ATR_INVERSE,    // #define ATR_INVERSE    7
        ATR_UNDEFINED;

        companion object {
            fun fromAttr(attr: Int): List<TextAttr> {
                val textAttrs = mutableListOf<TextAttr>()
                TextAttr.values().forEach {
                    if(attr and (1 shl it.ordinal) != 0)
                        textAttrs.add(it)
                }
                return textAttrs
            }
        }
    }
}