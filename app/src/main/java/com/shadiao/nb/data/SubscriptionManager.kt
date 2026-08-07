package com.shadiao.nb.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

object SubscriptionManager {

    private const val SUB_URL =
        "https://meitu.ccwu.cc/sub?token=27882ec74d1d608cbc6d0f6756bc174f"

    suspend fun fetchNodes(): List<ProxyNode> = withContext(Dispatchers.IO) {
        try {
            val text = fetchUrl(SUB_URL)
            val encoded = text.trim()
            val decoded = try {
                // Base64 解码
                val data = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                String(data, Charsets.UTF_8)
            } catch (_: Exception) {
                encoded
            }
            parseVlessLinks(decoded)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun fetchUrl(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "ShadiaoVPN/1.0")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseVlessLinks(raw: String): List<ProxyNode> {
        val nodes = mutableListOf<ProxyNode>()
        val lines = raw.lines().filter { it.startsWith("vless://") }

        for ((index, line) in lines.withIndex()) {
            try {
                val id = "node-${index + 1}"
                // 格式: vless://uuid@address:port?params#name
                val afterProtocol = line.removePrefix("vless://")
                val uuid = afterProtocol.substringBefore("@")
                val afterAt = afterProtocol.substringAfter("@")
                val address = afterAt.substringBefore(":")
                val afterAddr = afterAt.substringAfter(":")
                val port = afterAddr.substringBefore("?").toIntOrNull() ?: 443
                val paramsStr = afterAddr.substringAfter("?").substringBefore("#")
                val nameEncoded = afterAddr.substringAfter("#", "")
                val name = try {
                    URLDecoder.decode(nameEncoded, "UTF-8")
                } catch (_: Exception) {
                    nameEncoded
                }

                // 解析参数
                val params = paramsStr.split("&").associate {
                    val kv = it.split("=", limit = 2)
                    kv[0] to kv.getOrElse(1) { "" }
                }
                val security = params["security"] ?: "tls"
                val type = params["type"] ?: "ws"
                val host = params["host"] ?: ""
                val sni = params["sni"] ?: host
                val path = try { URLDecoder.decode(params["path"] ?: "/", "UTF-8") } catch (_: Exception) { params["path"] ?: "/" }
                val fp = params["fp"] ?: "chrome"
                val encryption = params["encryption"] ?: "none"

                // 解析区域
                val region = detectRegion(name)

                nodes.add(ProxyNode(
                    id = id,
                    name = name.ifBlank { "$region ${index + 1}" },
                    region = region,
                    address = address,
                    port = port,
                    uuid = uuid,
                    security = security,
                    type = type,
                    host = host,
                    sni = sni,
                    path = path,
                    fingerprint = fp,
                    encryption = encryption
                ))
            } catch (_: Exception) {
                // 跳过解析失败的节点
            }
        }
        return nodes
    }

    private fun detectRegion(name: String): String = when {
        name.contains("香港") || name.contains("HK") || name.contains("hk") -> "香港"
        name.contains("韩国") || name.contains("KR") || name.contains("kr") || name.contains("韩国") -> "韩国"
        name.contains("日本") || name.contains("JP") || name.contains("jp") || name.contains("日本") -> "日本"
        name.contains("新加坡") || name.contains("SG") || name.contains("sg") || name.contains("新加坡") -> "新加坡"
        name.contains("美国") || name.contains("US") || name.contains("us") || name.contains("美国") -> "美国"
        name.contains("台湾") || name.contains("TW") || name.contains("tw") || name.contains("台湾") -> "台湾"
        name.contains("德国") || name.contains("DE") || name.contains("de") -> "德国"
        name.contains("英国") || name.contains("UK") || name.contains("uk") -> "英国"
        name.contains("印度") || name.contains("IN") || name.contains("in") -> "印度"
        else -> "其他"
    }
}