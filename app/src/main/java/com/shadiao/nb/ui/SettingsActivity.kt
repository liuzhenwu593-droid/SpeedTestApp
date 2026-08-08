package com.shadiao.nb.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.shadiao.nb.R
import com.shadiao.nb.config.AppConfig
import com.shadiao.nb.util.PrefsHelper
import com.shadiao.nb.util.UpdateChecker
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initToolbar()
        initProxyMode()
        initTheme()
        initAppProxy()
        initSubscription()
        initUpdate()
    }

    private fun initToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initProxyMode() {
        val rgMode = findViewById<RadioGroup>(R.id.rg_mode)
        when (PrefsHelper.getProxyMode()) {
            PrefsHelper.MODE_GLOBAL -> rgMode.check(R.id.rb_global)
            PrefsHelper.MODE_RULE -> rgMode.check(R.id.rb_rule)
        }
        rgMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_rule -> PrefsHelper.setProxyMode(PrefsHelper.MODE_RULE)
                R.id.rb_global -> PrefsHelper.setProxyMode(PrefsHelper.MODE_GLOBAL)
            }
        }
    }

    private fun initTheme() {
        val rgTheme = findViewById<RadioGroup>(R.id.rg_theme)
        when (PrefsHelper.getThemeMode()) {
            PrefsHelper.THEME_SYSTEM -> rgTheme.check(R.id.rb_theme_system)
            PrefsHelper.THEME_LIGHT -> rgTheme.check(R.id.rb_theme_light)
            PrefsHelper.THEME_DARK -> rgTheme.check(R.id.rb_theme_dark)
        }
        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_theme_system -> PrefsHelper.THEME_SYSTEM
                R.id.rb_theme_light -> PrefsHelper.THEME_LIGHT
                R.id.rb_theme_dark -> PrefsHelper.THEME_DARK
                else -> PrefsHelper.THEME_SYSTEM
            }
            PrefsHelper.setThemeMode(mode)
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    PrefsHelper.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    PrefsHelper.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }

    private fun initAppProxy() {
        val switch = findViewById<SwitchMaterial>(R.id.switch_app_proxy)
        val btnConfig = findViewById<MaterialButton>(R.id.btn_app_proxy_config)

        switch.isChecked = PrefsHelper.isAppProxyEnabled()
        switch.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setAppProxyEnabled(isChecked)
            if (isChecked) {
                Toast.makeText(this, R.string.app_proxy_hint, Toast.LENGTH_LONG).show()
            }
        }

        btnConfig.setOnClickListener {
            startActivity(Intent(this, AppProxyActivity::class.java))
        }
    }

    private fun initSubscription() {
        val etUrl = findViewById<TextInputEditText>(R.id.et_subscription_url)
        val switchAutoUpdate = findViewById<SwitchMaterial>(R.id.switch_auto_update)
        val switchAutoSpeedTest = findViewById<SwitchMaterial>(R.id.switch_auto_speedtest)

        etUrl.setText(PrefsHelper.getSubscriptionUrl())
        switchAutoUpdate.isChecked = true // 默认开启自动更新
        switchAutoSpeedTest.isChecked = PrefsHelper.isAutoSpeedTestEnabled()

        etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                PrefsHelper.setSubscriptionUrl(etUrl.text.toString())
            }
        }

        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                scheduleAutoUpdate()
            } else {
                cancelAutoUpdate()
            }
        }

        switchAutoSpeedTest.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setAutoSpeedTest(isChecked)
        }
    }

    private fun scheduleAutoUpdate() {
        val request = androidx.work.PeriodicWorkRequestBuilder<com.shadiao.nb.util.SubscriptionUpdateWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        ).setInitialDelay(
            com.shadiao.nb.util.SubscriptionUpdateWorker.calculateDelay(),
            java.util.concurrent.TimeUnit.MILLISECONDS
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            com.shadiao.nb.util.SubscriptionUpdateWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Toast.makeText(this, "已开启自动更新", Toast.LENGTH_SHORT).show()
    }

    private fun cancelAutoUpdate() {
        androidx.work.WorkManager.getInstance(this)
            .cancelUniqueWork(com.shadiao.nb.util.SubscriptionUpdateWorker.WORK_NAME)
        Toast.makeText(this, "已关闭自动更新", Toast.LENGTH_SHORT).show()
    }

    private fun initUpdate() {
        findViewById<MaterialButton>(R.id.btn_check_update).setOnClickListener {
            checkUpdate()
        }

        findViewById<MaterialButton>(R.id.btn_about).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun checkUpdate() {
        Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val checker = UpdateChecker()
            val result = checker.checkUpdate(AppConfig.UPDATE_JSON_URL)
            if (result.isSuccess) {
                val info = result.getOrNull()!!
                val currentVersion = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
                if (info.versionCode > currentVersion) {
                    Toast.makeText(this@SettingsActivity, "发现新版本: ${info.versionName}", Toast.LENGTH_LONG).show()
                    // 打开下载链接
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, "下载链接: ${info.downloadUrl}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@SettingsActivity, R.string.update_latest, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@SettingsActivity, R.string.update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
