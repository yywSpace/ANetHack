package com.yywspace.anethack

import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.Charset


object Utils {
    fun readAssetsFile(context: Context, filename: String): String {
        val stream = context.assets.open(filename)
        val fileLength = stream.available()
        val buffer = ByteArray(fileLength)
        stream.read(buffer)
        stream.close()
        return String(buffer, Charset.defaultCharset())
    }

    fun isAssetsFileExists(context: Context, filename: String): Boolean {
        try {
            context.assets.open(filename).use {
                return true
            }
        } catch(e: FileNotFoundException) {
            return false
        }
    }


    // 根据手机的分辨率从 dp 的单位 转成为 px(像素)
    fun dip2px(context: Context, dpValue: Float): Int {
        // 获取当前手机的像素密度（1个dp对应几个px）
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt() // 四舍五入取整
    }

    // 根据手机的分辨率从 px(像素) 的单位 转成为 dp
    fun px2dip(context: Context, pxValue: Float): Int {
        // 获取当前手机的像素密度（1个dp对应几个px）
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt() // 四舍五入取整
    }


    fun removeDialogFocus(dialog:AlertDialog){
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setGravity(Gravity.CENTER)
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
        }
    }
}