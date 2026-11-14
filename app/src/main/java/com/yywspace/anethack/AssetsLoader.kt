package com.yywspace.anethack

import android.content.Context
import com.yywspace.anethack.log.AppLog
import java.io.File
import java.io.FileOutputStream

/**
 * 把 APK 内 nethackdir 的资源复制到 filesDir。
 *
 * nethackdir 目前只有一层：根文件 + 玩家数据目录。
 * 文件按 app versionCode 覆盖，玩家目录只创建、不覆盖内容。
 */
class AssetsLoader(
    val context: Context,
    private val baseDir: File = context.filesDir
) {
    private val prefs = SharedPreferencesUtils(context)

    private companion object {
        const val TAG = "AssetsLoader"
        const val ASSET_ROOT = "nethackdir"

        val USER_DATA_DIRS = setOf("save", "bones", "score", "levels", "dumplog")
    }

    /** 当前 app 版本是否需要更新资源 */
    fun needsUpdate(): Boolean {
        val versionCode = currentVersionCode()
        val installedVersion = prefs.installedAssetVersionCode
        val updated = installedVersion != versionCode
        AppLog.i(
            TAG,
            "check assets version: current=$versionCode installed=$installedVersion update=$updated"
        )
        return updated
    }

    /** 需要时复制资源；成功后初始化用户配置并记录版本。调用方应放在后台线程。 */
    fun loadAssetsIfNeeded(): Boolean {
        if (!needsUpdate())
            return true

        val versionCode = currentVersionCode()
        val success = loadAssets()
        if (success) {
            ensureUserConfig()
            prefs.installedAssetVersionCode = versionCode
            AppLog.i(TAG, "asset update completed: version=$versionCode")
        } else {
            AppLog.w(TAG, "asset update failed, keep installed version")
        }
        return success
    }

    /** 用户配置只在不存在时从模板初始化，资源更新不覆盖用户修改 */
    private fun ensureUserConfig() {
        val template = File(baseDir, "nethackdir/nethackrc.default")
        val userConf = File(baseDir, "nethackdir/.nethackrc")
        if (!userConf.exists() && template.exists()) {
            AppLog.i(TAG, "initialize user config from template")
            template.copyTo(userConf)
        }

        val kitTemplate = File(baseDir, "nethackdir/wizkit.default")
        val wizkit = File(baseDir, "nethackdir/wizkit")
        if (!wizkit.exists() && kitTemplate.exists()) {
            AppLog.i(TAG, "initialize wizard kit from template")
            kitTemplate.copyTo(wizkit)
        }

    }

    private fun currentVersionCode(): Int {
        val packageInfo =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            else
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.longVersionCode.toInt()
    }

    private fun loadAssets(): Boolean = try {
        AppLog.i(TAG, "load assets start: root=${File(baseDir, ASSET_ROOT)}")
        val root = File(baseDir, ASSET_ROOT)
        root.mkdirs()

        val children = context.assets.list(ASSET_ROOT).orEmpty()
        AppLog.d(TAG, "asset root entries: ${children.joinToString()}")
        for (name in children) {
            if (name in USER_DATA_DIRS) {
                // 用户目录补齐缺失文件，不覆盖已有内容
                copyMissingTree("$ASSET_ROOT/$name", File(root, name))
                continue
            }

            copyAsset("$ASSET_ROOT/$name", File(root, name))
        }
        AppLog.i(TAG, "load assets finished")
        true
    } catch (e: Exception) {
        AppLog.e(TAG, "load assets failed", e)
        false
    }

    /**
     * 递归遍历 assets 里的用户目录，补齐缺失的目录/文件。
     */
    private fun copyMissingTree(assetPath: String, targetDir: File) {
        // 先保证这一层目录存在；已存在时 mkdirs 不会破坏里面的内容
        targetDir.mkdirs()

        // assets.list 返回目录下的直接子项：可能是文件，也可能是子目录
        val children = context.assets.list(assetPath).orEmpty()
        for (name in children) {
            val childAsset = "$assetPath/$name"
            val target = File(targetDir, name)

            // 有子项说明它是目录；继续递归，只补里面缺失的内容
            val grandchildren = context.assets.list(childAsset).orEmpty()
            if (grandchildren.isNotEmpty()) {
                copyMissingTree(childAsset, target)
                continue
            }

            // 没有子项就是文件；用户目录已显式创建，不依赖空目录判断
            if (!target.exists()) {
                AppLog.d(TAG, "copy user asset: $childAsset")
                copyAsset(childAsset, target)
            }
        }
    }

    private fun copyAsset(path: String, target: File) {
        AppLog.d(TAG, "copy asset: $path -> ${target.absolutePath}")
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        try {
            context.assets.open(path).use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target))
                    throw IllegalStateException("rename failed: $path")
            }
        } finally {
            if (tmp.exists())
                tmp.delete()
        }
    }
}
