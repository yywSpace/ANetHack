package com.yywspace.anethack.log

import android.content.Context
import java.io.File

/** 崩溃日志目录、文件和清理；报告内容由 CrashHandler 生成。 */
object CrashLog {
    @Volatile
    private var directory: File? = null

    fun init(context: Context) {
        directory = File(context.filesDir, "logs/crash").apply {
            mkdirs()
            setExecutable(true, true)
        }
    }

    fun dir(context: Context): File =
        directory ?: File(context.filesDir, "logs/crash").apply {
            mkdirs()
            directory = this
        }

    fun files(context: Context): List<File> =
        dir(context).listFiles { it.extension == "log" }?.toList().orEmpty()

    fun latest(context: Context): File? = files(context).maxByOrNull { it.name }

    fun clear(context: Context): Int = files(context).count { it.delete() }

    fun newFile(stamp: String): File =
        File(checkNotNull(directory) { "CrashLog.init() not called" }, "crash-$stamp.log")
}
