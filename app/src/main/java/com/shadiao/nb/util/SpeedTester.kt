package com.shadiao.nb.util

import com.shadiao.nb.data.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object SpeedTester {

    // 单节点超时缩短到 2 秒（原 3 秒太慢）
    private const val TIMEOUT_MS = 2000
    // 并发上限，避免同时打开过多 socket
    private val concurrencyLimit = Semaphore(8)

    suspend fun testLatencyDirect(node: ProxyNode): Long = withContext(Dispatchers.IO) {
        concurrencyLimit.withPermit {
            try {
                val start = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(node.address, node.port), TIMEOUT_MS)
                socket.close()
                System.currentTimeMillis() - start
            } catch (_: Exception) {
                -1
            }
        }
    }

    /**
     * 并发测速多个节点，比逐个串行快 N 倍。
     * 使用信号量限制并发数（8），避免同时打开过多连接。
     */
    suspend fun testLatencyBatch(nodes: List<ProxyNode>): Map<String, Long> = coroutineScope {
        nodes.map { node ->
            async(Dispatchers.IO) {
                node.id to testLatencyDirect(node)
            }
        }.awaitAll().toMap()
    }
}
