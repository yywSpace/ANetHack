package com.yywspace.anethack.window

import android.util.Log
import com.yywspace.anethack.map.NHStatusSurfaceView
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.NetHack


class NHWStatus(wid: Int, type:NHWindowType, nh: NetHack) : NHWindow(wid, type) {
    val status = NHStatus(nh.context)
    private var statusView: NHStatusSurfaceView = nh.binding.statusView

    init {
        statusView.initStatus(nh, status)
    }
    override fun curs(x: Int, y: Int) {

    }

    override fun displayWindow(blocking: Boolean) {
        status.updateStatus()
        // 状态渲染完成（每回合全量 renderField 提交后）→ 唤醒状态绘制线程
        statusView.requestRedraw()
    }

    override fun clearWindow(isRogueLevel: Int) {

    }

    override fun destroyWindow() {

    }

    fun renderField(fldIdx: Int, fldName: String, fmtVal: String, realVal:String, attr: Int, color: Int, percent:Int) {
        // 只更新数据；绘制由 displayWindow（渲染完成，全量提交后）统一触发
        status.addStatusAttr(fldIdx, color, attr, percent, fmtVal, realVal)
    }

    override fun putString(attr: Int, msg: String, color: Int) {

    }
}