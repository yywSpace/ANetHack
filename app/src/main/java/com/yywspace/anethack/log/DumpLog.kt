package com.yywspace.anethack.log

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** dump 日志目录、列表和清理；展示交互留给 DumpLogDialog。 */
object DumpLog {
    @Volatile
    private var directory: File? = null

    data class Entry(
        val file: File,
        val name: String,
        val start: LocalDateTime?,
        val range: String
    )

    fun init(context: Context) {
        directory = File(context.filesDir, "logs/dump").apply {
            mkdirs()
            setExecutable(true, true)
        }
    }

    fun dir(context: Context): File =
        directory ?: File(context.filesDir, "logs/dump").apply {
            mkdirs()
            directory = this
        }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun files(context: Context): List<File> =
        dir(context).listFiles { it.extension == "log" }?.toList().orEmpty()

    fun list(context: Context): List<Entry> {
        val parser = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        val display = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return files(context).mapNotNull { file ->
            // 用户名_开始时间_结束时间
            val parts = file.nameWithoutExtension.split("_")
            if (parts.size < 3) return@mapNotNull null
            val name = parts[0]
            val start = parse(parts[1], parser)
            val end = parse(parts[2], parser)
            val range = if (start != null && end != null)
                "${start.format(display)} ~ ${end.format(display)}"
            else
                file.name
            Entry(file, name, start, range)
        }.sortedByDescending { it.start }
    }

    /** 删除全部 dump 日志，返回删除数量。 */
    fun clear(context: Context): Int = files(context).count { it.delete() }

    private fun parse(value: String, parser: DateTimeFormatter): LocalDateTime? =
        runCatching { LocalDateTime.parse(value, parser) }.getOrNull()
}
