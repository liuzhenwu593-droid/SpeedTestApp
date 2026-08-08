package com.shadiao.nb.util

import com.shadiao.nb.config.AppConfig
import com.shadiao.nb.data.ProxyNode
import com.shadiao.nb.util.PrefsHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * V2Ray/libv2ray 配置生成器
 * 根据节点生成完整的 V2Ray JSON 配置
 */
object V2rayConfigGenerator {

    /**
     * 生成完整的 V2Ray 配置
     */
    fun generate(node: ProxyNode): String {
        val config = JSONObject()
        config.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        // DNS
        config.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                put("8.8.8.8")
                put("1.1.1.1")
                put("223.5.5.5")
            })
        })

        // Inbounds - SOCKS5 + HTTP
        val inbounds = JSONArray()
        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("port", AppConfig.LOCAL_SOCKS_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
        })
        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("port", AppConfig.LOCAL_HTTP_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject())
        })
        config.put("inbounds", inbounds)

        // Outbounds
        val outbounds = JSONArray()
        outbounds.put(generateOutbound(node))
        // Direct outbound
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject())
        })
        // Block outbound
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject())
        })
        config.put("outbounds", outbounds)

        // Routing
        val routing = JSONObject()
        val rules = JSONArray()

        val proxyMode = PrefsHelper.getProxyMode()
        when (proxyMode) {
            PrefsHelper.MODE_GLOBAL -> {
                // 全局模式：所有流量走代理
                routing.put("domainStrategy", "AsIs")
            }
            PrefsHelper.MODE_RULE -> {
                // 分流模式：国内直连，国外走代理
                routing.put("domainStrategy", "IPIfNonMatch")
                // 局域网直连
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply {
                        put("geoip:private")
                    })
                })
                // 国内域名直连
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("domain", JSONArray().apply {
                        put("geosite:cn")
                    })
                })
                // 国内 IP 直连
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply {
                        put("geoip:cn")
                    })
                })
                // 广告拦截
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "block")
                    put("domain", JSONArray().apply {
                        put("geosite:category-ads-all")
                    })
                })
            }
        }

        routing.put("rules", rules)
        config.put("routing", routing)

        return config.toString()
    }

    private fun generateOutbound(node: ProxyNode): JSONObject {
        return when (node.protocol) {
            "vmess" -> generateVmessOutbound(node)
            "vless" -> generateVlessOutbound(node)
            "trojan" -> generateTrojanOutbound(node)
            "ss" -> generateSSOutbound(node)
            else -> generateVmessOutbound(node) // fallback
        }
    }

    private fun generateVmessOutbound(node: ProxyNode): JSONObject {
        val rawConfig = try {
            JSONObject(node.rawConfig)
        } catch (e: Exception) {
            JSONObject()
        }

        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vmess")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", node.address)
                        put("port", node.port)
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", rawConfig.optString("id", ""))
                                put("alterId", rawConfig.optInt("aid", 0))
                                put("security", rawConfig.optString("scy", "auto"))
                            })
                        })
                    })
                })
            })
            put("streamSettings", generateStreamSettings(rawConfig))
        }
    }

    private fun generateVlessOutbound(node: ProxyNode): JSONObject {
        // 解析 vless URI
        val noScheme = node.rawConfig.removePrefix("vless://")
        val fragIndex = noScheme.lastIndexOf("#")
        val main = if (fragIndex >= 0) noScheme.substring(0, fragIndex) else noScheme
        val atIndex = main.indexOf("@")
        val uuid = if (atIndex >= 0) main.substring(0, atIndex) else ""
        val queryStr = main.substringAfter("?", "")

        val params = parseQuery(queryStr)

        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", node.address)
                        put("port", node.port)
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", uuid)
                                put("encryption", "none")
                                params["flow"]?.let { put("flow", it) }
                            })
                        })
                    })
                })
            })
            put("streamSettings", generateStreamSettingsFromParams(params))
        }
    }

    private fun generateTrojanOutbound(node: ProxyNode): JSONObject {
        val noScheme = node.rawConfig.removePrefix("trojan://")
        val fragIndex = noScheme.lastIndexOf("#")
        val main = if (fragIndex >= 0) noScheme.substring(0, fragIndex) else noScheme
        val atIndex = main.indexOf("@")
        val password = if (atIndex >= 0) main.substring(0, atIndex) else ""
        val queryStr = main.substringAfter("?", "")

        val params = parseQuery(queryStr)

        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "trojan")
            put("settings", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", node.address)
                        put("port", node.port)
                        put("password", password)
                    })
                })
            })
            put("streamSettings", generateStreamSettingsFromParams(params))
        }
    }

    private fun generateSSOutbound(node: ProxyNode): JSONObject {
        // 解析 ss URI
        val noScheme = node.rawConfig.removePrefix("ss://")
        val fragIndex = noScheme.lastIndexOf("#")
        val main = if (fragIndex >= 0) noScheme.substring(0, fragIndex) else noScheme

        var method = ""
        var password = ""
        val atIndex = main.indexOf("@")
        if (atIndex >= 0) {
            val userInfo = main.substring(0, atIndex)
            try {
                val decoded = String(android.util.Base64.decode(userInfo, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val colonIdx = decoded.indexOf(":")
                method = decoded.substring(0, colonIdx)
                password = decoded.substring(colonIdx + 1)
            } catch (e: Exception) {
                // 非base64
                val colonIdx = userInfo.indexOf(":")
                method = userInfo.substring(0, colonIdx)
                password = userInfo.substring(colonIdx + 1)
            }
        } else {
            val decoded = String(android.util.Base64.decode(main, android.util.Base64.DEFAULT), Charsets.UTF_8)
            val atIdx2 = decoded.lastIndexOf("@")
            val colonIdx = decoded.indexOf(":")
            method = decoded.substring(0, colonIdx)
            password = decoded.substring(colonIdx + 1, atIdx2)
        }

        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "shadowsocks")
            put("settings", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", node.address)
                        put("port", node.port)
                        put("method", method)
                        put("password", password)
                    })
                })
            })
        }
    }

    private fun generateStreamSettings(config: JSONObject): JSONObject {
        val network = config.optString("net", "tcp")
        val tls = config.optString("tls", "")
        val sni = config.optString("sni", config.optString("host", ""))
        val host = config.optString("host", "")
        val path = config.optString("path", "/")

        return buildStreamSettings(network, tls, sni, host, path)
    }

    private fun generateStreamSettingsFromParams(params: Map<String, String>): JSONObject {
        val network = params["type"] ?: "tcp"
        val tls = params["security"] ?: ""
        val sni = params["sni"] ?: params["host"] ?: ""
        val host = params["host"] ?: ""
        val path = params["path"] ?: "/"

        return buildStreamSettings(network, tls, sni, host, path)
    }

    private fun buildStreamSettings(network: String, tls: String, sni: String, host: String, path: String): JSONObject {
        return JSONObject().apply {
            put("network", network)
            when (network) {
                "ws" -> {
                    put("wsSettings", JSONObject().apply {
                        put("path", path)
                        put("headers", JSONObject().apply {
                            if (host.isNotEmpty()) put("Host", host)
                        })
                    })
                }
                "grpc" -> {
                    put("grpcSettings", JSONObject().apply {
                        put("serviceName", path)
                    })
                }
                "h2" -> {
                    put("httpSettings", JSONObject().apply {
                        put("path", path)
                        if (host.isNotEmpty()) put("host", JSONArray().apply { put(host) })
                    })
                }
            }
            if (tls == "tls" || tls == "xtls") {
                put("security", tls)
                put("tlsSettings", JSONObject().apply {
                    put("allowInsecure", true)
                    if (sni.isNotEmpty()) put("serverName", sni)
                })
            }
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (query.isBlank()) return params
        for (pair in query.split("&")) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                params[key] = value
            }
        }
        return params
    }
}
