package com.yywspace.anethack.entity

import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import androidx.core.text.toSpannable
import java.lang.RuntimeException

class NHStatus {
    val fields = HashMap<StatusField, NHString>()
    private val conditionsField =HashMap<Int, MutableList<NHString>>()
    private var conditionsIndex = 0
    fun setField(idx:Int, color:Int, attr:Int, value:String) {
        val field = StatusField.fromIdx(idx)
        if(field == StatusField.BL_CONDITION) {
            if(conditionsField.containsKey(conditionsIndex))
                conditionsField[conditionsIndex]?.add(NHString(value, attr, color))
            else
                conditionsField[conditionsIndex] = mutableListOf(NHString(value, attr, color))
            return
        }
        if (fields.containsKey(field))
            fields[field]?.set(value, attr, color)
        else
            fields[field] = NHString(value, attr, color)
    }
    fun getField(field: StatusField): NHString? {
        return fields[field]
    }
    fun getSpannableField(field: StatusField): Spannable {
        if(field == StatusField.BL_CONDITION) {
            // because nowhere clear status, everytime we must get newest condition
            conditionsIndex++
            if(conditionsField.isNotEmpty()) {
                val index = conditionsField.keys.max()
                val conditions = SpannableStringBuilder("")
                conditionsField[index]?.forEach {
                    conditions.append(it.toSpannableString())
                    conditions.append(" ")
                }
                return conditions.toSpannable()
            }
            return SpannableString("")
        }
        return if (fields.containsKey(field))
            fields[field]!!.toSpannableString()
        else
            SpannableString("")
    }

    override fun toString(): String {
        return "condition: ${getSpannableField(StatusField.BL_CONDITION)}, ${fields.values.joinToString(" ")}"
    }
    enum class StatusField {
        BL_TITLE,
        BL_STR, BL_DX, BL_CO, BL_IN, BL_WI, BL_CH,
        BL_ALIGN, BL_SCORE, BL_CAP, BL_GOLD, BL_ENE, BL_ENEMAX,
        BL_XP, BL_AC, BL_HD, BL_TIME, BL_HUNGER, BL_HP,
        BL_HPMAX, BL_LEVELDESC, BL_EXP, BL_CONDITION,
        MAXBLSTATS;
        companion object {
            fun fromIdx(idx:Int): StatusField {
                StatusField.values().forEach {
                    if(it.ordinal == idx)
                        return it
                }
                throw RuntimeException("idx not in filed")
            }
        }
    }
}
