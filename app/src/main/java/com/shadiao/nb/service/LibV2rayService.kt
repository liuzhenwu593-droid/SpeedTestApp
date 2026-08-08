package com.shadiao.nb.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * libv2ray 后台服务占位
 * 实际核心逻辑在 VpnService 中通过 LibV2rayCore 调用
 */
class LibV2rayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}
