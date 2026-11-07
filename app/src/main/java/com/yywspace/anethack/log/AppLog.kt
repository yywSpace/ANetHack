package com.yywspace.anethack.log

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 应用统一日志入口。
 *
 * Debug 构建写 logcat 和文件；Release 只写 i/w/e 到文件。
 * 文件按 2MB 轮转，保留 3 个。
 */
object AppLog {

    private const val MAX_BYTES = 2 * 1024 * 1024L
    private const val MAX_FILES = 3

    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AppLog").apply { isDaemon = true }
    }

    @Volatile private var debug = false
    @Volatile private var dir: File? = null

    fun init(context: Context) {
        debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        folder(context)
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        write("I", "AppLog", "start v$version debug=$debug ${Build.MODEL} API${Build.VERSION.SDK_INT}", null)
    }

    fun d(tag: String, msg: String) {
        if (debug) write("D", tag, msg, null)
    }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)

    fun w(tag: String, msg: String, tr: Throwable? = null) = write("W", tag, msg, tr)

    fun e(tag: String, msg: String, tr: Throwable? = null) = write("E", tag, msg, tr)

    fun file(context: Context): File = File(folder(context), "app.log")

    fun files(context: Context): List<File> =
        folder(context).listFiles { it.name.startsWith("app.log") }?.toList().orEmpty()

    fun clear(context: Context): Int = files(context).count { it.delete() }

    private fun folder(context: Context): File =
        dir ?: File(context.filesDir, "logs/app").apply {
            mkdirs()
            setExecutable(true, true)
            dir = this
        }

    /** 等待已排队的日志写入完成 */
    fun flush(timeoutMs: Long = 300) {
        runCatching { executor.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    /** 最近 [count] 行，用于崩溃报告 */
    fun recentLines(count: Int): List<String> {
        val file = File(dir ?: return emptyList(), "app.log")
        if (!file.exists()) return emptyList()
        return runCatching { file.readLines().takeLast(count) }.getOrDefault(emptyList())
    }

    private fun write(level: String, tag: String, msg: String, tr: Throwable?) {
        val priority = when (level) {
            "D" -> Log.DEBUG
            "W" -> Log.WARN
            "E" -> Log.ERROR
            else -> Log.INFO
        }
        if (level != "D" || debug) Log.println(priority, tag, msg)

        val folder = dir ?: return
        val line = "${LocalDateTime.now().format(time)} $level/$tag: $msg"
        val stack = tr?.let { Log.getStackTraceString(it) }
        executor.execute { append(folder, line, stack) }
    }

    private fun append(folder: File, line: String, stack: String?) {
        runCatching {
            val file = File(folder, "app.log")
            if (file.length() > MAX_BYTES) rotate(folder)
            file.appendText(buildString {
                appendLine(line)
                if (stack != null) appendLine(stack)
            })
        }
    }

    private fun rotate(folder: File) {
        for (i in MAX_FILES - 1 downTo 1) {
            val from = if (i == 1) File(folder, "app.log") else File(folder, "app.log.${i - 1}")
            val to = File(folder, "app.log.$i")
            if (from.exists()) {
                to.delete()
                from.renameTo(to)
            }
        }
    }
}
