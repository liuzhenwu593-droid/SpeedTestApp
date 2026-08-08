package com.shadiao.nb.util

import android.util.Base64
import com.shadiao.nb.data.ProxyNode
import org.json.JSONObject

/**
 * 订阅链接解析器
 * 支持: vmess://, vless://, trojan://, ss://, ssr://
 */
object SubscriptionParser {

    fun parse(content: String): List<ProxyNode> {
        val trimmed = content.trim()
        // 尝试 Base64 解码
        val decoded = try {
            if (trimmed.contains("://")) {
                trimmed
            } else {
                String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            trimmed
        }

        val nodes = mutableListOf<ProxyNode>()
        for (line in decoded.lines()) {
            val parsed = parseLine(line.trim()) ?: continue
            nodes.add(parsed)
        }
        return nodes
    }

    private fun parseLine(line: String): ProxyNode? {
        return when {
            line.startsWith("vmess://") -> parseVmess(line)
            line.startsWith("vless://") -> parseVless(line)
            line.startsWith("trojan://") -> parseTrojan(line)
            line.startsWith("ss://") -> parseSS(line)
            line.startsWith("ssr://") -> parseSSR(line)
            else -> null
        }
    }

    private fun parseVmess(uri: String): ProxyNode? {
        return try {
            val b64 = uri.removePrefix("vmess://")
            val json = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            val obj = JSONObject(json)
            val name = obj.optString("ps", obj.optString("remarks", "Unknown"))
            val address = obj.optString("add", "")
            val port = obj.optInt("port", 0)
            ProxyNode(
                id = "vmess_${address}_${port}_${name.hashCode()}",
                name = name,
                protocol = "vmess",
                address = address,
                port = port,
                rawConfig = json
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVless(uri: String): ProxyNode? {
        return try {
            val noScheme = uri.removePrefix("vless://")
            val fragIndex = noScheme.lastIndexOf("#")
            val name = if (fragIndex >= 0) {
                java.net.URLDecoder.decode(noScheme.substring(fragIndex + 1), "UTF-8")
            } else {
                "Unknown"
            }
            val main = if (fragIndex >= 0) noScheme.substring(0, fragIndex) else noScheme
            val atIndex = main.indexOf("@")
            if (atIndex < 0) return null
            val uuid = main.substring(0, atIndex)
            val hostPort = main.substring(atIndex + 1)
            val colonIndex = hostPort.indexOf(":")
            val queryIndex = hostPort.indexOf("?")
            val endIdx = if (queryIndex > 0) queryIndex else hostPort.length
            val address = hostPort.substring(0, colonIndex)
            val port = hostPort.substring(colonIndex + 1, endIdx).toIntOrNull() ?: 0
            ProxyNode(
                id = "vless_${address}_${port}_${name.hashCode()}",
                name = name,
                protocol = "vless",
                address = address,
                port = port,
                rawConfig = uri
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTrojan(uri: String): ProxyNode? {
        return try {
            val noScheme = uri.removePrefix("trojan://")
            val fragIndex = noScheme.lastIndexOf("#")
            val name = if (fragIndex >= 0) {
                java.net.URLDecoder.decode(noScheme.substring(fragIndex + 1), "UTF-8")
            } else {
                "Unknown"
            }
            val main = if (fragIndex >= 0) noScheme.substring(0, fragIndex) else noScheme
            val atIndex = main.indexOf("@")
            if (atIndex < 0) return null
            val hostPort = main.substring(atIndex + 1)
            val colonIndex = hostPort.indexOf(":")
            val queryIndex = hostPort.indexOf("?")
            val endIdx = if (queryIndex > 0) queryIndex else hostPort.length
            val address = hostPort.substring(0, colonIndex)
            val port = hostPort.substring(colonIndex + 1, endIdx).toIntOrNull() ?: 0
            ProxyNode(
                id = "trojan_${address}_${port}_${name.hashCode()}",
                name = name,
                protocol = "trojan",
                address = address,
                port = port,
                rawConfig = uri
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSS(uri: String): ProxyNode? {
        return try {
            val noScheme = uri.removePrefix("ss://")
            val fragIndex = noScheme.lastIndexOf("#")
            val name = if (fragIndex >= 0) {
                java.net.URLDecoder.decode(noScheme.substring(fragIndex + 1), "UTF-8")
            } else {
                "Unknown"
            }
            val main = if (fragIndex >= 0) noScheme.substring(0, fragIndex) else noScheme
            // ss://base64(method:password)@host:port  or  ss://base64(method:password@host:port)
            val atIndex = main.indexOf("@")
            if (atIndex >= 0) {
                val hostPort = main.substring(atIndex + 1)
                val colonIndex = hostPort.lastIndexOf(":")
                val address = hostPort.substring(0, colonIndex)
                val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: 0
                ProxyNode(
                    id = "ss_${address}_${port}_${name.hashCode()}",
                    name = name,
                    protocol = "ss",
                    address = address,
                    port = port,
                    rawConfig = uri
                )
            } else {
                // 整体 base64
                val decoded = String(Base64.decode(main, Base64.DEFAULT), Charsets.UTF_8)
                val atIdx2 = decoded.lastIndexOf("@")
                val colonIdx = decoded.lastIndexOf(":")
                val address = decoded.substring(atIdx2 + 1, colonIdx)
                val port = decoded.substring(colonIdx + 1).toIntOrNull() ?: 0
                ProxyNode(
                    id = "ss_${address}_${port}_${name.hashCode()}",
                    name = name,
                    protocol = "ss",
                    address = address,
                    port = port,
                    rawConfig = uri
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSSR(uri: String): ProxyNode? {
        return try {
            val b64 = uri.removePrefix("ssr://")
            val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            // host:port:protocol:method:obfs:base64pass/?params
            val mainPart = decoded.substringBefore("/?")
            val parts = mainPart.split(":")
            if (parts.size < 6) return null
            val address = parts[0]
            val port = parts[1].toIntOrNull() ?: 0
            val name = run {
                val params = decoded.substringAfter("/?", "")
                val remarksParam = params.split("&").find { it.startsWith("remarks=") }
                if (remarksParam != null) {
                    String(Base64.decode(remarksParam.substring(8), Base64.DEFAULT), Charsets.UTF_8)
                } else {
                    "Unknown"
                }
            }
            ProxyNode(
                id = "ssr_${address}_${port}_${name.hashCode()}",
                name = name,
                protocol = "ssr",
                address = address,
                port = port,
                rawConfig = decoded
            )
        } catch (e: Exception) {
            null
        }
    }
}
