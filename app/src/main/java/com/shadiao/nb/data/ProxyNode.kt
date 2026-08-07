package com.shadiao.nb.data

import kotlinx.serialization.Serializable

@Serializable
data class ProxyNode(
    val id: String,
    val name: String,
    val region: String,
    val address: String,
    val port: Int,
    val protocol: String = "vless",
    val uuid: String,
    val security: String = "tls",
    val type: String = "ws",
    val host: String,
    val sni: String,
    val path: String,
    val fingerprint: String = "chrome",
    val encryption: String = "none",
    var latency: Long = -1L,
    var downloadSpeed: Float = 0f,
    var isSelected: Boolean = false
) {
    val regionFlag: String
        get() = when (region) {
            "香港" -> "🇭🇰"
            "韩国" -> "🇰🇷"
            "日本" -> "🇯🇵"
            "新加坡" -> "🇸🇬"
            "美国" -> "🇺🇸"
            else -> "🌐"
        }

    val displayName: String
        get() = "$regionFlag $name"

    val latencyText: String
        get() = if (latency < 0) "—" else "$latency"
}