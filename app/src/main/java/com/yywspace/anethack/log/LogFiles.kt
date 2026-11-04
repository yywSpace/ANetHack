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

    fun appLog(context: Context): File = File(context.filesDir, "logs/app/app.log")

    fun crashDir(context: Context): File = File(context.filesDir, "logs/crash")

    fun latestCrash(context: Context): File? =
        crashDir(context).listFiles { it.extension == "log" }
            ?.maxByOrNull { it.name }

    /** 打包 app 日志和崩溃报告，返回 zip 文件 */
    fun export(context: Context): File? {
        AppLog.flush()
        val appDir = File(context.filesDir, "logs/app")
        val apps = appDir.listFiles { file -> file.name.startsWith("app.log") }.orEmpty()
        val crashes = crashDir(context).listFiles { it.extension == "log" }.orEmpty()
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
        File(context.filesDir, "logs/app").listFiles()?.forEach { it.delete() }
        crashDir(context).listFiles()?.forEach { it.delete() }
        File(context.filesDir, "logs/export").listFiles()?.forEach { it.delete() }
    }
}
