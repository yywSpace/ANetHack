package com.yywspace.anethack.entity

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import androidx.core.text.toSpannable
import com.yywspace.anethack.R
import java.lang.RuntimeException
import java.util.concurrent.CopyOnWriteArrayList

class NHStatus(val context: Context) {
    private val fields = HashMap<StatusField, NHAttr>()
    private val conditionField = CopyOnWriteArrayList<NHAttr>()
    var runMode:RunMode = RunMode.WALK

    val title:NHAttr
        get() = getField(StatusField.BL_TITLE)
    val hitPoints:NHAttr
        get() = getField(StatusField.BL_HP)
    val maxHitPoints:NHAttr
        get() = getField(StatusField.BL_HPMAX)
    val power:NHAttr
        get() = getField(StatusField.BL_ENE)
    val maxPower:NHAttr
        get() = getField(StatusField.BL_ENEMAX)
    val expLevel:NHAttr
        get() = getField(StatusField.BL_XP)
    val expPoints:NHAttr
        get() = getField(StatusField.BL_EXP)
    val hitDice:NHAttr
        get() = getField(StatusField.BL_HD)
    val charisma:NHAttr
        get() = getField(StatusField.BL_CH)
    val dungeonLevel:NHAttr
        get() = getField(StatusField.BL_LEVELDESC)

    fun getConditionSpannable(): Spannable {
        val conditions = SpannableStringBuilder("")
        conditionField.forEach {
            conditions.append(it.toSpannableString())
            conditions.append(" ")
        }
        if (!title.isEmpty()) {
            val runModeStr = when(runMode) {
                RunMode.RUN -> NHString(context.getString(R.string.run_mode_run), colorIdx = NHColor.CLR_GREEN.ordinal)
                RunMode.TRAVEL -> NHString(context.getString(R.string.run_mode_travel), colorIdx = NHColor.CLR_GREEN.ordinal)
                RunMode.WALK -> NHString(context.getString(R.string.run_mode_walk), colorIdx = NHColor.CLR_WHITE.ordinal)
            }
            conditions.append(runModeStr.toSpannableString())
        }
        return conditions.toSpannable()
    }
    fun replaceStatus(data: IntArray, texts: Array<String>) {
        val nextFields = HashMap<StatusField, NHAttr>()
        val nextConditions = ArrayList<NHAttr>()

        for (i in 0 until texts.size / 2) {
            val base = i * 4
            val field = StatusField.fromIdx(data[base])
            val statusValue = if (field == StatusField.BL_TITLE)
                texts[i * 2] else texts[i * 2].trim()
            val attr = NHAttr(
                field, data[base + 2], data[base + 1], data[base + 3],
                statusValue, texts[i * 2 + 1]
            )
            if (field == StatusField.BL_CONDITION)
                nextConditions.add(attr)
            else
                nextFields[field] = attr
        }

        fields.clear()
        fields.putAll(nextFields)
        conditionField.clear()
        conditionField.addAll(nextConditions)
    }

    fun getField(field: StatusField): NHAttr {
        return fields.getOrDefault(field,
            NHAttr(
                StatusField.MAXBLSTATS,
                NHColor.NO_COLOR.ordinal,
                NHString.TextAttr.ATR_NONE.ordinal,
                0,"",""
            ))
    }



    override fun toString(): String {
        return "condition: ${getConditionSpannable()}, ${fields.values.joinToString(" ")}"
    }

    enum class RunMode{
        RUN,
        TRAVEL,
        WALK
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
                entries.forEach {
                    if(it.ordinal == idx)
                        return it
                }
                throw RuntimeException("idx not in filed")
            }
        }
    }
}
