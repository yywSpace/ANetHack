package com.yywspace.anethack

import android.app.Application
import com.yywspace.anethack.log.AppLog
import com.yywspace.anethack.log.CrashHandler
import com.yywspace.anethack.log.CrashLog
import com.yywspace.anethack.log.DumpLog
import com.yywspace.anethack.theme.AppTheme

/**
 * 在进程启动、任何 Activity 创建之前先把主题套上。
 *
 * AppCompat 的夜间模式是异步生效的：如果在 Activity 里才设置，
 * 首次进入时布局会先按旧的（浅色）资源 inflate，出现"半截白"的界面，
 * 等下一次切换主题才恢复正常。放到 Application 里可以避开这个时间差。
 */
class NHApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppTheme.applyTheme(this)
        AppLog.init(this)
        CrashLog.init(this)
        CrashHandler.install(this)
        DumpLog.init(this)
    }
}
