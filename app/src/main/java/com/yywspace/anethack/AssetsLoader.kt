package com.yywspace.anethack

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class AssetsLoader(val context: Context) {
    fun loadAssets(pathList: List<String>,
                   overwrite: Boolean = false,
                   onLoadFinished: ((overwrite: Boolean) -> Unit)? = null) {
        for (path in pathList) {
            if (File(context.filesDir, path).exists() && !overwrite)
                continue
            loadGameAssets(path)
        }
        onLoadFinished?.invoke(overwrite)
    }

    private fun loadGameAssets(path: String) {
        val fileList = context.assets.list(path)
        val file = File(context.filesDir, path)
        if (fileList.isNullOrEmpty()) {
            context.assets.open(path).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            file.mkdirs()
            for (name in fileList)
                loadGameAssets("$path/$name")
        }
    }
}
