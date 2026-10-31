package com.yywspace.anethack

import android.content.Context
import com.google.gson.Gson
import java.io.FileNotFoundException
import java.nio.charset.Charset

/**
 * 所有跟 assets 有关的读写都放这里。
 */
object AssetsUtils {

    /** 读 assets 里的原始文本；失败时返回 null */
    fun readText(context: Context, filename: String): String? =
        runCatching {
            context.assets.open(filename).use { stream ->
                val buffer = ByteArray(stream.available())
                stream.read(buffer)
                String(buffer, Charset.defaultCharset())
            }
        }.getOrNull()

    /** 读 assets 里的 JSON 并映射成对象；失败时返回 null */
    fun <T> readJson(context: Context, filename: String, type: Class<T>): T? =
        readText(context, filename)?.let { runCatching { Gson().fromJson(it, type) }.getOrNull() }

    /** assets 里是否存在这个文件 */
    fun exists(context: Context, filename: String): Boolean =
        try {
            context.assets.open(filename).use { true }
        } catch (_: FileNotFoundException) {
            false
        }
}
