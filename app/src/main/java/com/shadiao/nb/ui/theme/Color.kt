package com.shadiao.nb.ui.theme

import androidx.compose.ui.graphics.Color

// 主题模式
enum class ThemeMode { SYSTEM, DARK, LIGHT }

// ── 暗色模式 ──
val DarkPrimary = Color(0xFF6366F1)
val DarkPrimaryVariant = Color(0xFF4F46E5)
val DarkSecondary = Color(0xFF22D3EE)
val DarkAccent = Color(0xFF34D399)
val DarkBackground = Color(0xFF0F0F1A)
val DarkSurface = Color(0xFF1A1A2E)
val DarkSurfaceVariant = Color(0xFF252540)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkOnBackground = Color(0xFFE2E8F0)
val DarkOnSurface = Color(0xFFCBD5E1)
val DarkOnSurfaceVariant = Color(0xFF64748B)
val DarkError = Color(0xFFEF4444)
val DarkWarning = Color(0xFFF59E0B)
val DarkConnected = Color(0xFF22C55E)
val DarkDisconnected = Color(0xFF64748B)

// ── 亮色模式 ──
val LightPrimary = Color(0xFF4F46E5)
val LightPrimaryVariant = Color(0xFF6366F1)
val LightSecondary = Color(0xFF0EA5E9)
val LightAccent = Color(0xFF10B981)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF0F172A)
val LightOnSurface = Color(0xFF1E293B)
val LightOnSurfaceVariant = Color(0xFF64748B)
val LightError = Color(0xFFDC2626)
val LightWarning = Color(0xFFD97706)
val LightConnected = Color(0xFF16A34A)
val LightDisconnected = Color(0xFF94A3B8)

// ── 兼容旧引用（暗色模式别名） ──
val Primary = DarkPrimary
val PrimaryVariant = DarkPrimaryVariant
val Secondary = DarkSecondary
val Accent = DarkAccent
val Background = DarkBackground
val Surface = DarkSurface
val SurfaceVariant = DarkSurfaceVariant
val OnPrimary = DarkOnPrimary
val OnBackground = DarkOnBackground
val OnSurface = DarkOnSurface
val OnSurfaceVariant = DarkOnSurfaceVariant
val Error = DarkError
val Warning = DarkWarning
val Connected = DarkConnected
val Disconnected = DarkDisconnected

// 测速色（通用）
val SpeedFast = Color(0xFF22C55E)
val SpeedMedium = Color(0xFFF59E0B)
val SpeedSlow = Color(0xFFEF4444)