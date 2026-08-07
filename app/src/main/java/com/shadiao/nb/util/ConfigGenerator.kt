package com.shadiao.nb.util

import com.shadiao.nb.data.ProxyNode
import org.json.JSONArray
import org.json.JSONObject

/**
 * 生成 Xray 配置。
 *
 * 分应用过滤在 VpnService.Builder 上用 addAllowedApplication 白名单实现（见 ShadiaoVPNService），
 * Xray 只负责处理进入 TUN 的流量。本类依据 [RoutingMode] + [geoAvailable] 决定分流策略：
 *
 * - [RoutingMode.GLOBAL] 全部转发到 proxy 出站。
 * - [RoutingMode.GEO] + geoAvailable=true：依据 geosite/geoip 分流（广告拦截、国内直连、其余代理）。
 * - [RoutingMode.GEO] + geoAvailable=false：dat 缺失降级，字面量 CIDR 私有地址直连，其余代理。
 *
 * ════════════════════════════════════════════════════════════════════════
 *  根因分析：为什么之前 GEO 模式没网络
 * ════════════════════════════════════════════════════════════════════════
 *  旧代码 GEO 模式用 domainStrategy=IPIfNonMatch + 复杂 DNS 域名分流：
 *    dns.servers = [{address:"223.5.5.5", domains:["geosite:cn","domain:cn"]}, "1.1.1.1"]
 *
 *  当用户访问境外域名（如 google.com）时：
 *    1. 路由匹配：google.com 不命中 geosite:cn
 *    2. IPIfNonMatch 触发：调用内置 DNS 解析 google.com → IP，再做第二轮 geoip 匹配
 *    3. 内置 DNS：google.com 不匹配 geosite:cn → 用 1.1.1.1 解析
 *    4. DNS 查询到 1.1.1.1 → 路由 → 不命中 geoip:cn → proxy 出站
 *    5. proxy 通过 VLESS+WS+TLS 隧道发送 UDP DNS 查询
 *
 *  问题在第 5 步：VLESS+WS+TLS 传输层对 UDP DNS 查询的兼容性不确定，
 *  内置 DNS 子系统在域名分流 + geosite 匹配下可能解析失败 → 境外域名全部解析失败 → 全局断流。
 *
 *  GLOBAL 模式不受影响，因为 domainStrategy=AsIs 不触发内置 DNS，
 *  所有域名直接交给 proxy 出站，由远端服务器解析。
 *
 * ════════════════════════════════════════════════════════════════════════
 *  修复方案：GEO 模式也用 AsIs + 简单 DNS
 * ════════════════════════════════════════════════════════════════════════
 *  domainStrategy=AsIs：路由引擎用嗅探到的域名直接匹配 geosite 规则，
 *  不触发内置 DNS 解析。与 GLOBAL 模式走相同的代码路径，只多了分流规则。
 *
 *  流量走向：
 *    · 境外域名（google.com）→ 不命中 geosite:cn → 兜底 proxy → 远端解析 ✓
 *    · 国内域名（baidu.com）  → 命中 geosite:cn → direct → Go 解析器(223.5.5.5)解析 ✓
 *    · DNS 到 223.5.5.5      → IP 命中 geoip:cn → direct ✓
 *    · DNS 到 1.1.1.1        → IP 不命中 → proxy ✓（但 AsIs 下内置 DNS 不主动使用）
 *    · 广告域名               → 命中 geosite:category-ads-all → block ✓
 *
 *  DNS 配置用简单字符串（与 GLOBAL 一致），不使用 domains 字段做域名分流，
 *  避免内置 DNS 子系统的兼容性问题。
 */
object ConfigGenerator {

    fun generateXrayConfig(node: ProxyNode, routingMode: RoutingMode, geoAvailable: Boolean): String {
        val config = JSONObject()

        // 日志
        config.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        // 入站 — TUN
        config.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "tun-in")
                put("protocol", "tun")
                put("settings", JSONObject().apply {
                    put("name", "xray0")
                    put("MTU", 1500)
                    put("userLevel", 8)
                })
                // 嗅探：从 TLS SNI / HTTP Host / QUIC 还原真实域名，供 geosite 域名规则匹配
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().apply {
                        put("http")
                        put("tls")
                        put("quic")
                    })
                })
            })
        })

        // 出站：proxy 在前 = 默认出站；direct 次之；block 备用
        config.put("outbounds", JSONArray().apply {
            put(buildProxyOutbound(node))
            put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put("settings", JSONObject())
            })
            put(JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
                put("settings", JSONObject())
            })
        })

        config.put("routing", buildRouting(routingMode, geoAvailable))
        config.put("dns", buildDns(routingMode, geoAvailable))

        return config.toString(2)
    }

    /**
     * 路由规则。
     *
     * 所有模式统一使用 domainStrategy=AsIs：
     * 路由引擎用嗅探到的域名直接匹配 geosite，用连接 IP 匹配 geoip。
     * 不触发内置 DNS 解析（避免 IPIfNonMatch 导致的境外域名解析失败）。
     *
     * GEO + geoAvailable：
     *  1. geosite:category-ads-all  → block    广告拦截
     *  2. geosite:cn / geosite:private → direct  国内 / 私有域名直连
     *  3. geoip:private / geoip:cn  → direct  私有 IP / 国内 IP 直连（含 DNS 查询自然分流）
     *  4. 其余 → proxy（兜底）
     *
     * GEO + !geoAvailable（dat 缺失降级）：
     *  1. 私有 IP 段（字面量 CIDR）→ direct
     *  2. 其余 → proxy
     *
     * GLOBAL：全部 → proxy
     */
    private fun buildRouting(mode: RoutingMode, geoAvailable: Boolean): JSONObject = JSONObject().apply {
        // 统一用 AsIs：不触发内置 DNS 解析，避免境外域名解析失败
        put("domainStrategy", "AsIs")
        put("rules", JSONArray().apply {
            when (mode) {
                RoutingMode.GLOBAL -> {
                    // 全局代理：所有流量走 proxy
                    put(JSONObject().apply {
                        put("type", "field")
                        put("network", "tcp,udp")
                        put("outboundTag", "proxy")
                    })
                }
                RoutingMode.GEO -> {
                    if (geoAvailable) {
                        // 1. 广告拦截
                        put(JSONObject().apply {
                            put("type", "field")
                            put("outboundTag", "block")
                            put("domain", JSONArray().apply {
                                put("geosite:category-ads-all")
                            })
                        })
                        // 2. 国内 / 私有域名直连
                        put(JSONObject().apply {
                            put("type", "field")
                            put("outboundTag", "direct")
                            put("domain", JSONArray().apply {
                                put("geosite:private")
                                put("geosite:cn")
                            })
                        })
                        // 3. 私有 IP + 国内 IP 直连
                        //    DNS 查询也走这里：223.5.5.5 命中 geoip:cn → direct
                        put(JSONObject().apply {
                            put("type", "field")
                            put("outboundTag", "direct")
                            put("ip", JSONArray().apply {
                                put("geoip:private")
                                put("geoip:cn")
                            })
                        })
                    } else {
                        // dat 缺失：字面量 CIDR 私有地址直连
                        put(JSONObject().apply {
                            put("type", "field")
                            put("outboundTag", "direct")
                            put("ip", JSONArray().apply {
                                put("10.0.0.0/8")
                                put("172.16.0.0/12")
                                put("192.168.0.0/16")
                                put("127.0.0.0/8")
                                put("100.64.0.0/10")
                                put("fc00::/7")
                                put("fe80::/10")
                            })
                        })
                    }
                    // 兜底：其余全部走代理
                    put(JSONObject().apply {
                        put("type", "field")
                        put("network", "tcp,udp")
                        put("outboundTag", "proxy")
                    })
                }
            }
        })
    }

    /**
     * DNS 配置。
     *
     * 所有模式统一用简单字符串服务器，不用 domains 字段做域名分流：
     *  - 223.5.5.5（阿里 DNS）
     *  - 1.1.1.1（Cloudflare DNS）
     *
     * 不使用复杂 DNS 域名分流的原因：
     *  domains 字段 + geosite 匹配的域名分流在某些 Xray-core 版本上兼容性不确定，
     *  简单字符串服务器最稳定，且 AsIs 模式下内置 DNS 不主动参与路由解析。
     *
     * DNS 查询走向（由 geoip 路由规则决定）：
     *  - 到 223.5.5.5 → 命中 geoip:cn → direct（国内可达）
     *  - 到 1.1.1.1   → 不命中 → proxy（经代理出境）
     */
    private fun buildDns(mode: RoutingMode, geoAvailable: Boolean): JSONObject = JSONObject().apply {
        put("servers", JSONArray().apply {
            put("223.5.5.5")
            put("1.1.1.1")
        })
    }

    private fun buildProxyOutbound(node: ProxyNode): JSONObject = JSONObject().apply {
        put("tag", "proxy")
        put("protocol", "vless")
        put("settings", JSONObject().apply {
            put("vnext", JSONArray().apply {
                put(JSONObject().apply {
                    put("address", node.address)
                    put("port", node.port)
                    put("users", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", node.uuid)
                            put("encryption", "none")
                            put("flow", "")
                        })
                    })
                })
            })
        })
        put("streamSettings", JSONObject().apply {
            put("network", "ws")
            put("security", "tls")
            put("tlsSettings", JSONObject().apply {
                put("serverName", node.sni)
                put("fingerprint", node.fingerprint)
                put("allowInsecure", false)
            })
            put("wsSettings", JSONObject().apply {
                put("path", node.path)
                put("headers", JSONObject().apply {
                    put("Host", node.host)
                })
            })
        })
    }
}
