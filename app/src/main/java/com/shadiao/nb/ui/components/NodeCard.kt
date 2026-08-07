package com.shadiao.nb.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadiao.nb.data.ProxyNode
import com.shadiao.nb.ui.theme.ThemeColors

@Composable
fun NodeCard(
    node: ProxyNode,
    isSelected: Boolean,
    isTesting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ThemeColors? = null
) {
    val c = colors ?: ThemeColors(
        background = Color(0xFF0F0F1A),
        surface = Color(0xFF1A1A2E),
        surfaceVariant = Color(0xFF252540),
        onBackground = Color(0xFFE2E8F0),
        onSurface = Color(0xFFCBD5E1),
        onSurfaceVariant = Color(0xFF64748B),
        primary = Color(0xFF6366F1),
        connected = Color(0xFF22C55E),
        disconnected = Color(0xFF64748B),
        error = Color(0xFFEF4444),
        warning = Color(0xFFF59E0B),
        isDark = true
    )

    val bgColor by animateColorAsState(
        if (isSelected) c.primary.copy(alpha = 0.15f) else c.surface,
        label = "bg"
    )

    val latencyColor = when {
        node.latency < 0 -> c.onSurfaceVariant
        node.latency < 100 -> c.connected
        node.latency < 300 -> c.warning
        else -> c.error
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(node.regionFlag, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(node.name, color = c.onBackground, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (isTesting) {
            LinearProgressIndicator(
                Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = c.primary, trackColor = c.surfaceVariant
            )
        } else {
            Text(node.latencyText, color = latencyColor, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold)
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.connected))
        }
    }
}