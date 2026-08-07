package com.shadiao.nb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadiao.nb.ui.theme.ThemeColors
import com.shadiao.nb.util.AppListProvider
import com.shadiao.nb.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ────────────── 设置全屏页 ──────────────
@Composable
fun SettingsScreen(
    colors: ThemeColors,
    onClose: () -> Unit,
    onOpenAppPicker: () -> Unit
) {
    val perAppPackages by SettingsManager.perAppPackages.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // 顶部栏
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设置", color = colors.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Text("✕", color = colors.onSurfaceVariant, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // 分应用代理卡片
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .clickable { onOpenAppPicker() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Apps, null, tint = colors.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("分应用代理", color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (perAppPackages.isEmpty())
                        "点击选择需要代理的应用"
                    else
                        "已选 ${perAppPackages.size} 个应用",
                    color = colors.onSurfaceVariant, fontSize = 12.sp
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.height(16.dp))

        // 说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("使用说明", color = colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "全局模式下，仅你选择的应用会通过代理上网，其余应用直连。" +
                        "首次使用已自动为你选择常见的需要代理的应用（浏览器、Google、社交类等），" +
                        "你可以随时增减。" +
                        "\n\n修改应用列表后如已连接，将自动断开重连以生效。",
                    color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
    }
}

// ────────────── 应用选择全屏 ──────────────
@Composable
fun AppPickerScreen(
    colors: ThemeColors,
    onClose: () -> Unit,
    onSelectionChanged: () -> Unit = {}
) {
    val context = LocalContext.current

    var apps by remember { mutableStateOf<List<AppListProvider.AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    // 选中集合的本地副本，避免每项都触发全局写入；退出时统一保存
    val initialSelected = remember { SettingsManager.perAppPackages.value.toSet() }
    var selected by remember { mutableStateOf(initialSelected) }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { AppListProvider.getInstalledApps(context) }
        apps = list
        loading = false
    }

    val filtered = remember(apps, query, showSystem) {
        apps.filter { info ->
            (showSystem || !info.isSystem) && !info.isSelf &&
                    (query.isBlank() || info.name.contains(query, true) || info.packageName.contains(query, true))
        }
    }

    // 关闭时持久化并通知是否有变化
    fun persistAndClose() {
        val changed = selected != initialSelected
        if (changed) {
            SettingsManager.setPerAppPackages(selected)
            onSelectionChanged()
        }
        onClose()
    }

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
            Text("选择应用", color = colors.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                // 反选当前过滤结果
                val allPkg = filtered.map { it.packageName }.toSet()
                selected = if (selected.containsAll(allPkg)) selected - allPkg else selected + allPkg
            }) {
                Text("反选", color = colors.primary, fontSize = 13.sp)
            }
            TextButton(onClick = ::persistAndClose) {
                Text("完成", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // 搜索框
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索应用", color = colors.onSurfaceVariant, fontSize = 14.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = colors.primary,
                    focusedTextColor = colors.onBackground,
                    unfocusedTextColor = colors.onBackground
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "已选 ${selected.size} 个应用",
                color = colors.onSurfaceVariant, fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = showSystem,
                onClick = { showSystem = !showSystem },
                label = { Text("含系统应用", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary.copy(alpha = 0.2f),
                    selectedLabelColor = colors.primary,
                    containerColor = colors.surface,
                    labelColor = colors.onSurfaceVariant
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("加载应用列表中…", color = colors.onSurfaceVariant, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (checked) colors.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable {
                                selected = if (checked) selected - app.packageName else selected + app.packageName
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 应用图标
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            app.icon?.let {
                                androidx.compose.foundation.Image(
                                    bitmap = it.toBitmapSafe(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                            } ?: Text(app.name.firstOrNull()?.toString() ?: "·", color = colors.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.name,
                                color = colors.onBackground, fontSize = 14.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium
                            )
                            Text(
                                app.packageName,
                                color = colors.onSurfaceVariant, fontSize = 10.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (checked) {
                            Icon(Icons.Rounded.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

/** Drawable 转 ImageBitmap 的安全包装 */
private fun android.graphics.drawable.Drawable.toBitmapSafe(): androidx.compose.ui.graphics.ImageBitmap {
    val bmp = android.graphics.Bitmap.createBitmap(
        kotlin.math.max(1, intrinsicWidth.coerceAtLeast(48)),
        kotlin.math.max(1, intrinsicHeight.coerceAtLeast(48)),
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}
