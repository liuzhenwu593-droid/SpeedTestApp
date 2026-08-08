package com.shadiao.nb.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shadiao.nb.App
import com.shadiao.nb.R
import com.shadiao.nb.config.AppConfig
import com.shadiao.nb.data.ProxyNode
import com.shadiao.nb.ui.MainActivity
import com.shadiao.nb.util.PrefsHelper
import com.shadiao.nb.util.V2rayConfigGenerator

/**
 * VPN 服务
 * 负责 VPN 隧道的建立、libv2ray 核心管理、通知
 */
class VpnService : android.net.VpnService() {

    private var pfd: ParcelFileDescriptor? = null
    private var libV2rayCore: LibV2rayCore? = null
    private var currentNode: ProxyNode? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_CONNECT
        when (action) {
            ACTION_CONNECT -> {
                val nodeJson = intent.getStringExtra(EXTRA_NODE) ?: return START_NOT_STICKY
                val node = deserializeNode(nodeJson)
                if (node != null) {
                    connect(node)
                }
            }
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    private fun connect(node: ProxyNode) {
        currentNode = node
        startForeground(NOTIFICATION_ID, createNotification(node.name))

        // 启动 libv2ray 核心
        try {
            val config = V2rayConfigGenerator.generate(node)
            libV2rayCore = LibV2rayCore()
            libV2rayCore?.start(config)

            // 建立 VPN 隧道
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(AppConfig.VPN_ADDRESS, 32)
                .addRoute(AppConfig.VPN_ROUTE, 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")

            // 分应用代理
            if (PrefsHelper.isAppProxyEnabled()) {
                val proxyPackages = PrefsHelper.getAppProxyPackages()
                if (proxyPackages.isNotEmpty()) {
                    // 这些应用走 VPN，其余的不走
                    for (pkg in proxyPackages) {
                        builder.addAllowedApplication(pkg)
                    }
                }
            } else {
                // 默认排除自身
                builder.addDisallowedApplication(packageName)
            }

            pfd = builder.establish()
            if (pfd == null) {
                Log.e(TAG, "VPN 隧道建立失败")
                stopSelf()
                return
            }

            // 将 VPN 文件描述符传给 libv2ray
            libV2rayCore?.setVpnFd(pfd!!.detachFd())

            Log.i(TAG, "VPN 已连接: ${node.name}")

            // 发送连接成功广播
            sendBroadcast(Intent(BROADCAST_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, STATE_CONNECTED)
                putExtra(EXTRA_NODE_NAME, node.name)
            })
        } catch (e: Exception) {
            Log.e(TAG, "连接失败", e)
            sendBroadcast(Intent(BROADCAST_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, STATE_ERROR)
                putExtra(EXTRA_ERROR, e.message)
            })
            stopSelf()
        }
    }

    private fun disconnect() {
        try {
            libV2rayCore?.stop()
            libV2rayCore = null
            pfd?.close()
            pfd = null

            sendBroadcast(Intent(BROADCAST_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, STATE_DISCONNECTED)
            })
        } catch (e: Exception) {
            Log.e(TAG, "断开失败", e)
        }
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    override fun onRevoke() {
        disconnect()
    }

    private fun createNotification(nodeName: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, App.CHANNEL_VPN)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text, nodeName))
            .setSmallIcon(R.drawable.ic_power_on)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun deserializeNode(json: String): ProxyNode? {
        return try {
            val obj = org.json.JSONObject(json)
            ProxyNode(
                id = obj.getString("id"),
                name = obj.getString("name"),
                protocol = obj.getString("protocol"),
                address = obj.getString("address"),
                port = obj.getInt("port"),
                rawConfig = obj.getString("rawConfig")
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析节点失败", e)
            null
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.shadiao.nb.CONNECT"
        const val ACTION_DISCONNECT = "com.shadiao.nb.DISCONNECT"
        const val EXTRA_NODE = "extra_node"
        const val BROADCAST_STATE_CHANGED = "com.shadiao.nb.STATE_CHANGED"
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_NODE_NAME = "extra_node_name"
        const val EXTRA_ERROR = "extra_error"
        const val STATE_CONNECTED = 1
        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTING = 2
        const val STATE_ERROR = -1
        private const val NOTIFICATION_ID = 1
        private const val TAG = "VpnService"
    }
}
