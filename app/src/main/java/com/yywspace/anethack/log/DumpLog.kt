package com.yywspace.anethack.log

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** dump 日志目录、列表和清理；展示交互留给 DumpLogDialog。 */
object DumpLog {
    private lateinit var directory: File

    data class Entry(
        val file: File,
        val name: String,
        val start: LocalDateTime?,
        val range: String
    )

    fun init(context: Context) {
        directory = File(context.filesDir, "nethackdir/dumplog")
    }

    fun file(name: String): File = File(directory, name)

    fun files(): List<File> =
        directory.listFiles { it.extension == "log" }?.toList().orEmpty()

    fun list(): List<Entry> {
        val parser = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        val display = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return files().mapNotNull { file ->
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
    fun clear(): Int = files().count { it.delete() }

    private fun parse(value: String, parser: DateTimeFormatter): LocalDateTime? =
        runCatching { LocalDateTime.parse(value, parser) }.getOrNull()
}
