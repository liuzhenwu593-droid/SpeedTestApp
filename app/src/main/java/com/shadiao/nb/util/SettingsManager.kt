package com.shadiao.nb.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 路由模式。
 * - [GLOBAL] 全局代理：进入 TUN 的流量全部走代理（旧默认行为，作为兜底）。
 * - [GEO]    智能分流：依据 geoip.dat / geosite.dat 分流，国内域名/IP 直连，
 *            广告拦截，其余走代理。这才是让 dat 文件真正"发挥作用"的模式。
 */
enum class RoutingMode { GLOBAL, GEO }

/**
 * 全局设置管理：分应用代理 + 路由模式。
 *
 * 通过 SharedPreferences 持久化，对外暴露 StateFlow 供 UI 与 Service 读取。
 * - 分应用代理始终启用：仅用户选中的应用走 VPN，其余直连（VpnService 白名单）。
 * - 路由模式决定进入 TUN 的流量在 Xray 内部如何分流（全局 / 智能分流）。
 * - 首次使用时自动预选常见的需要代理的应用（浏览器、Google、社交类等）。
 */
object SettingsManager {

    private const val PREFS_NAME = "shadiao_settings"
    private const val KEY_PERAPP_PACKAGES = "perapp_packages"
    private const val KEY_ROUTING_MODE = "routing_mode"
    private const val KEY_FIRST_RUN = "first_run_done"

    private var appContext: Context? = null

    private val _perAppPackages = MutableStateFlow<Set<String>>(emptySet())
    val perAppPackages: StateFlow<Set<String>> = _perAppPackages

    // 默认使用智能分流，让 geoip.dat / geosite.dat 直接生效
    private val _routingMode = MutableStateFlow(RoutingMode.GEO)
    val routingMode: StateFlow<RoutingMode> = _routingMode

    /** 必须在 Application.onCreate 中调用 */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        load()
    }

    private fun prefs() =
        appContext!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun load() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _perAppPackages.value = prefs.getStringSet(KEY_PERAPP_PACKAGES, null) ?: emptySet()
        val modeName = prefs.getString(KEY_ROUTING_MODE, RoutingMode.GEO.name) ?: RoutingMode.GEO.name
        _routingMode.value = try { RoutingMode.valueOf(modeName) } catch (_: Exception) { RoutingMode.GEO }
    }

    fun setPerAppPackages(packages: Set<String>) {
        _perAppPackages.value = packages
        prefs().edit().putStringSet(KEY_PERAPP_PACKAGES, packages).apply()
    }

    fun setRoutingMode(mode: RoutingMode) {
        _routingMode.value = mode
        prefs().edit().putString(KEY_ROUTING_MODE, mode.name).apply()
    }

    /** 快照读取（供 Service 在连接时一次性读取） */
    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        perAppPackages = _perAppPackages.value.toSet(),
        routingMode = _routingMode.value
    )

    /**
     * 根据设备已安装应用，自动预选常见的需要代理的应用包名。
     * 只在首次运行（无任何选择记录）时调用。
     */
    fun autoSelectDefaultApps(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FIRST_RUN, false)) return

        val installed = AppListProvider.getInstalledApps(context).map { it.packageName }.toSet()
        val selected = COMMON_PROXY_APPS.filter { it in installed }.toMutableSet()

        // 检测已安装的浏览器
        BROWSER_KEYWORDS.forEach { kw ->
            installed.filter { it.contains(kw, true) }.forEach { selected.add(it) }
        }

        if (selected.isNotEmpty()) {
            setPerAppPackages(selected)
        }
        prefs.edit().putBoolean(KEY_FIRST_RUN, true).apply()
    }

    /** 一次性快照，避免 Service 在 IO 线程读取时状态被改动 */
    data class SettingsSnapshot(
        val perAppPackages: Set<String>,
        val routingMode: RoutingMode
    )

    /** 常见需要代理的应用包名前缀/全名 */
    private val COMMON_PROXY_APPS = listOf(
        // Google
        "com.google.android.youtube",
        "com.android.youtube",
        "com.google.android.gms",
        "com.android.vending",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.maps",
        "com.google.android.apps.photos",
        "com.google.android.gm",
        "com.google.android.apps.translate",
        "com.google.android.inputmethod.latin",
        // 社交
        "com.twitter.android",
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.whatsapp",
        "org.telegram.messenger",
        "org.telegram.plus",
        "com.discord",
        "com.reddit.frontpage",
        "com.spotify.music",
        "com.netflix.mediaclient",
        "com.amazon.mShop.android.shopping",
        "com.twitch.android.app",
        // 其他常见
        "com.github.android",
        "com.microsoft.emmx",
        "com.cloudflare.onedotonedotonedotone",
    )

    /** 浏览器关键词（用于自动检测已安装浏览器） */
    private val BROWSER_KEYWORDS = listOf("chrome", "firefox", "browser", "brave", "opera", "vivaldi", "duckduckgo")
}
