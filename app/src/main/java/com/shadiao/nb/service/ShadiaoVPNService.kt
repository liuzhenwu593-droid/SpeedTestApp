package com.shadiao.nb.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.shadiao.nb.MainActivity
import com.shadiao.nb.data.NodeRepository
import com.shadiao.nb.util.ConfigGenerator
import com.shadiao.nb.util.GeoDataManager
import com.shadiao.nb.util.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

class ShadiaoVPNService : VpnService() {

    companion object {
        private const val TAG = "ShadiaoVPNService"
        const val CHANNEL_ID = "shadiao_vpn"
        const val NOTIFICATION_ID = 1
        const val VPN_ADDRESS = "10.0.0.2"
        const val VPN_MTU = 1500

        val vpnState = MutableStateFlow(VPNState.DISCONNECTED)
        val currentNode = MutableStateFlow<String?>(null)
        val currentRxSpeed = MutableStateFlow("0 B/s")
        val currentTxSpeed = MutableStateFlow("0 B/s")
        /** 最近一次连接错误信息，供 UI 展示（null = 无错误） */
        val lastError = MutableStateFlow<String?>(null)
    }

    enum class VPNState { CONNECTED, DISCONNECTED, CONNECTING, DISCONNECTING }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var speedMonitorJob: Job? = null
    private var coreController: CoreController? = null

    // 流量统计
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastSpeedCheck = 0L

    /**
     * AndroidLibXrayLite 回调处理器。
     * CoreController 在启动/停止/状态变更时回调这些方法，返回值未使用（固定返回 0）。
     */
    private val callbackHandler = object : CoreCallbackHandler {
        override fun startup(): Long {
            Log.i(TAG, "Xray core 启动回调")
            return 0
        }
        override fun shutdown(): Long {
            Log.i(TAG, "Xray core 停止回调")
            return 0
        }
        override fun onEmitStatus(status: Long, message: String): Long {
            Log.i(TAG, "Xray 状态: $status - $message")
            return 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "沙雕VPN", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        when (intent.action) {
            "CONNECT" -> {
                val nodeId = intent.getStringExtra("node_id") ?: return START_NOT_STICKY
                vpnState.value = VPNState.CONNECTING
                startForeground(NOTIFICATION_ID, buildNotification(nodeId, null, null))
                scope.launch { startVpn(nodeId) }
            }
            "DISCONNECT" -> {
                vpnState.value = VPNState.DISCONNECTING
                scope.launch { stopVpn() }
            }
        }
        return START_STICKY
    }

    private suspend fun startVpn(nodeId: String) = withContext(Dispatchers.IO) {
        try {
            lastError.value = null

            // 0. 读取当前设置快照
            val settings = SettingsManager.snapshot()

            // 1. 建立 VPN 接口
            //    VPN 自身始终被排除（applyPerAppFilter），Xray 出站 socket 自动绕过隧道，
            //    无需旧库的 registerProtectHandler。
            val builder = Builder()
                .setSession("沙雕VPN")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("223.5.5.5")
                .addDnsServer("114.114.114.114")
                .setMtu(VPN_MTU)

            applyPerAppFilter(builder, settings)
            vpnInterface = builder.establish() ?: throw Exception("VPN 建立失败")

            // 2. 获取节点
            val node = NodeRepository.getCachedNodes().find { it.id == nodeId }
                ?: throw Exception("节点未找到")

            // 3. 确保 geoip.dat / geosite.dat 存在
            //    AndroidLibXrayLite 的 AAR 已内置 dat 文件到 assets，
            //    InitCoreEnv 设置的自定义文件读取器会自动回退到 assets 查找。
            GeoDataManager.ensureDatFiles(this@ShadiaoVPNService)
            val geoAvailable = GeoDataManager.hasDatFiles(this@ShadiaoVPNService)
            if (!geoAvailable) {
                Log.w(TAG, "geoip.dat / geosite.dat 不可用，GEO 模式将降级为字面量规则")
            }

            // 4. 生成 Xray 配置 JSON（AndroidLibXrayLite 直接接收 JSON 字符串，无需写文件）
            val configJson = ConfigGenerator.generateXrayConfig(node, settings.routingMode, geoAvailable)
            Log.i(TAG, "Xray 配置:\n$configJson")

            // 5. 创建 CoreController 并启动 Xray
            //    startLoop 内部完成：设置 TUN fd 环境变量 → 加载配置 → 创建 core 实例 → 启动
            //    失败时抛出 Exception（gomobile 将 Go error 映射为 Java Exception）
            coreController = Libv2ray.newCoreController(callbackHandler)

            val tunFd = vpnInterface!!.fd
            Log.i(TAG, "启动 Xray: tunFd=$tunFd, node=${node.address}:${node.port}")
            coreController!!.startLoop(configJson, tunFd)

            if (coreController!!.isRunning != true) {
                throw Exception("Xray 启动失败：isRunning = false")
            }

            Log.i(TAG, "Xray 启动成功: ${Libv2ray.checkVersionX()}")

            currentNode.value = nodeId
            vpnState.value = VPNState.CONNECTED

            // 6. 启动实时网速监控
            startSpeedMonitor(nodeId)

        } catch (e: Exception) {
            Log.e(TAG, "VPN 启动异常: ${e.message}", e)
            lastError.value = e.message ?: "未知错误"
            stopVpn()
        }
    }

    /**
     * 分应用代理过滤（白名单模式）。
     *
     * - 有选中应用：仅选中应用的流量进入 VPN；本应用自身不在白名单内，自动排除避免回环。
     * - 无选中应用：全部应用进入 VPN，排除自身。
     *
     * VPN 自身被排除 = Xray 出站 socket 不会进入 TUN 隧道 = 无路由循环。
     * 这替代了旧库（thebytearray/libxray-android）的 registerProtectHandler 机制。
     */
    private fun applyPerAppFilter(builder: Builder, settings: SettingsManager.SettingsSnapshot) {
        if (settings.perAppPackages.isNotEmpty()) {
            // 白名单模式：仅选中应用走 VPN
            settings.perAppPackages.forEach { pkg ->
                if (pkg == packageName) return@forEach // 跳过自身
                runCatching { builder.addAllowedApplication(pkg) }
            }
        } else {
            // 无选中应用：排除自身即可
            runCatching { builder.addDisallowedApplication(packageName) }
        }
    }

    private suspend fun stopVpn() = withContext(Dispatchers.IO) {
        // 停止网速监控
        speedMonitorJob?.cancel()
        speedMonitorJob = null
        currentRxSpeed.value = "0 B/s"
        currentTxSpeed.value = "0 B/s"

        // 停止 Xray core（stopLoop 内部有互斥锁，线程安全）
        try { coreController?.stopLoop() } catch (_: Exception) {}
        coreController = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        currentNode.value = null
        lastError.value = null
        vpnState.value = VPNState.DISCONNECTED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ────────────── 实时网速监控 ──────────────
    private fun startSpeedMonitor(nodeId: String) {
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastSpeedCheck = System.currentTimeMillis()

        speedMonitorJob = scope.launch {
            while (isActive) {
                delay(1000)
                updateSpeedNotification(nodeId)
            }
        }
    }

    private fun updateSpeedNotification(nodeId: String) {
        try {
            val now = System.currentTimeMillis()
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()
            val elapsed = (now - lastSpeedCheck) / 1000f

            if (elapsed > 0) {
                val rxSpeed = ((currentRx - lastRxBytes) / elapsed).toLong()
                val txSpeed = ((currentTx - lastTxBytes) / elapsed).toLong()

                lastRxBytes = currentRx
                lastTxBytes = currentTx
                lastSpeedCheck = now

                val rxStr = formatSpeed(rxSpeed)
                val txStr = formatSpeed(txSpeed)

                currentRxSpeed.value = rxStr
                currentTxSpeed.value = txStr

                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(nodeId, rxStr, txStr))
            }
        } catch (_: Exception) { }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 0 -> "0 B/s"
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024f)
            bytesPerSec < 1024 * 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024f * 1024f))
            else -> String.format("%.1f GB/s", bytesPerSec / (1024f * 1024f * 1024f))
        }
    }

    // ────────────── 通知栏 ──────────────
    private fun buildNotification(
        nodeId: String,
        rxSpeed: String?,
        txSpeed: String?
    ): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speedText = if (rxSpeed != null && txSpeed != null) {
            "↑ $txSpeed   ↓ $rxSpeed"
        } else {
            "正在连接…"
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("沙雕VPN")
            .setContentText(speedText)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        scope.launch { stopVpn() }
        scope.cancel()
        super.onDestroy()
    }
}
