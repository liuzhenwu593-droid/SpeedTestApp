package com.shadiao.nb.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 管理 geoip.dat 和 geosite.dat 文件
 * dat 文件在 CI 构建时打包进 APK assets，首次运行时拷贝到 filesDir 供 Xray 使用
 */
object GeoDataManager {

    private const val TAG = "GeoDataManager"
    private const val GEOIP_FILE = "geoip.dat"
    private const val GEOSITE_FILE = "geosite.dat"

    /**
     * 确保 dat 文件存在于 filesDir 中，首次从 assets 拷贝
     * @return true 表示文件已就绪
     */
    suspend fun ensureDatFiles(context: Context): Boolean = withContext(Dispatchers.IO) {
        val datDir = context.filesDir
        var success = true

        if (!copyIfMissing(context, GEOIP_FILE, File(datDir, GEOIP_FILE))) {
            Log.e(TAG, "geoip.dat 拷贝失败")
            success = false
        }
        if (!copyIfMissing(context, GEOSITE_FILE, File(datDir, GEOSITE_FILE))) {
            Log.e(TAG, "geosite.dat 拷贝失败")
            success = false
        }

        success
    }

    /**
     * 检查 dat 文件是否已存在
     */
    fun hasDatFiles(context: Context): Boolean {
        val datDir = context.filesDir
        return File(datDir, GEOIP_FILE).exists() && File(datDir, GEOSITE_FILE).exists()
    }

    /**
     * 如果目标文件不存在，从 assets 拷贝过去
     */
    private fun copyIfMissing(context: Context, assetName: String, destFile: File): Boolean {
        if (destFile.exists() && destFile.length() > 0) {
            return true
        }

        return try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
            Log.d(TAG, "从 assets 拷贝成功: ${destFile.name} (${destFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "从 assets 拷贝失败: ${destFile.name}", e)
            if (destFile.exists()) destFile.delete()
            false
        }
    }
}