package com.yywspace.anethack.window

import android.util.Log
import com.yywspace.anethack.map.NHStatusSurfaceView
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.NetHack


class NHWStatus(wid: Int, type:NHWindowType, nh: NetHack) : NHWindow(wid, type) {
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

    fun renderField(fldIdx: Int, fldName: String, fmtVal: String, realVal:String, attr: Int, color: Int, percent:Int) {
        status.addStatusAttr(fldIdx, color, attr, percent, fmtVal, realVal)
        Log.d(
            "NHWStatus",
            "renderField(fldIdx:$fldIdx, fldName:$fldName, fmtVal:$fmtVal, realVal:$realVal, attr:$attr, color:$color, percent:$percent)"
        )
    }

    override fun putString(attr: Int, msg: String, color: Int) {

    }
}