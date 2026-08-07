package com.shadiao.nb.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object NodeRepository {

    private var cachedNodes: List<ProxyNode> = emptyList()
    private var lastRefreshTime: Long = 0L
    private var appContext: Context? = null

    private const val PREF_NAME = "shadiao_nodes"
    private const val KEY_NODES = "nodes_json"
    private const val KEY_TIME = "last_refresh_time"

    private val json = Json { ignoreUnknownKeys = true }

    /** 初始化，必须在 Application.onCreate 中调用 */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadFromLocal()
    }

    /** 从本地 SharedPreferences 加载节点 */
    private fun loadFromLocal() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val nodesJson = prefs.getString(KEY_NODES, null)
            val time = prefs.getLong(KEY_TIME, 0L)
            if (nodesJson != null) {
                cachedNodes = json.decodeFromString<List<ProxyNode>>(nodesJson)
                lastRefreshTime = time
            }
        } catch (_: Exception) {
            cachedNodes = emptyList()
            lastRefreshTime = 0L
        }
    }

    /** 保存节点到本地 SharedPreferences */
    private fun saveToLocal() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_NODES, json.encodeToString(cachedNodes))
                .putLong(KEY_TIME, lastRefreshTime)
                .apply()
        } catch (_: Exception) { }
    }

    /** 从订阅链接刷新节点，失败时返回空列表，成功则自动保存到本地 */
    suspend fun refreshNodes(): List<ProxyNode> {
        val fetched = SubscriptionManager.fetchNodes()
        return if (fetched.isNotEmpty()) {
            cachedNodes = fetched
            lastRefreshTime = System.currentTimeMillis()
            saveToLocal()
            fetched
        } else {
            emptyList()
        }
    }

    fun getCachedNodes(): List<ProxyNode> = cachedNodes

    fun getLastRefreshTime(): Long = lastRefreshTime

    /** 是否需要刷新（无缓存或超过24小时） */
    fun shouldRefresh(): Boolean {
        if (cachedNodes.isEmpty()) return true
        val elapsed = System.currentTimeMillis() - lastRefreshTime
        return elapsed > 24 * 3600 * 1000L
    }
}