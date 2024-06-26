package com.yywspace.anethack.window

import android.util.Log
import com.yywspace.anethack.map.NHStatusSurfaceView
import com.yywspace.anethack.entity.NHStatus
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
        status.updateStatus()
        Log.d("NHWStatus", "Status: $status")
    }

    override fun clearWindow(isRogueLevel: Int) {

    }

    override fun destroyWindow() {

    }

    fun renderField(fldIdx: Int, fldName: String, value: String, attr: Int, color: Int, percent:Int) {
        status.addStatusField(fldIdx, color, attr, percent, value)
        Log.d(
            "NHWStatus",
            "renderStatus(fldIdx:$fldIdx, fldName:$fldName, value:$value, attr:$attr, color:$color, percent:$percent)"
        )
    }

    override fun putString(attr: Int, msg: String, color: Int) {

    }
}