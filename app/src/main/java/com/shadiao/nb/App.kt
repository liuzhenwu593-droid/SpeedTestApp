package com.shadiao.nb

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shadiao.nb.util.PrefsHelper
import com.shadiao.nb.util.SubscriptionUpdateWorker
import java.util.concurrent.TimeUnit

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化偏好设置
        PrefsHelper.init(this)

        // 应用主题（首次跟随系统）
        applyTheme()

        // 创建通知渠道
        createNotificationChannels()

        // 调度订阅自动更新（24小时）
        scheduleSubscriptionUpdate()
    }

    private fun applyTheme() {
        val mode = PrefsHelper.getThemeMode()
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                PrefsHelper.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                PrefsHelper.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_VPN,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun scheduleSubscriptionUpdate() {
        val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
            24, TimeUnit.HOURS
        ).setInitialDelay(
            SubscriptionUpdateWorker.calculateDelay(),
            TimeUnit.MILLISECONDS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SubscriptionUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        const val CHANNEL_VPN = "vpn_status"

        lateinit var instance: App
            private set
    }
}
