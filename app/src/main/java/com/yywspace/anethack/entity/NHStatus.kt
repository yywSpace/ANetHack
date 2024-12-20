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
    private val newestFields = HashMap<StatusField, NHAttr>()
    private val conditionField = CopyOnWriteArrayList<NHAttr>()
    private val newestConditionField = mutableListOf<NHAttr>()
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
    val armorClass:NHAttr
        get() = getField(StatusField.BL_AC)
    val strength:NHAttr
        get() = getField(StatusField.BL_STR)
    val dexterity:NHAttr
        get() = getField(StatusField.BL_DX)
    val constitution:NHAttr
        get() = getField(StatusField.BL_CO)
    val intelligence:NHAttr
        get() = getField(StatusField.BL_IN)
    val wisdom:NHAttr
        get() = getField(StatusField.BL_WI)
    val charisma:NHAttr
        get() = getField(StatusField.BL_CH)
    val alignment:NHAttr
        get() = getField(StatusField.BL_ALIGN)
    val score:NHAttr
        get() = getField(StatusField.BL_SCORE)
    val gold:NHAttr
        get() = getField(StatusField.BL_GOLD)
    val time:NHAttr
        get() = getField(StatusField.BL_TIME)
    val hunger:NHAttr
        get() = getField(StatusField.BL_HUNGER)
    val encumbrance:NHAttr
        get() = getField(StatusField.BL_TITLE)
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
                RunMode.WALK -> NHString(context.getString(R.string.run_mode_walk), colorIdx = NHColor.CLR_WHITE.ordinal)
            }
            conditions.append(runModeStr.toSpannableString())
        }
        return conditions.toSpannable()
    }
    fun addStatusAttr(idx:Int, color:Int, attr:Int, percent:Int, fmtVal:String, realVal:String) {
        val field = StatusField.fromIdx(idx)
        val statusValue = if (field == StatusField.BL_TITLE) fmtVal else fmtVal.trim()
        val nhAttr = NHAttr(field, color, attr, percent, statusValue, realVal)
        if(field == StatusField.BL_CONDITION) {
            newestConditionField.add(nhAttr)
            return
        }
        newestFields[field] = nhAttr
    }

    fun updateStatus() {
        fields.clear()
        conditionField.clear()
        fields.putAll(newestFields)
        conditionField.addAll(newestConditionField)
        newestFields.clear()
        newestConditionField.clear()
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
                StatusField.values().forEach {
                    if(it.ordinal == idx)
                        return it
                }
                throw RuntimeException("idx not in filed")
            }
        }
    }
}
