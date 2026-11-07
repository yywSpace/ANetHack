package com.yywspace.anethack.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 日志文件的路径、导出和清理 */
object LogFiles {

    /** 打包 app 日志和崩溃报告，返回 zip 文件 */
    fun export(context: Context): File? {
        AppLog.flush()
        val apps = AppLog.files(context)
        val crashes = CrashLog.files(context)
        if (apps.isEmpty() && crashes.isEmpty()) return null

        val exportDir = File(context.filesDir, "logs/export").apply {
            mkdirs()
            setExecutable(true, true)
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val zip = File(exportDir, "logs-$stamp.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            apps.forEach { file ->
                out.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
            crashes.forEach { file ->
                out.putNextEntry(ZipEntry("crash/${file.name}"))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }

    fun clear(context: Context) {
        AppLog.clear(context)
        CrashLog.clear(context)
        File(context.filesDir, "logs/export").listFiles()?.forEach { it.delete() }
    }
}
