package com.shadiao.nb.util

import android.util.Log
import com.shadiao.nb.data.ProxyNode
import com.shadiao.nb.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 节点测速工具
 * 通过 TCP 连接测试延迟
 */
object SpeedTestUtil {

    private const val TAG = "SpeedTestUtil"

    /**
     * 测试单个节点延迟
     * @return 延迟毫秒数，-2 表示超时
     */
    suspend fun testNode(node: ProxyNode): Int = withContext(Dispatchers.IO) {
        if (node.address.isBlank() || node.port == 0) return@withContext -2

        val result = withTimeoutOrNull(AppConfig.SPEED_TEST_TIMEOUT_MS.toLong()) {
            val start = System.currentTimeMillis()
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(node.address, node.port), AppConfig.SPEED_TEST_TIMEOUT_MS)
                socket.close()
                (System.currentTimeMillis() - start).toInt()
            } catch (e: Exception) {
                Log.d(TAG, "测速失败 ${node.name}: ${e.message}")
                -2
            }
        }

        result ?: -2
    }

    /**
     * 批量测速
     * @param nodes 节点列表
     * @param concurrency 并发数
     * @param onProgress 每完成一个节点回调
     */
    suspend fun testAll(
        nodes: List<ProxyNode>,
        concurrency: Int = AppConfig.SPEED_TEST_CONCURRENCY,
        onProgress: (ProxyNode) -> Unit
    ) = coroutineScope {
        // 分批并发
        nodes.chunked(concurrency).forEach { batch ->
            batch.map { node ->
                async {
                    node.latency = 0 // 标记测速中
                    onProgress(node)
                    val latency = testNode(node)
                    node.latency = latency
                    node.speedTestTime = System.currentTimeMillis()
                    onProgress(node)
                }
            }.awaitAll()
        }
    }
}
