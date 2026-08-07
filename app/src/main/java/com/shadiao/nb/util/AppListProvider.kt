package com.shadiao.nb.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * 已安装应用列表提供者。
 *
 * 关键点：Android 11（API 30）以后，应用默认只能看到系统应用与部分通过 <queries> 声明的应用。
 * 要列出全部用户应用做“分应用代理”，必须在 Manifest 中声明 android.permission.QUERY_ALL_PACKAGES。
 * 本类对 API 33+ 使用 PackageInfoFlags.of 的非过期 API，对低版本回退到旧 API。
 */
object AppListProvider {

    data class AppInfo(
        val packageName: String,
        val name: String,
        val icon: Drawable?,
        val isSystem: Boolean,
        /** 是否是当前 VPN 应用自身 */
        val isSelf: Boolean
    )

    /**
     * 查询设备上全部已安装应用（含系统应用），按名称排序。
     * 必须在 IO 线程调用。
     */
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val ownPkg = context.packageName

        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        return installed.mapNotNull { pi ->
            val app = pi.applicationInfo ?: return@mapNotNull null
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = try {
                pm.getApplicationLabel(app).toString()
            } catch (_: Exception) {
                pi.packageName
            }
            val icon = try {
                pm.getApplicationIcon(app)
            } catch (_: Exception) {
                null
            }
            AppInfo(
                packageName = pi.packageName,
                name = label,
                icon = icon,
                isSystem = isSystem,
                isSelf = pi.packageName == ownPkg
            )
        }.sortedWith(compareBy({ it.isSystem }, { it.name }))
    }
}
