package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.MainViewModel

@Composable
fun DiagnosticsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("الفحص الذاتي الشامل", "التشخيص الحي")

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTabIndex) {
                0 -> SelfTestScreen(viewModel = viewModel)
                1 -> LiveDiagnosticsSection(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun LiveDiagnosticsSection(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPkg by viewModel.repository.currentForegroundPackage.collectAsState()
    val prevPkg by viewModel.repository.previousForegroundPackage.collectAsState()
    val stateMachineState by viewModel.repository.currentStateMachineState.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    val profiles by viewModel.profilesFlow.collectAsState(initial = emptyList())

    val isAccessibilityActive = viewModel.isAccessibilityEnabled()
    val hasUsageStats = viewModel.hasUsageStatsPermission()
    val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    val isDeviceAdmin = viewModel.isDeviceAdminActive()
    val isBatteryOptimizedIgnored = viewModel.isBatteryOptimizationIgnored()

    val activeProfileName = profiles.find { it.id == settings.activeProfileId }?.name ?: "الوضع الافتراضي"
    val tempSessions = viewModel.repository.getTemporaryUnlockSessionsMap()
    val isRecentSessionActive = viewModel.repository.isRecentAuthSessionValid()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "مراقبة حية للمحرك، كشف الحزم الأمامية، وحالة الحماية الذاتية والأذونات.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // Live Engine State
        item {
            Text("1. حالة المحرك والكشف الحي (Live Foreground Engine)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagnosticItem(label = "Current Foreground Package:", value = if (currentPkg.isNotBlank()) currentPkg else "في انتظار حدث...", isHighlight = true)
                    DiagnosticItem(label = "Previous Foreground Package:", value = if (prevPkg.isNotBlank()) prevPkg else "لا يوجد")
                    DiagnosticItem(label = "Pending Target Package:", value = if (currentPkg.isNotBlank()) currentPkg else "None")
                    DiagnosticItem(label = "Locked Target Package:", value = if (stateMachineState.startsWith("LOCKED")) currentPkg else "None")
                    DiagnosticItem(label = "Current Protection Decision:", value = stateMachineState)
                    DiagnosticItem(label = "Temporary Unlock State:", value = if (tempSessions.isEmpty()) "لا توجد جلسات نشطة" else "${tempSessions.size} جلسة نشطة")
                    DiagnosticItem(label = "Unlock Mode:", value = settings.unlockRelockMode.name)
                    DiagnosticItem(label = "Remaining Time (Timeout):", value = "${settings.timeoutMinutes} دقيقة")
                    DiagnosticItem(label = "Remaining Opens (Open Count):", value = "${settings.openCountLimit} مرات فتح")
                    DiagnosticItem(label = "Screen-Off Relock:", value = if (settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.SCREEN_OFF) "مفعّل (ON - إبطال فوري عند إطفاء الشاشة)" else "معطّل (OFF)")
                    DiagnosticItem(label = "Recent Apps Lock:", value = if (settings.isRecentAppsLockEnabled) "مفعّل (ON - محمي)" else "معطّل (OFF)")
                    DiagnosticItem(label = "Recent Auth Session:", value = if (isRecentSessionActive) "نشطة (مسموح بالدخول)" else "منتهية (يتطلب PIN)")
                    DiagnosticItem(label = "App Guard Self Protection:", value = "ALWAYS PROTECTED (قاعدة SELF_PROTECTION مفعلة دائماً)", isHighlight = true)
                    DiagnosticItem(label = "Accessibility Status:", value = if (isAccessibilityActive) "نشطة وتعمل (CONNECTED)" else "معطلة (DISCONNECTED)")
                }
            }
        }

        // Permissions Status
        item {
            Text("2. الصلاحيات وخدمات النظام (System Permissions)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PermissionStatusRow(
                        title = "خدمة إمكانية الوصول (Accessibility Service)",
                        isGranted = isAccessibilityActive,
                        onGrant = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    PermissionStatusRow(
                        title = "الوصول لبيانات الاستخدام (Usage Stats)",
                        isGranted = hasUsageStats,
                        onGrant = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    PermissionStatusRow(
                        title = "الظهور فوق التطبيقات (Overlay Permission)",
                        isGranted = hasOverlay,
                        onGrant = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    PermissionStatusRow(
                        title = "مسؤول الجهاز لمنع الإزالة (Device Admin)",
                        isGranted = isDeviceAdmin,
                        onGrant = {
                            val component = android.content.ComponentName(context, com.example.service.AppGuardDeviceAdminReceiver::class.java)
                            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                            }
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    PermissionStatusRow(
                        title = "استثناء تحسين البطارية (Battery Optimization Exemption)",
                        isGranted = isBatteryOptimizedIgnored,
                        onGrant = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(fallbackIntent)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticItem(label: String, value: String, isHighlight: Boolean = false) {
    Column {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = if (isGranted) "مفعلة وتعمل بنجاح" else "غير مفعلة (مطلوبة)",
                fontSize = 11.sp,
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        if (!isGranted) {
            FilledTonalButton(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("منح الإذن", fontSize = 11.sp)
            }
        } else {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
    }
}
