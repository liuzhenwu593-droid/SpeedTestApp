package com.shadiao.nb.util

import android.content.Context
import android.content.SharedPreferences
import com.shadiao.nb.config.AppConfig

object PrefsHelper {

    private const val PREFS_NAME = "shadiao_vpn_prefs"

    // Keys
    private const val KEY_SUBSCRIPTION_URL = "subscription_url"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_PROXY_MODE = "proxy_mode"
    private const val KEY_APP_PROXY_ENABLED = "app_proxy_enabled"
    private const val KEY_APP_PROXY_PACKAGES = "app_proxy_packages"
    private const val KEY_LAST_SUB_UPDATE = "last_sub_update"
    private const val KEY_SELECTED_NODE = "selected_node"
    private const val KEY_AUTO_SPEEDTEST = "auto_speedtest"
    private const val KEY_FIRST_LAUNCH = "first_launch"

    // Theme modes
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    // Proxy modes
    const val MODE_RULE = 0
    const val MODE_GLOBAL = 1

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Subscription URL
    fun getSubscriptionUrl(): String =
        prefs.getString(KEY_SUBSCRIPTION_URL, AppConfig.DEFAULT_SUBSCRIPTION_URL)
            ?: AppConfig.DEFAULT_SUBSCRIPTION_URL

    fun setSubscriptionUrl(url: String) {
        prefs.edit().putString(KEY_SUBSCRIPTION_URL, url).apply()
    }

    // Theme
    fun getThemeMode(): Int = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    // Proxy mode
    fun getProxyMode(): Int = prefs.getInt(KEY_PROXY_MODE, MODE_RULE)

    fun setProxyMode(mode: Int) {
        prefs.edit().putInt(KEY_PROXY_MODE, mode).apply()
    }

    // App proxy
    fun isAppProxyEnabled(): Boolean = prefs.getBoolean(KEY_APP_PROXY_ENABLED, false)

    fun setAppProxyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_PROXY_ENABLED, enabled).apply()
    }

    fun getAppProxyPackages(): Set<String> =
        prefs.getStringSet(KEY_APP_PROXY_PACKAGES, emptySet()) ?: emptySet()

    fun setAppProxyPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_APP_PROXY_PACKAGES, packages).apply()
    }

    // Subscription update time
    fun getLastSubUpdateTime(): Long = prefs.getLong(KEY_LAST_SUB_UPDATE, 0)

    fun setLastSubUpdateTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_SUB_UPDATE, time).apply()
    }

    // Selected node
    fun getSelectedNode(): String? = prefs.getString(KEY_SELECTED_NODE, null)

    fun setSelectedNode(nodeId: String) {
        prefs.edit().putString(KEY_SELECTED_NODE, nodeId).apply()
    }

    // Auto speed test
    fun isAutoSpeedTestEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_SPEEDTEST, true)

    fun setAutoSpeedTest(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SPEEDTEST, enabled).apply()
    }

    // First launch
    fun isFirstLaunch(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

    fun setFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
}
