package com.yywspace.anethack.entity

import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import androidx.core.text.toSpannable
import java.lang.RuntimeException

class NHStatus {
    private val fields = HashMap<StatusField, NHString>()
    private val newestFields = HashMap<StatusField, NHString>()
    private val conditionField = mutableListOf<NHString>()
    private val newestConditionField = mutableListOf<NHString>()
    private val fieldPercents = HashMap<StatusField, Int>()

    fun addStatusField(idx:Int, color:Int, attr:Int, percent:Int, value:String) {
        val field = StatusField.fromIdx(idx)
        val statusValue = if (field == StatusField.BL_TITLE) value else value.trim()
        if(field == StatusField.BL_CONDITION) {
            newestConditionField.add(NHString(statusValue, attr, color))
            return
        }
        if (newestFields.containsKey(field))
            newestFields[field]?.set(statusValue, attr, color)
        else
            newestFields[field] = NHString(statusValue, attr, color)
        fieldPercents[field] = percent
    }

    fun updateStatus() {
        fields.clear()
        conditionField.clear()
        fields.putAll(newestFields)
        conditionField.addAll(newestConditionField)
        newestFields.clear()
        newestConditionField.clear()
    }

    fun getField(field: StatusField): NHString? {
        return fields[field]
    }

    fun getFieldPercent(field: StatusField): Int {
        return fieldPercents[field]?:0
    }

    fun getSpannableField(field: StatusField): Spannable {
        if(field == StatusField.BL_CONDITION) {
            val conditions = SpannableStringBuilder("")
            conditionField.forEach {
                conditions.append(it.toSpannableString())
                conditions.append(" ")
            }
            return conditions.toSpannable()
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
