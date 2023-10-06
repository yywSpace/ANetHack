package com.yywspace.anethack.window

import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.entity.NHStatus.*
import com.yywspace.anethack.NetHack


class NHWStatus(wid: Int, private val nh: NetHack) : NHWindow(wid) {
    val status = NHStatus()
    override fun curs(x: Int, y: Int) {

    }

    override fun displayWindow(blocking: Boolean) {
        updateStatus()
        Log.d("NHWStatus", "Status: $status")
    }

    private fun updateStatus() {
        val align = status.getSpannableField(StatusField.BL_ALIGN)
        val title = status.getSpannableField(StatusField.BL_TITLE)
        val st = status.getSpannableField(StatusField.BL_STR)
        val dx = status.getSpannableField(StatusField.BL_DX)
        val co = status.getSpannableField(StatusField.BL_CO)
        val intel = status.getSpannableField(StatusField.BL_IN)
        val wi = status.getSpannableField(StatusField.BL_WI)
        val ch = status.getSpannableField(StatusField.BL_CH)
        val hp = SpannableStringBuilder(status.getSpannableField(StatusField.BL_HP)).append(status.getSpannableField(StatusField.BL_HPMAX))
        val pw = SpannableStringBuilder(status.getSpannableField(StatusField.BL_ENE)).append(status.getSpannableField(StatusField.BL_ENEMAX))
        val gold = status.getSpannableField(StatusField.BL_GOLD)
        val ac = status.getSpannableField(StatusField.BL_AC)
        val xp = status.getSpannableField(StatusField.BL_XP)
        val time = status.getSpannableField(StatusField.BL_TIME)
        val level = status.getSpannableField(StatusField.BL_LEVELDESC)
        val hunger = status.getSpannableField(StatusField.BL_HUNGER)
        val cap = status.getSpannableField(StatusField.BL_CAP)
        val condition = status.getSpannableField(StatusField.BL_CONDITION)
        nh.runOnUi { binding, _ ->
            binding.statusTitle.text = title
            binding.statusAlign.text = align
            binding.statusSt.text = st
            binding.statusDx.text = dx
            binding.statusCo.text = co
            binding.statusIn.text = intel
            binding.statusWi.text = wi
            binding.statusCh.text = ch
            binding.statusHp.text = hp
            binding.statusPw.text = pw
            binding.statusGold.text = gold
            binding.statusAc.text = ac
            binding.statusXp.text = xp
            binding.statusTime.text = time
            binding.statusLevel.text = level
            binding.statusHunger.text = hunger
            binding.statusCap.text = cap
            binding.statusCondition.text = condition

            binding.statusTitle.visibility = if (title.isEmpty()) View.GONE else View.VISIBLE
            binding.statusAlign.visibility = if (align.isEmpty()) View.GONE else View.VISIBLE
            binding.statusSt.visibility = if (st.isEmpty()) View.GONE else View.VISIBLE
            binding.statusDx.visibility = if (dx.isEmpty()) View.GONE else View.VISIBLE
            binding.statusCo.visibility = if (co.isEmpty()) View.GONE else View.VISIBLE
            binding.statusIn.visibility = if (intel.isEmpty()) View.GONE else View.VISIBLE
            binding.statusWi.visibility = if (wi.isEmpty()) View.GONE else View.VISIBLE
            binding.statusCh.visibility =  if (ch.isEmpty()) View.GONE else View.VISIBLE
            binding.statusHp.visibility = if (hp.isEmpty()) View.GONE else View.VISIBLE
            binding.statusPw.visibility = if (pw.isEmpty()) View.GONE else View.VISIBLE
            binding.statusGold.visibility = if (gold.isEmpty()) View.GONE else View.VISIBLE
            binding.statusAc.visibility = if (ac.isEmpty()) View.GONE else View.VISIBLE
            binding.statusXp.visibility = if (xp.isEmpty()) View.GONE else View.VISIBLE
            binding.statusTime.visibility = if (time.isEmpty()) View.GONE else View.VISIBLE
            binding.statusLevel.visibility = if (level.isEmpty()) View.GONE else View.VISIBLE
            binding.statusHunger.visibility = if (hunger.isEmpty()) View.GONE else View.VISIBLE
            binding.statusCap.visibility = if (cap.isEmpty()) View.GONE else View.VISIBLE
            binding.statusCondition.visibility = if (condition.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun clearWindow(isRogueLevel: Int) {

    }

    override fun destroyWindow() {

    }

    fun renderField(fldIdx: Int, fldName: String, value: String, attr: Int, color: Int) {
        status.setField(fldIdx, color, attr, value)
        Log.d(
            "NHWStatus",
            "renderStatus(fldIdx:$fldIdx, fldName:$fldName, value:$value, attr:$attr, color:$color)"
        )
    }

    override fun putString(attr: Int, msg: String, color: Int) {

    }
}