package com.yywspace.anethack.window

import com.yywspace.anethack.gameview.NHStatusView
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.NetHack


class NHWStatus(wid: Int, type:NHWindowType, nh: NetHack) : NHWindow(wid, type) {
    val status = NHStatus(nh.context)
    private var statusView: NHStatusView = nh.binding.statusView

    init {
        statusView.initStatus(nh, status)
    }
    override fun curs(x: Int, y: Int) {

    }

    override fun displayWindow(blocking: Boolean) {
        statusView.requestRedraw()
    }

    override fun clearWindow(isRogueLevel: Int) {

    }

    override fun destroyWindow() {

    }

    fun updateStatus(data: IntArray, texts: Array<String>) {
        status.replaceStatus(data, texts)
        statusView.requestRedraw()
    }

    override fun putString(attr: Int, msg: String, color: Int) {

    }
}