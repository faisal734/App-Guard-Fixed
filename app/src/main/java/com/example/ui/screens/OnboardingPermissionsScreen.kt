package com.example.ui.screens

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingPermissionsScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live permission states
    var isAccessibilityActive by remember { mutableStateOf(viewModel.isAccessibilityEnabled()) }
    var hasUsageStats by remember { mutableStateOf(viewModel.hasUsageStatsPermission()) }
    var hasOverlay by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true) }
    var isDeviceAdmin by remember { mutableStateOf(viewModel.isDeviceAdminActive()) }
    var isBatteryOptimizedIgnored by remember { mutableStateOf(viewModel.isBatteryOptimizationIgnored()) }
    var isNotificationAccess by remember { mutableStateOf(viewModel.isNotificationAccessGranted()) }

    // Helper to refresh all permissions
    fun refreshPermissions() {
        isAccessibilityActive = viewModel.isAccessibilityEnabled()
        hasUsageStats = viewModel.hasUsageStatsPermission()
        hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        isDeviceAdmin = viewModel.isDeviceAdminActive()
        isBatteryOptimizedIgnored = viewModel.isBatteryOptimizationIgnored()
        isNotificationAccess = viewModel.isNotificationAccessGranted()
    }

    // Refresh automatically when user returns to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Checking if all required (Group 1) permissions are granted
    val isRequiredGranted = isAccessibilityActive && hasUsageStats && hasOverlay

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onComplete,
                        enabled = isRequiredGranted,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = "تفعيل الحماية والدخول",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isRequiredGranted) 
                            "رائع! جازك جاهز للتأمين الآن" 
                        else 
                            "يرجى تفعيل الصلاحيات الأساسية المطلوبة للمتابعة",
                        fontSize = 12.sp,
                        color = if (isRequiredGranted) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
        ) {
            // Header Hero Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "خطوات الحماية النشطة",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "يحتاج حارس التطبيقات لبعض الصلاحيات للعمل بكفاءة ورصد محاولات التسلل ومنع الالتفاف على شاشات القفل.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // Group 1: Required Permissions (إلزامية العبور 🛑)
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "1. صلاحيات التشغيل الأساسية",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = "مطلوب",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            item {
                OnboardingPermissionCard(
                    title = "إذن تتبع استخدام التطبيقات (Usage Stats)",
                    description = "يسمح للتطبيق بمعرفة متى يتم فتح أي تطبيق بالواجهة لتتمكن الحماية من إطلاق شاشة القفل فوراً.",
                    isGranted = hasUsageStats,
                    onGrant = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }

            item {
                OnboardingPermissionCard(
                    title = "الظهور فوق التطبيقات (Overlay Permission)",
                    description = "يسمح برسم وعرض نافذة رمز الـ PIN وقفل التطبيقات وتغطية المحتوى لحمايته.",
                    isGranted = hasOverlay,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    }
                )
            }

            item {
                OnboardingPermissionCard(
                    title = "خدمة إمكانية الوصول (Accessibility Service)",
                    description = "الدرع الأمني الأساسي لمنع المتسللين من الالتفاف أو استخدام أزرار الهاتف لتجاوز قفل التطبيقات.",
                    isGranted = isAccessibilityActive,
                    onGrant = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }

            // Group 2: Recommended / Optional Permissions (موصى به / اختياري ⚡)
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "2. درع الاستقرار والحماية الإضافية",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = "موصى به",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            item {
                OnboardingPermissionCard(
                    title = "استثناء تحسين البطارية (Battery Optimization Exemption)",
                    description = "يضمن استقرار خدمة الأمان بالخلفية وعدم إغلاقها أو قتلها فجأة بواسطة نظام الأندرويد لتوفير الطاقة.",
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

            item {
                OnboardingPermissionCard(
                    title = "إخفاء وحماية الإشعارات الحساسة (Notification Vault)",
                    description = "يسمح بإخفاء الإشعارات الخاصة بالتطبيقات المقفلة من شريط الحالة وحفظها بأمان داخل الخزنة.",
                    isGranted = isNotificationAccess,
                    onGrant = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }

            item {
                OnboardingPermissionCard(
                    title = "مسؤول الجهاز لمنع الإزالة (Device Admin)",
                    description = "يحمي حارس التطبيقات نفسه من أن يتم حذفه أو إلغاء تثبيته بواسطة الأشخاص المتطفلين.",
                    isGranted = isDeviceAdmin,
                    onGrant = {
                        val component = ComponentName(context, com.example.service.AppGuardDeviceAdminReceiver::class.java)
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingPermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isGranted) 
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) 
        else 
            null,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "تم منح الإذن",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Button(
                    onClick = onGrant,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("تفعيل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
