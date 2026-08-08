package com.shadiao.nb.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.shadiao.nb.R
import com.shadiao.nb.config.AppConfig

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 版本信息
        val versionText = findViewById<TextView>(R.id.tv_version)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo)
            versionText.text = "${getString(R.string.version)} ${packageInfo.versionName} ($versionCode)"
        } catch (e: Exception) {
            versionText.text = "${getString(R.string.version)} 1.0.0"
        }

        findViewById<View>(R.id.btn_join_tg).setOnClickListener {
            openUrl(AppConfig.TG_GROUP_URL)
        }

        findViewById<View>(R.id.btn_website).setOnClickListener {
            openUrl(AppConfig.WEBSITE_URL)
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }
}
