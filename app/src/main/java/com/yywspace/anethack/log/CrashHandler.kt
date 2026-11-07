package com.yywspace.anethack.log

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Java/Kotlin 未捕获异常：写一份崩溃报告，再交回系统默认处理 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private var version: String? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { write(thread, throwable) }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun write(thread: Thread, throwable: Throwable) {
        AppLog.flush()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = CrashLog.newFile(stamp)
        val report = buildString {
            appendLine("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("version: $version")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("thread: ${thread.name}")
            appendLine()
            appendLine(throwable.stackTraceToString())
            appendLine()
            appendLine("--- recent app log ---")
            AppLog.recentLines(200).forEach { appendLine(it) }
        }
        file.writeText(report)
    }
}
