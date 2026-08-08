package com.shadiao.nb.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shadiao.nb.config.AppConfig

/**
 * 订阅自动更新 Worker
 * 通过 WorkManager 定期执行（24小时）
 */
class SubscriptionUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val subManager = SubscriptionManager(applicationContext)
            val url = PrefsHelper.getSubscriptionUrl()
            val result = subManager.fetchSubscription(url)
            if (result.isSuccess) {
                val nodes = result.getOrDefault(emptyList())
                Log.i(TAG, "自动更新成功，${nodes.size} 个节点")
                Result.success()
            } else {
                Log.w(TAG, "自动更新失败: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动更新异常", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SubUpdateWorker"

        const val WORK_NAME = "subscription_auto_update"

        /**
         * 计算下次执行的延迟
         */
        fun calculateDelay(): Long {
            val lastUpdate = PrefsHelper.getLastSubUpdateTime()
            if (lastUpdate == 0L) return 0L
            val elapsed = System.currentTimeMillis() - lastUpdate
            val remaining = AppConfig.SUBSCRIPTION_UPDATE_INTERVAL_MS - elapsed
            return if (remaining > 0) remaining else 0L
        }
    }
}
