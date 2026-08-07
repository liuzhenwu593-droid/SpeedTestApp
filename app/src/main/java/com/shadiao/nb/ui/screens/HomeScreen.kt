package com.shadiao.nb.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadiao.nb.data.NodeRepository
import com.shadiao.nb.data.ProxyNode
import com.shadiao.nb.data.UpdateInfo
import com.shadiao.nb.service.ShadiaoVPNService
import com.shadiao.nb.ui.components.ConnectButton
import com.shadiao.nb.ui.components.NodeCard
import com.shadiao.nb.ui.theme.ThemeColors
import com.shadiao.nb.ui.theme.ThemeMode
import com.shadiao.nb.ui.theme.themeColors
import com.shadiao.nb.util.SettingsManager
import com.shadiao.nb.util.SpeedTester
import com.shadiao.nb.util.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = themeColors(themeMode)

    var nodes by remember { mutableStateOf(NodeRepository.getCachedNodes()) }
    val vpnState by ShadiaoVPNService.vpnState.collectAsState()
    val currentNodeId by ShadiaoVPNService.currentNode.collectAsState()

    var selectedNode by remember { mutableStateOf(nodes.firstOrNull()) }
    var showNodeList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var testingNodes by remember { mutableStateOf(setOf<String>()) }
    var isTestingAll by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(nodes.isEmpty()) }
    var lastRefreshTime by remember { mutableStateOf(NodeRepository.getLastRefreshTime()) }
    var countdown by remember { mutableStateOf("") }

    // 更新检测
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // 首次启动自动测速标记
    var hasAutoTested by remember { mutableStateOf(false) }

    val isConnected = vpnState == ShadiaoVPNService.VPNState.CONNECTED

    // VPN 权限请求 — 只在用户点击连接时触发
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 权限已授予，开始连接
            selectedNode?.let { node ->
                context.startService(Intent(context, ShadiaoVPNService::class.java).apply {
                    action = "CONNECT"
                    putExtra("node_id", node.id)
                })
            }
        }
    }

    fun refreshNodes() {
        scope.launch {
            isRefreshing = true
            refreshError = null
            val fresh = NodeRepository.refreshNodes()
            if (fresh.isNotEmpty()) {
                nodes = fresh
                if (selectedNode == null || nodes.none { it.id == selectedNode?.id }) {
                    selectedNode = nodes.firstOrNull()
                }
                lastRefreshTime = System.currentTimeMillis()
            } else {
                if (nodes.isEmpty()) refreshError = "刷新失败"
            }
            isLoading = false
            isRefreshing = false
        }
    }

    fun testAllNodes() {
        scope.launch {
            isTestingAll = true
            testingNodes = nodes.map { it.id }.toSet()
            // 并发测速：所有节点同时测，受 Semaphore(8) 限流
            val results = SpeedTester.testLatencyBatch(nodes)
            results.forEach { (id, latency) ->
                nodes.find { it.id == id }?.latency = latency
            }
            testingNodes = emptySet()
            isTestingAll = false
            if (!isConnected) {
                nodes.filter { it.latency > 0 }
                    .minByOrNull { it.latency }
                    ?.let { selectedNode = it }
            }
        }
    }

    fun toggleVPN() {
        if (isConnected) {
            context.startService(Intent(context, ShadiaoVPNService::class.java).apply {
                action = "DISCONNECT"
            })
        } else {
            selectedNode?.let { node ->
                // 先检查 VPN 权限，没有则请求
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    vpnPermissionLauncher.launch(prepareIntent)
                } else {
                    // 权限已授予，直接连接
                    context.startService(Intent(context, ShadiaoVPNService::class.java).apply {
                        action = "CONNECT"
                        putExtra("node_id", node.id)
                    })
                }
            }
        }
    }

    fun switchNode(node: ProxyNode) {
        selectedNode = node
        showNodeList = false
        if (isConnected) {
            scope.launch {
                context.startService(Intent(context, ShadiaoVPNService::class.java).apply {
                    action = "DISCONNECT"
                })
                delay(600)
                context.startService(Intent(context, ShadiaoVPNService::class.java).apply {
                    action = "CONNECT"
                    putExtra("node_id", node.id)
                })
            }
        }
    }

    // 设置变更后：若已连接，断开并以新设置重连（VPN 权限已授予，无需再次请求）
    fun reconnectForSettings() {
        if (!isConnected) return
        selectedNode?.let { node ->
            scope.launch {
                context.startService(Intent(context, ShadiaoVPNService::class.java).apply {
                    action = "DISCONNECT"
                })
                delay(800)
                if (VpnService.prepare(context) == null) {
                    context.startService(Intent(context, ShadiaoVPNService::class.java).apply {
                        action = "CONNECT"
                        putExtra("node_id", node.id)
                    })
                }
            }
        }
    }

    // 同步选中节点
    LaunchedEffect(currentNodeId) {
        currentNodeId?.let { id -> selectedNode = nodes.find { it.id == id } }
    }

    // 启动时：仅首次或超过24小时才刷新，否则直接加载缓存
    LaunchedEffect(Unit) {
        if (NodeRepository.shouldRefresh()) {
            refreshNodes()
        } else {
            isLoading = false
        }
    }

    // 启动时自动测速（仅首次）
    LaunchedEffect(nodes) {
        if (!hasAutoTested && nodes.isNotEmpty() && !isLoading) {
            hasAutoTested = true
            testAllNodes()
        }
    }

    // 启动时检测更新
    LaunchedEffect(Unit) {
        val result = UpdateManager.checkUpdate()
        if (result.hasUpdate && result.info != null) {
            updateInfo = result.info
            showUpdateDialog = true
        }
    }

    // 24小时自动更新 + 倒计时
    LaunchedEffect(lastRefreshTime) {
        while (true) {
            delay(1000)
            val elapsed = (System.currentTimeMillis() - lastRefreshTime) / 1000
            val remaining = 24 * 3600 - elapsed
            if (remaining <= 0) {
                refreshNodes()
                countdown = ""
            } else {
                val h = remaining / 3600
                val m = (remaining % 3600) / 60
                countdown = "${h}时${m}分后自动更新"
            }
        }
    }

    // ────────────── 更新弹窗 ──────────────
    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = {
                if (!info.forceUpdate) {
                    showUpdateDialog = true
                }
            },
            containerColor = colors.surface,
            title = {
                Text("发现新版本 v${info.versionName}",
                    color = colors.onBackground, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(info.updateLog.ifBlank { "无更新日志" },
                    color = colors.onSurfaceVariant, fontSize = 14.sp)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDownloading = true
                        // Android 8+ 需要检查安装权限
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !context.packageManager.canRequestPackageInstalls()) {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                        UpdateManager.downloadAndInstall(context, info)
                    },
                    enabled = !isDownloading
                ) {
                    Text(
                        if (isDownloading) "下载中…" else "立即更新",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                if (!info.forceUpdate) {
                    TextButton(onClick = { showUpdateDialog = true }) {
                        Text("稍后", color = colors.onSurfaceVariant)
                    }
                }
            }
        )
    }

    // ────────────── 设置全屏页（切页，非底部弹窗） ──────────────
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            colors = colors,
            onClose = { showSettings = false },
            onOpenAppPicker = {
                showSettings = false
                showAppPicker = true
            }
        )
        return
    }

    // ────────────── 应用选择全屏 ──────────────
    if (showAppPicker) {
        BackHandler { showAppPicker = false }
        AppPickerScreen(
            colors = colors,
            onClose = { showAppPicker = false },
            onSelectionChanged = { reconnectForSettings() }
        )
        return
    }

    // ────────────── 节点列表全屏（覆盖在主屏幕之上） ──────────────
    if (showNodeList) {
        BackHandler { showNodeList = false }
        NodeListFullScreen(
            nodes = nodes,
            selectedNode = selectedNode,
            testingNodes = testingNodes,
            isTestingAll = isTestingAll,
            isRefreshing = isRefreshing,
            onNodeClick = { switchNode(it) },
            onTestAll = { testAllNodes() },
            onRefresh = { refreshNodes() },
            onClose = { showNodeList = false },
            colors = colors
        )
        // 节点列表打开时不渲染主屏幕，避免遮挡
        return
    }

    // ────────────── 主屏幕（仅在节点列表关闭时渲染） ──────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        // 标题 + 主题切换按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左上角设置按钮
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "设置",
                    tint = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            // 居中标题
            Text("沙雕VPN", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Spacer(Modifier.weight(1f))
            // 右上角主题切换按钮
            IconButton(onClick = {
                onThemeModeChange(
                    when (themeMode) {
                        ThemeMode.SYSTEM -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.SYSTEM
                    }
                )
            }) {
                Icon(
                    imageVector = when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                    },
                    contentDescription = "切换主题",
                    tint = colors.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            when {
                isConnected -> "已连接 · 分应用代理"
                isRefreshing -> "正在更新节点…"
                isLoading -> "加载中…"
                else -> "未连接"
            },
            fontSize = 12.sp,
            color = when {
                isConnected -> colors.connected
                isRefreshing -> colors.primary
                else -> colors.onSurfaceVariant
            }
        )

        Spacer(Modifier.height(28.dp))

        // 连接按钮
        ConnectButton(
            state = vpnState,
            latency = selectedNode?.latency ?: -1,
            onToggle = { toggleVPN() }
        )

        Spacer(Modifier.height(24.dp))

        // ── 操作栏 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionChip("手动更新", isRefreshing, Icons.Rounded.Refresh, colors) { refreshNodes() }
            ActionChip("全部测速", isTestingAll, Icons.Rounded.Speed, colors) { testAllNodes() }
        }

        Spacer(Modifier.height(12.dp))

        // 自动更新倒计时
        if (countdown.isNotEmpty()) {
            Text(countdown, color = colors.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
        }

        // 加载中
        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                Text("加载节点中…", color = colors.onSurfaceVariant, fontSize = 14.sp)
            }
        }

        // 刷新错误
        if (refreshError != null) {
            Text(refreshError!!, color = colors.error, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }

        // ── 当前节点卡片 ──
        selectedNode?.let { node ->
            CurrentNodeCard(node, nodes.size, colors) { showNodeList = true }
        }

        Spacer(Modifier.height(16.dp))

        // ── TG 群 & 官网链接 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/shadiaovpn"))
                    context.startActivity(intent)
                }
            ) {
                Text("加入 TG 群", color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shadiao.hynb.ccwu.cc"))
                    context.startActivity(intent)
                }
            ) {
                Text("官网", color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

// ────────────── 当前节点卡片 ──────────────
@Composable
private fun CurrentNodeCard(node: ProxyNode, totalCount: Int, colors: ThemeColors, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(node.regionFlag, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name, color = colors.onBackground, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium)
                Text(node.address, color = colors.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            }
            Text("切换 >", color = colors.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("延迟", node.latencyText, when {
                node.latency < 0 -> colors.onSurfaceVariant
                node.latency < 100 -> colors.connected
                node.latency < 300 -> colors.warning
                else -> colors.error
            })
            StatItem("协议", "VLESS", colors.primary)
            StatItem("传输", "WS+TLS", colors.onSurfaceVariant)
            StatItem("节点", "$totalCount", colors.primary)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}

// ────────────── 操作按钮 ──────────────
@Composable
private fun RowScope.ActionChip(
    label: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: ThemeColors,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) colors.primary.copy(alpha = 0.15f) else colors.surface)
    ) {
        Icon(icon, null,
            tint = if (active) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (active) "${label}…" else label,
            color = if (active) colors.primary else colors.onSurfaceVariant,
            fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ────────────── 全屏节点列表 ──────────────
@Composable
private fun NodeListFullScreen(
    nodes: List<ProxyNode>,
    selectedNode: ProxyNode?,
    testingNodes: Set<String>,
    isTestingAll: Boolean,
    isRefreshing: Boolean,
    onNodeClick: (ProxyNode) -> Unit,
    onTestAll: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    colors: ThemeColors
) {
    val regions = nodes.map { it.region }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("节点列表", color = colors.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, null,
                    tint = if (isRefreshing) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onTestAll) {
                Icon(Icons.Rounded.Speed, null,
                    tint = if (isTestingAll) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onClose) {
                Text("✕", color = colors.onSurfaceVariant, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (nodes.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                Text("暂无节点，请点击刷新", color = colors.onSurfaceVariant, fontSize = 14.sp)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            regions.forEach { region ->
                item {
                    Text(region, color = colors.onSurfaceVariant, fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp))
                }
                items(nodes.filter { it.region == region }) { node ->
                    NodeCard(
                        node = node,
                        isSelected = node.id == selectedNode?.id,
                        isTesting = node.id in testingNodes,
                        onClick = { onNodeClick(node) },
                        colors = colors
                    )
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
