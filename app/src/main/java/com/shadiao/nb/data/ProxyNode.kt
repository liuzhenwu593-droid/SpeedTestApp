package com.shadiao.nb.data

import kotlinx.serialization.Serializable
import java.io.Serializable as JSerializable

@Serializable
data class ProxyNode(
    val id: String,
    val name: String,
    val protocol: String,       // vmess, vless, trojan, ss, ssr
    val address: String,
    val port: Int,
    val rawConfig: String,      // 原始 uri 或 JSON 配置
    var latency: Int = -1,      // -1 = 未测试, -2 = 超时
    var speedTestTime: Long = 0
) : JSerializable {
    val latencyText: String
        get() = when {
            latency == -1 -> "—"
            latency == -2 -> "超时"
            latency == 0 -> "测速中…"
            else -> "${latency}ms"
        }

    val latencyColor: String
        get() = when {
            latency <= 0 -> "#EF5350"
            latency < 200 -> "#4CAF50"
            latency < 500 -> "#FF9800"
            else -> "#EF5350"
        }
}
