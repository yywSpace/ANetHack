package com.yywspace.anethack

import android.app.Activity
import android.view.View
import android.view.ViewStub
import android.widget.Toast

/**
 * 负责启动前的资源准备：
 * 需要更新时 inflate 一个透明加载层，在后台复制资源，完成后再进入游戏。
 */
class AssetStartupController(
    private val activity: Activity,
    private val loadingStub: ViewStub,
    private val onReady: () -> Unit
) {
    private companion object {
        /** 超过这个时间还没加载完才显示，避免快速更新时闪一下 */
        const val LOADING_SHOW_DELAY_MS = 200L
        /** 一旦显示，至少保留这么久，避免刚好超过阈值时闪一下 */
        const val LOADING_MIN_VISIBLE_MS = 300L
    }
    private var loadingOverlay: View? = null

    fun start() {
        val loader = AssetsLoader(activity)
        if (!loader.needsUpdate()) {
            onReady()
            return
        }

        loadingOverlay = loadingStub.inflate()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val showLoading = Runnable {
            loadingOverlay?.visibility = View.VISIBLE
        }
        activity.window.decorView.postDelayed(showLoading, LOADING_SHOW_DELAY_MS)

        Thread({
            val success = loader.loadAssetsIfNeeded()
            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
            if (elapsed >= LOADING_SHOW_DELAY_MS) {
                val remaining = LOADING_SHOW_DELAY_MS + LOADING_MIN_VISIBLE_MS - elapsed
                if (remaining > 0)
                    Thread.sleep(remaining)
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed)
                    return@runOnUiThread

                activity.window.decorView.removeCallbacks(showLoading)
                loadingOverlay?.visibility = View.GONE
                loadingOverlay = null
                if (success) {
                    onReady()
                } else {
                    Toast.makeText(
                        activity,
                        R.string.asset_loading_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, "AssetLoader").start()
    }
}
