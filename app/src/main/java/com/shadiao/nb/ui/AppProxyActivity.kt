package com.shadiao.nb.ui

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.shadiao.nb.R
import com.shadiao.nb.util.PrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppProxyActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: AppAdapter
    private val apps = mutableListOf<AppInfo>()
    private val selectedPackages = mutableSetOf<String>()

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_proxy)

        initToolbar()
        initViews()
        loadApps()
    }

    private fun initToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_apps)
        swipeRefresh = findViewById(R.id.swipe_refresh)

        selectedPackages.clear()
        selectedPackages.addAll(PrefsHelper.getAppProxyPackages())

        adapter = AppAdapter(apps, selectedPackages) { pkg, isChecked ->
            if (isChecked) {
                selectedPackages.add(pkg)
            } else {
                selectedPackages.remove(pkg)
            }
            PrefsHelper.setAppProxyPackages(selectedPackages)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.isEnabled = false
    }

    private fun loadApps() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val loadedApps = withContext(Dispatchers.IO) {
                try {
                    val pm = packageManager
                    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    packages
                        .filter { it.packageName != packageName } // 排除自身
                        .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
                        .map { appInfo ->
                            AppInfo(
                                name = pm.getApplicationLabel(appInfo).toString(),
                                packageName = appInfo.packageName,
                                icon = pm.getApplicationIcon(appInfo)
                            )
                        }
                } catch (e: SecurityException) {
                    // 没有查询权限
                    runOnUiThread {
                        findViewById<TextView>(R.id.tv_permission_hint).visibility = View.VISIBLE
                        Toast.makeText(
                            this@AppProxyActivity,
                            "需要「查询已安装应用」权限才能扫描应用列表",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    emptyList()
                }
            }
            apps.clear()
            apps.addAll(loadedApps)
            adapter.notifyDataSetChanged()
            swipeRefresh.isRefreshing = false
        }
    }

    // --- Adapter ---

    private class AppAdapter(
        private val apps: List<AppInfo>,
        private val selectedPackages: Set<String>,
        private val onCheck: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.iv_app_icon)
            val tvName: TextView = view.findViewById(R.id.tv_app_name)
            val tvPackage: TextView = view.findViewById(R.id.tv_package_name)
            val cbSelect: CheckBox = view.findViewById(R.id.cb_select)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.tvName.text = app.name
            holder.tvPackage.text = app.packageName
            if (app.icon != null) {
                holder.ivIcon.setImageDrawable(app.icon)
            }
            holder.cbSelect.setOnCheckedChangeListener(null)
            holder.cbSelect.isChecked = selectedPackages.contains(app.packageName)
            holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                onCheck(app.packageName, isChecked)
            }
            holder.itemView.setOnClickListener {
                holder.cbSelect.isChecked = !holder.cbSelect.isChecked
            }
        }

        override fun getItemCount() = apps.size
    }
}
