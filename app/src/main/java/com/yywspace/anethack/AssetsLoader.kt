package com.yywspace.anethack

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class AssetsLoader(val context: Context) {
    fun loadAssets(pathList:List<String>, overwrite:Boolean = false, onLoadFinished:((overwrite:Boolean)->Unit)? = null) {
        for (path in pathList) {
            if(File(context.filesDir, path).exists() && !overwrite)
                continue
            var curCnt = 0
            val maxCnt = countGameAssets(path)
            if (maxCnt <= 0) continue
            loadGameAssets(path) { file ->
                curCnt += 1
                Log.d("loadAssets", "$curCnt-$file")
            }
        }
        onLoadFinished?.invoke(overwrite)
    }

    private fun loadGameAssets(path: String, onFileLoaded:((String)->Unit)? = null){
        val fileList = context.assets.list(path)
        val file = File(context.filesDir, path)
        if (fileList.isNullOrEmpty()) {
            FileOutputStream(file).use { fileOut ->
                context.assets.open(path).copyTo(fileOut)
            }
            onFileLoaded?.invoke(file.name)
        } else {
            if (!file.exists()) file.mkdirs()
            for (i in fileList.indices) {
                loadGameAssets(path + "/" + fileList[i], onFileLoaded)
            }
        }
    }

    private fun countGameAssets(path: String):Int{
        var fileCount = 0
        val fileList = context.assets.list(path)
        val file = File(context.filesDir, path)
        if (fileList.isNullOrEmpty()) {
            fileCount += 1
        } else {
            if (!file.exists()) file.mkdirs()
            for (i in fileList.indices)
                fileCount += countGameAssets(path + "/" + fileList[i])
        }
        return fileCount
    }
}