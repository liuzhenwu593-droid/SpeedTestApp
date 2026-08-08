package com.shadiao.nb.util

import android.content.Context
import android.util.Log
import com.shadiao.nb.data.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 订阅管理器：下载、解析、缓存订阅
 */
class SubscriptionManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cacheFile: File by lazy {
        File(context.filesDir, "subscription_cache.json")
    }

    /**
     * 从远程获取并解析订阅
     */
    suspend fun fetchSubscription(url: String): Result<List<ProxyNode>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("空响应"))
            val nodes = SubscriptionParser.parse(body)
            if (nodes.isEmpty()) {
                return@withContext Result.failure(Exception("未解析到节点"))
            }
            // 缓存到本地
            saveCache(body)
            PrefsHelper.setLastSubUpdateTime(System.currentTimeMillis())
            Result.success(nodes)
        } catch (e: Exception) {
            Log.e(TAG, "获取订阅失败", e)
            Result.failure(e)
        }
    }

    /**
     * 从缓存加载节点
     */
    fun loadFromCache(): List<ProxyNode> {
        return try {
            if (!cacheFile.exists()) return emptyList()
            val body = cacheFile.readText()
            SubscriptionParser.parse(body)
        } catch (e: Exception) {
            Log.e(TAG, "读取缓存失败", e)
            emptyList()
        }
    }

    /**
     * 检查是否需要更新（距离上次更新超过24小时）
     */
    fun needsUpdate(): Boolean {
        val lastUpdate = PrefsHelper.getLastSubUpdateTime()
        if (lastUpdate == 0L) return true
        val elapsed = System.currentTimeMillis() - lastUpdate
        return elapsed >= com.shadiao.nb.config.AppConfig.SUBSCRIPTION_UPDATE_INTERVAL_MS
    }

    private fun saveCache(content: String) {
        try {
            cacheFile.writeText(content)
        } catch (e: Exception) {
            Log.e(TAG, "保存缓存失败", e)
        }
    }

    companion object {
        private const val TAG = "SubscriptionManager"
    }
}
