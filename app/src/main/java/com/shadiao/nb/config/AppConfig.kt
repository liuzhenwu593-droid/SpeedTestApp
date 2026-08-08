package com.shadiao.nb.config

object AppConfig {
    /** 默认订阅链接 */
    const val DEFAULT_SUBSCRIPTION_URL =
        "https://meitu.ccwu.cc/sub?token=27882ec74d1d608cbc6d0f6756bc174f"

    /** 远程更新 JSON 地址 */
    const val UPDATE_JSON_URL =
        "https://yunpan.hynb.ccwu.cc/raw/update.json"

    /** TG 群链接 */
    const val TG_GROUP_URL = "https://t.me/shadiaovpn"

    /** 官网链接 */
    const val WEBSITE_URL = "https://shadiao.hynb.ccwu.cc"

    /** 订阅自动更新间隔（24小时） */
    const val SUBSCRIPTION_UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** 测速超时时间（毫秒） */
    const val SPEED_TEST_TIMEOUT_MS = 5000

    /** 测速并发数 */
    const val SPEED_TEST_CONCURRENCY = 5

    /** libv2ray 本地 SOCKS5 端口 */
    const val LOCAL_SOCKS_PORT = 10808

    /** libv2ray 本地 HTTP 端口 */
    const val LOCAL_HTTP_PORT = 10809

    /** VPN 虚拟网卡地址 */
    const val VPN_ADDRESS = "172.19.0.1"
    const val VPN_ROUTE = "0.0.0.0"
}
