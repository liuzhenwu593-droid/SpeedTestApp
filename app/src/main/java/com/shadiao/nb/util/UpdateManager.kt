package com.shadiao.nb.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.shadiao.nb.BuildConfig
import com.shadiao.nb.data.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val UPDATE_URL = "https://yunpan.hynb.ccwu.cc/raw/update.json"

    private val json = Json { ignoreUnknownKeys = true }

    data class CheckResult(
        val hasUpdate: Boolean,
        val info: UpdateInfo?,
        val error: String?
    )

    /**
     * 检查是否有新版本
     */
    suspend fun checkUpdate(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val text = fetchUrl(UPDATE_URL)
            val info = json.decodeFromString<UpdateInfo>(text)
            if (info.versionCode > BuildConfig.VERSION_CODE) {
                CheckResult(true, info, null)
            } else {
                CheckResult(false, null, null)
            }
        } catch (e: Exception) {
            CheckResult(false, null, e.message)
        }
    }

    /**
     * 下载并安装 APK
     * Android 7+ 使用 FileProvider
     * Android 8+ 需要先检查 REQUEST_INSTALL_PACKAGES 权限
     */
    fun downloadAndInstall(context: Context, info: UpdateInfo) {
        try {
            val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "沙雕VPN.apk")
            apkFile.parentFile?.mkdirs()
            if (apkFile.exists()) apkFile.delete()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7+ 使用 DownloadManager + FileProvider
                downloadViaDownloadManager(context, info, apkFile)
            } else {
                // 旧版本直接用 DownloadManager
                downloadViaDownloadManager(context, info, apkFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun downloadViaDownloadManager(context: Context, info: UpdateInfo, file: File) {
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl)).apply {
            setTitle("沙雕VPN 更新")
            setDescription("正在下载 v${info.versionName}…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(file))
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // 监听下载完成
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    installApk(ctx, file)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    fun installApk(context: Context, file: File) {
        if (!file.exists()) return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // 某些设备没有文件管理器，尝试用系统安装器
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(intent) } catch (_: Exception) { }
        }
    }

    private fun fetchUrl(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "ShadiaoVPN/1.0")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}