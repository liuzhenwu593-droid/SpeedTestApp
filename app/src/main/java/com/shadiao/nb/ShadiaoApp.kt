package com.shadiao.nb

import android.app.Application
import android.util.Log
import com.shadiao.nb.data.NodeRepository
import com.shadiao.nb.util.GeoDataManager
import com.shadiao.nb.util.SettingsManager
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

class ShadiaoApp : Application() {

    companion object {
        private const val TAG = "ShadiaoApp"
        private val coreEnvInitialized = AtomicBoolean(false)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NodeRepository.initialize(this)
        SettingsManager.initialize(this)

        // 初始化 AndroidLibXrayLite 运行环境（仅一次）
        // Seq.setContext 设置 gomobile JNI 上下文，InitCoreEnv 设置 asset 路径和文件读取器
        initXrayCoreEnv()

        // 后台：首次预选代理应用 + 下载 geoip/geosite（均为 IO 操作，不可阻塞主线程）
        appScope.launch {
            SettingsManager.autoSelectDefaultApps(this@ShadiaoApp)
            GeoDataManager.ensureDatFiles(this@ShadiaoApp)
        }
    }

    /**
     * 初始化 AndroidLibXrayLite 核心环境。
     *
     * - Seq.setContext: 注册 gomobile JNI 上下文，Go 侧才能回调 Java
     * - Libv2ray.initCoreEnv: 设置 xray.location.asset / xray.location.cert 环境变量，
     *   并安装自定义文件读取器（文件系统找不到时自动回退到 Android assets，
     *   AAR 已内置 geoip.dat / geosite.dat）
     *
     * 线程安全：AtomicBoolean 确保只执行一次。
     */
    private fun initXrayCoreEnv() {
        if (coreEnvInitialized.compareAndSet(false, true)) {
            try {
                Seq.setContext(applicationContext)
                Libv2ray.initCoreEnv(filesDir.absolutePath, "")
                Log.i(TAG, "AndroidLibXrayLite 环境初始化成功: ${Libv2ray.checkVersionX()}")
            } catch (e: Exception) {
                Log.e(TAG, "AndroidLibXrayLite 环境初始化失败", e)
                coreEnvInitialized.set(false)
            }
        }
    }
}
