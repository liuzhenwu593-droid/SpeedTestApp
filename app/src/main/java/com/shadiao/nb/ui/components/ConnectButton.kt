package com.shadiao.nb.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadiao.nb.service.ShadiaoVPNService

@Composable
fun ConnectButton(
    state: ShadiaoVPNService.VPNState,
    latency: Long,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = state == ShadiaoVPNService.VPNState.CONNECTED
    val isConnecting = state == ShadiaoVPNService.VPNState.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val color = when {
        isConnected -> Color(0xFF22C55E)
        isConnecting -> Color(0xFF6366F1)
        else -> Color(0xFF2D2D44)
    }

    val gradient = Brush.verticalGradient(
        when {
            isConnected -> listOf(Color(0xFF22C55E), Color(0xFF16A34A))
            isConnecting -> listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
            else -> listOf(Color(0xFF3B3B52), Color(0xFF2D2D44))
        }
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(176.dp)
                .scale(if (isConnecting) pulseScale else 1f)
                .clip(CircleShape)
                .background(gradient)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isConnected) "断开" else if (isConnecting) "..." else "连接",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isConnecting) {
                    Spacer(Modifier.height(4.dp))
                    Text("连接中", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                }
            }
        }
    }
}