package com.shadiao.nb.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService as SystemVpnService
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.shadiao.nb.R
import com.shadiao.nb.config.AppConfig
import com.shadiao.nb.data.ProxyNode
import com.shadiao.nb.service.VpnService
import com.shadiao.nb.util.PrefsHelper
import com.shadiao.nb.util.SpeedTestUtil
import com.shadiao.nb.util.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnConnect: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var tvCurrentNode: TextView
    private lateinit var tvLatency: TextView
    private lateinit var tvMode: TextView

    private lateinit var subscriptionManager: SubscriptionManager
    private var nodes: List<ProxyNode> = emptyList()
    private var currentNode: ProxyNode? = null
    private var isConnecting = false
    private var vpnConnected = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra(VpnService.EXTRA_STATE, -1)) {
                VpnService.STATE_CONNECTED -> {
                    vpnConnected = true
                    isConnecting = false
                    updateUI()
                }
                VpnService.STATE_DISCONNECTED -> {
                    vpnConnected = false
                    isConnecting = false
                    updateUI()
                }
                VpnService.STATE_ERROR -> {
                    vpnConnected = false
                    isConnecting = false
                    val error = intent.getStringExtra(VpnService.EXTRA_ERROR)
                    Toast.makeText(this@MainActivity, "连接失败: $error", Toast.LENGTH_SHORT).show()
                    updateUI()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        subscriptionManager = SubscriptionManager(this)

        initViews()
        setupToolbar()
        setupNavigation()
        setupClickListeners()

        // 首次启动自动导入订阅
        if (PrefsHelper.isFirstLaunch()) {
            PrefsHelper.setFirstLaunchDone()
            autoImportSubscription()
        } else {
            loadCachedNodes()
            // 检查是否需要自动更新订阅
            if (subscriptionManager.needsUpdate()) {
                autoUpdateSubscription()
            }
        }

        updateModeDisplay()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(stateReceiver, IntentFilter(VpnService.BROADCAST_STATE_CHANGED))

        // 打开应用自动测速
        if (PrefsHelper.isAutoSpeedTestEnabled() && nodes.isNotEmpty()) {
            autoSpeedTest()
        }

        updateModeDisplay()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateReceiver)
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        btnConnect = findViewById(R.id.btn_connect)
        tvStatus = findViewById(R.id.tv_status)
        tvCurrentNode = findViewById(R.id.tv_current_node)
        tvLatency = findViewById(R.id.tv_latency)
        tvMode = findViewById(R.id.tv_mode)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_settings)
        toolbar.navigationIconTint = android.graphics.Color.WHITE
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupNavigation() {
        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.nav_mode -> {
                    toggleProxyMode()
                }
                R.id.nav_app_proxy -> {
                    startActivity(Intent(this, AppProxyActivity::class.java))
                }
                R.id.nav_join_tg -> {
                    openUrl(AppConfig.TG_GROUP_URL)
                }
                R.id.nav_website -> {
                    openUrl(AppConfig.WEBSITE_URL)
                }
                R.id.nav_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupClickListeners() {
        btnConnect.setOnClickListener {
            if (vpnConnected) {
                disconnect()
            } else {
                connect()
            }
        }

        findViewById<View>(R.id.btn_select_node).setOnClickListener {
            if (nodes.isEmpty()) {
                Toast.makeText(this, R.string.no_nodes, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 打开节点列表时自动测速
            val intent = Intent(this, NodeListActivity::class.java)
            intent.putExtra("nodes", ArrayList(nodes))
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_speed_test).setOnClickListener {
            if (nodes.isEmpty()) {
                Toast.makeText(this, R.string.no_nodes, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            autoSpeedTest()
        }

        findViewById<View>(R.id.btn_update_sub).setOnClickListener {
            autoUpdateSubscription()
        }

        findViewById<View>(R.id.btn_join_tg).setOnClickListener {
            openUrl(AppConfig.TG_GROUP_URL)
        }

        findViewById<View>(R.id.btn_website).setOnClickListener {
            openUrl(AppConfig.WEBSITE_URL)
        }
    }

    private fun autoImportSubscription() {
        Toast.makeText(this, "正在导入订阅…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = subscriptionManager.fetchSubscription(PrefsHelper.getSubscriptionUrl())
            if (result.isSuccess) {
                nodes = result.getOrDefault(emptyList())
                Toast.makeText(this@MainActivity, "导入 ${nodes.size} 个节点", Toast.LENGTH_SHORT).show()
                autoSpeedTest()
            } else {
                Toast.makeText(this@MainActivity, "导入失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                loadCachedNodes()
            }
        }
    }

    private fun autoUpdateSubscription() {
        lifecycleScope.launch {
            val result = subscriptionManager.fetchSubscription(PrefsHelper.getSubscriptionUrl())
            if (result.isSuccess) {
                nodes = result.getOrDefault(emptyList())
                Toast.makeText(this@MainActivity, "更新成功，共 ${nodes.size} 个节点", Toast.LENGTH_SHORT).show()
            } else {
                loadCachedNodes()
            }
        }
    }

    private fun loadCachedNodes() {
        lifecycleScope.launch {
            nodes = withContext(Dispatchers.IO) { subscriptionManager.loadFromCache() }
            if (nodes.isNotEmpty()) {
                // 恢复选中节点
                val selectedId = PrefsHelper.getSelectedNode()
                if (selectedId != null) {
                    currentNode = nodes.find { it.id == selectedId }
                }
                if (currentNode == null) {
                    currentNode = nodes.first()
                }
                updateNodeDisplay()
            }
        }
    }

    private fun autoSpeedTest() {
        if (nodes.isEmpty()) return
        Toast.makeText(this, R.string.testing, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            SpeedTestUtil.testAll(nodes) { node ->
                runOnUiThread {
                    if (node.id == currentNode?.id) {
                        updateNodeDisplay()
                    }
                }
            }
            // 选中延迟最低的节点（如果没有选中）
            if (currentNode == null || currentNode!!.latency <= 0) {
                currentNode = nodes.filter { it.latency > 0 }.minByOrNull { it.latency }
                updateNodeDisplay()
            }
        }
    }

    private fun connect() {
        if (currentNode == null) {
            if (nodes.isNotEmpty()) {
                currentNode = nodes.first()
            } else {
                Toast.makeText(this, R.string.no_nodes, Toast.LENGTH_SHORT).show()
                return
            }
        }

        // 检查 VPN 权限
        val vpnIntent = SystemVpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, REQUEST_VPN_PERMISSION)
            return
        }

        startVpn()
    }

    private fun startVpn() {
        isConnecting = true
        updateUI()

        val node = currentNode ?: return
        val nodeJson = JSONObject().apply {
            put("id", node.id)
            put("name", node.name)
            put("protocol", node.protocol)
            put("address", node.address)
            put("port", node.port)
            put("rawConfig", node.rawConfig)
        }.toString()

        val intent = Intent(this, VpnService::class.java).apply {
            action = VpnService.ACTION_CONNECT
            putExtra(VpnService.EXTRA_NODE, nodeJson)
        }
        startService(intent)

        PrefsHelper.setSelectedNode(node.id)
    }

    private fun disconnect() {
        isConnecting = true
        updateUI()
        val intent = Intent(this, VpnService::class.java).apply {
            action = VpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN_PERMISSION) {
            if (resultCode == RESULT_OK) {
                startVpn()
            } else {
                Toast.makeText(this, "需要 VPN 权限", Toast.LENGTH_SHORT).show()
                isConnecting = false
                updateUI()
            }
        }
    }

    private fun toggleProxyMode() {
        val newMode = if (PrefsHelper.getProxyMode() == PrefsHelper.MODE_RULE) {
            PrefsHelper.MODE_GLOBAL
        } else {
            PrefsHelper.MODE_RULE
        }
        PrefsHelper.setProxyMode(newMode)
        updateModeDisplay()
        Toast.makeText(this, if (newMode == PrefsHelper.MODE_GLOBAL) "全局模式" else "分流模式", Toast.LENGTH_SHORT).show()

        // 如果已连接，重新连接以应用新模式
        if (vpnConnected) {
            disconnect()
            // 延迟重连
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                connect()
            }
        }
    }

    private fun updateUI() {
        runOnUiThread {
            when {
                isConnecting -> {
                    btnConnect.setImageResource(R.drawable.ic_power_off)
                    btnConnect.setBackgroundResource(R.drawable.btn_connect_selector)
                    tvStatus.text = getString(R.string.connecting)
                }
                vpnConnected -> {
                    btnConnect.setImageResource(R.drawable.ic_power_on)
                    btnConnect.setBackgroundResource(R.drawable.btn_connect_selector)
                    btnConnect.isSelected = true
                    tvStatus.text = getString(R.string.connected)
                }
                else -> {
                    btnConnect.setImageResource(R.drawable.ic_power_off)
                    btnConnect.setBackgroundResource(R.drawable.btn_connect_selector)
                    btnConnect.isSelected = false
                    tvStatus.text = getString(R.string.disconnected)
                }
            }
            updateNodeDisplay()
        }
    }

    private fun updateNodeDisplay() {
        runOnUiThread {
            if (currentNode != null) {
                tvCurrentNode.text = currentNode!!.name
                if (currentNode!!.latency > 0) {
                    tvLatency.visibility = View.VISIBLE
                    tvLatency.text = currentNode!!.latencyText
                }
            }
        }
    }

    private fun updateModeDisplay() {
        tvMode.text = if (PrefsHelper.getProxyMode() == PrefsHelper.MODE_GLOBAL) {
            getString(R.string.mode_global)
        } else {
            getString(R.string.mode_rule)
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQUEST_VPN_PERMISSION = 100
    }
}
