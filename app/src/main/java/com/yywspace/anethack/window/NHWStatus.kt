package com.yywspace.anethack.window

import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import com.yywspace.anethack.NHMessageSurfaceView
import com.yywspace.anethack.NHStatusSurfaceView
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.entity.NHStatus.*
import com.yywspace.anethack.NetHack


class NHWStatus(wid: Int, private val nh: NetHack) : NHWindow(wid) {
    val status = NHStatus()
    private var statusView: NHStatusSurfaceView = nh.binding.statusView

    init {
        statusView.initStatus(nh, status)
    }
    override fun curs(x: Int, y: Int) {

    }

    override fun displayWindow(blocking: Boolean) {
        Log.d("NHWStatus", "Status: $status")
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