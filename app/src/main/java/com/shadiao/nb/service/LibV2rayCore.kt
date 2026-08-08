package com.shadiao.nb.service

import android.util.Log

/**
 * libv2ray 核心包装类
 *
 * 这个类封装了 libv2ray.aar 的 API 调用。
 * 实际使用时需要将 libv2ray.aar 放入 app/libs/ 目录。
 *
 * 如果没有 AAR 文件，构建时会跳过核心功能，但 UI 和其他功能仍可使用。
 * 以下是 libv2ray 标准 API 的调用封装。
 */
class LibV2rayCore {

    private var running = false

    /**
     * 启动 V2Ray 核心
     * @param configJson V2Ray JSON 配置
     */
    fun start(configJson: String) {
        try {
            // libv2ray API: Libv2rayTestKit 或 V2RayCore
            // 使用反射调用，避免编译时依赖缺失
            try {
                val testKitClass = Class.forName("com.v2ray.ang.util.Libv2rayHelper")
                val startMethod = testKitClass.getMethod("startLoop", String::class.java)
                startMethod.invoke(null, configJson)
                running = true
                Log.i(TAG, "libv2ray 核心已启动")
                return
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "未找到 Libv2rayHelper，尝试备用接口")
            }

            // 备用：直接调用 libv2ray.go 包
            try {
                val goClass = Class.forName("libv2ray.Libv2ray")
                val runMethod = goClass.getMethod("runLoop", String::class.java)
                runMethod.invoke(null, configJson)
                running = true
                Log.i(TAG, "libv2ray (go) 核心已启动")
                return
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "未找到 libv2ray.Libv2ray")
            }

            // 没有核心库，仅标记为运行（调试模式）
            Log.w(TAG, "libv2ray.aar 未安装，VPN 核心不可用")
            running = true
        } catch (e: Exception) {
            Log.e(TAG, "启动 libv2ray 失败", e)
            throw e
        }
    }

    /**
     * 设置 VPN 文件描述符
     */
    fun setVpnFd(fd: Int) {
        try {
            try {
                val testKitClass = Class.forName("com.v2ray.ang.util.Libv2rayHelper")
                val method = testKitClass.getMethod("setVpnFd", Int::class.javaPrimitiveType)
                method.invoke(null, fd)
            } catch (e: ClassNotFoundException) {
                // 尝试备用
                val goClass = Class.forName("libv2ray.Libv2ray")
                val method = goClass.getMethod("setVpnFd", Int::class.javaPrimitiveType)
                method.invoke(null, fd)
            }
        } catch (e: Exception) {
            Log.w(TAG, "设置 VPN FD 失败（可能无核心库）: ${e.message}")
        }
    }

    /**
     * 停止 V2Ray 核心
     */
    fun stop() {
        try {
            try {
                val testKitClass = Class.forName("com.v2ray.ang.util.Libv2rayHelper")
                val method = testKitClass.getMethod("stopLoop")
                method.invoke(null)
            } catch (e: ClassNotFoundException) {
                val goClass = Class.forName("libv2ray.Libv2ray")
                val method = goClass.getMethod("stopLoop")
                method.invoke(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "停止 libv2ray 失败（可能无核心库）: ${e.message}")
        }
        running = false
        Log.i(TAG, "libv2ray 核心已停止")
    }

    fun isRunning(): Boolean = running

    companion object {
        private const val TAG = "LibV2rayCore"
    }
}
